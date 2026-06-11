package com.example.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Görev/Amaç: Dış servislere duyuruları (metin ve opsiyonel resimleri) ileten HTTP istemci servisidir.
 * OkHttp tabanlı çalışır, ağ hatalarında (özellikle VPN kaynaklı kopmalarda) otomatik yeniden deneme (retry) yapar.
 */
sealed class ServiceResult {
    data class Success(val info: String) : ServiceResult()
    data class Failure(val error: String) : ServiceResult()
}

class BroadcastService {
    // 30 saniyelik geniş zaman aşımı oranları ve VPN kopmalarına karşı otomatik deneme desteği
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Payload içindeki özel karakterleri JSON standartlarına uygun şekilde kaçış karakteri (escape) ile temizler.
     */
    private fun escapeForJson(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * VPN gibi kararsız ağ durumlarında isteği logaritmik katlanan bekleme süresiyle 3 kez yeniden dener.
     */
    private suspend fun <T> retryOnNetworkFailure(block: suspend () -> T): T {
        var lastException: Exception? = null
        var delayMs = 1000L
        for (attempt in 1..3) {
            try {
                return block()
            } catch (e: IOException) {
                lastException = e
                if (attempt < 3) {
                    kotlinx.coroutines.delay(delayMs)
                    delayMs *= 2
                }
            }
        }
        throw lastException ?: IOException("Bilinmeyen ağ hatası")
    }

    /**
     * Telegram Bot API kullanarak mesaj gönderir.
     * Hem sadece metin (sendMessage), hem de resim (sendPhoto - URL veya Yerel Dosya) yöntemlerini destekler.
     */
    suspend fun sendTelegram(
        token: String, 
        chatId: String, 
        message: String,
        photoUrl: String? = null,
        photoBytes: ByteArray? = null
    ): ServiceResult = withContext(Dispatchers.IO) {
        if (token.isBlank() || chatId.isBlank()) {
            return@withContext ServiceResult.Failure("Eksik Telegram parametreleri (Bot Token veya Chat ID)")
        }

        if (!token.trim().matches(Regex("^[0-9]+:[A-Za-z0-9_-]+$"))) {
            return@withContext ServiceResult.Failure("Geçersiz Telegram Bot Token biçimi")
        }

        if (photoUrl != null && photoUrl.isNotBlank() && !photoUrl.trim().startsWith("https://")) {
            return@withContext ServiceResult.Failure("Görsel URL'si yalnızca https:// ile başlamalıdır")
        }

        val hasPhoto = (photoUrl != null && photoUrl.isNotBlank()) || photoBytes != null
        val url = if (hasPhoto) {
            "https://api.telegram.org/bot${token.trim()}/sendPhoto"
        } else {
            "https://api.telegram.org/bot${token.trim()}/sendMessage"
        }

        val request = if (photoBytes != null) {
            // Yerel Görsel Yükleme (Multipart form-data)
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId.trim())
                .addFormDataPart("caption", message)
                .addFormDataPart("photo", "announcement_image.jpg", photoBytes.toRequestBody("image/*".toMediaType()))
                .build()

            Request.Builder().url(url).post(multipartBody).build()
        } else if (photoUrl != null && photoUrl.isNotBlank()) {
            // Link Üzerinden Görsel Gönderme (JSON)
            val jsonPayload = """{"chat_id": "${chatId.trim()}", "photo": "${escapeForJson(photoUrl)}", "caption": "${escapeForJson(message)}"}"""
            Request.Builder().url(url).post(jsonPayload.toRequestBody(jsonMediaType)).build()
        } else {
            // Sadece Düz Metin Mesajı Gönderme (JSON)
            val jsonPayload = """{"chat_id": "${chatId.trim()}", "text": "${escapeForJson(message)}"}"""
            Request.Builder().url(url).post(jsonPayload.toRequestBody(jsonMediaType)).build()
        }

        try {
            retryOnNetworkFailure {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        ServiceResult.Success("Telegram: Gönderildi (Kod: ${response.code})")
                    } else {
                        val errorBody = response.body?.string() ?: "Boş hata gövdesi"
                        ServiceResult.Failure("Telegram API Hatası (Kod ${response.code}): $errorBody")
                    }
                }
            }
        } catch (e: IOException) {
            ServiceResult.Failure("Telegram Bağlantı/Zaman Aşımı Hatası: ${e.localizedMessage} (VPN bağlantısını kontrol edin veya tekrar deneyin)")
        } catch (e: Exception) {
            ServiceResult.Failure("Telegram Hatası: ${e.localizedMessage}")
        }
    }

    /**
     * Slack Webhook API kullanarak yapılandırılmış Block Kit mesajı gönderir.
     * Not: Slack Webhook doğrudan yerel çok parçalı (multipart) dosya yüklemeyi desteklemez,
     * ancak web üzerindeki bir resim adresini (block-kit) harika şekilde sunar.
     */
    suspend fun sendSlack(
        webhookUrl: String, 
        message: String,
        photoUrl: String? = null,
        photoBytes: ByteArray? = null
    ): ServiceResult = withContext(Dispatchers.IO) {
        if (webhookUrl.isBlank() || !webhookUrl.startsWith("https://")) {
            return@withContext ServiceResult.Failure("Slack Webhook URL'si https:// ile başlamalıdır")
        }

        val parsedHost = try { URI(webhookUrl.trim()).host } catch (_: Exception) { null }
        if (parsedHost == null || !parsedHost.endsWith("slack.com")) {
            return@withContext ServiceResult.Failure("Slack Webhook URL'si yalnızca hooks.slack.com alan adını kabul eder")
        }

        val jsonPayload = if (photoUrl != null && photoUrl.isNotBlank()) {
            // Slack Block Kit formatı sayesinde metin ve görseli yan yana / alt alta hizalama şeması
            """{
                "text": "${escapeForJson(message)}",
                "blocks": [
                    {
                        "type": "section",
                        "text": {
                            "type": "mrkdwn",
                            "text": "${escapeForJson(message)}"
                        }
                    },
                    {
                        "type": "image",
                        "image_url": "${escapeForJson(photoUrl)}",
                        "alt_text": "Duyuru Görseli"
                    }
                ]
            }"""
        } else {
            // Sadece Düz Metin
            """{"text": "${escapeForJson(message)}"}"""
        }

        val request = Request.Builder()
            .url(webhookUrl.trim())
            .post(jsonPayload.toRequestBody(jsonMediaType))
            .build()

        try {
            retryOnNetworkFailure {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        ServiceResult.Success("Slack: Gönderildi (Kod: ${response.code})")
                    } else {
                        val errorBody = response.body?.string() ?: "Boş hata gövdesi"
                        ServiceResult.Failure("Slack API Hatası (Kod ${response.code}): $errorBody")
                    }
                }
            }
        } catch (e: IOException) {
            ServiceResult.Failure("Slack Webhook Bağlantı/Zaman Aşımı Hatası: ${e.localizedMessage} (VPN bağlantısını kontrol edin)")
        } catch (e: Exception) {
            ServiceResult.Failure("Slack Hatası: ${e.localizedMessage}")
        }
    }
}
