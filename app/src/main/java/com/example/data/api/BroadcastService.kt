package com.example.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
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

    private suspend fun executeRequest(request: Request, platformName: String): ServiceResult {
        return try {
            retryOnNetworkFailure {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        ServiceResult.Success("$platformName: Gönderildi (Kod: ${response.code})")
                    } else {
                        val errorBody = response.body?.string() ?: "Boş hata gövdesi"
                        ServiceResult.Failure("$platformName API Hatası (Kod ${response.code}): $errorBody")
                    }
                }
            }
        } catch (e: IOException) {
            ServiceResult.Failure("$platformName Bağlantı/Zaman Aşımı Hatası: ${e.localizedMessage} (VPN bağlantısını kontrol edin)")
        } catch (e: Exception) {
            ServiceResult.Failure("$platformName Hatası: ${e.localizedMessage}")
        }
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

        executeRequest(request, "Telegram")
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
        if (webhookUrl.isBlank() || !webhookUrl.startsWith("http")) {
            return@withContext ServiceResult.Failure("Geçersiz Slack Webhook URL segmenti")
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

        executeRequest(request, "Slack")
    }
}
