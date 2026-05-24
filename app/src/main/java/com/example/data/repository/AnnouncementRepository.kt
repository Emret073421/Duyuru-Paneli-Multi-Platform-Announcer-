package com.example.data.repository

import com.example.data.api.BroadcastService
import com.example.data.api.ServiceResult
import com.example.data.database.AnnouncementDao
import com.example.data.database.AnnouncementEntity
import com.example.data.database.ChannelDao
import com.example.data.database.ChannelEntity
import kotlinx.coroutines.flow.Flow

class AnnouncementRepository(
    private val channelDao: ChannelDao,
    private val announcementDao: AnnouncementDao,
    private val broadcastService: BroadcastService
) {
    val channels: Flow<List<ChannelEntity>> = channelDao.getAllChannels()
    val history: Flow<List<AnnouncementEntity>> = announcementDao.getAllAnnouncements()

    suspend fun saveChannel(channel: ChannelEntity) {
        channelDao.saveChannel(channel)
    }

    suspend fun deleteChannel(channel: ChannelEntity) {
        channelDao.deleteChannel(channel)
    }

    suspend fun clearHistory() {
        announcementDao.clearHistory()
    }

    /**
     * Görev/Amaç: Seçilen tüm kanallara duyuru metnini ve opsiyonel görselleri (link veya yerel binary veri) iletir.
     * Ardından gönderim detaylarını SQLite veritabanımıza kaydeder ve geçmiş tablosunda gösterilmesini sağlar.
     */
    suspend fun broadcastAnnouncement(
        message: String, 
        selectedChannels: List<ChannelEntity>,
        photoUrl: String? = null,
        photoBytes: ByteArray? = null
    ): AnnouncementEntity {
        if (selectedChannels.isEmpty()) {
            val log = AnnouncementEntity(
                message = message,
                resultSummary = "Hiçbir hedef kanal seçilmedi."
            )
            announcementDao.insertAnnouncement(log)
            return log
        }

        val summaryLines = mutableListOf<String>()

        selectedChannels.forEach { channel ->
            val result = when (channel.platformType) {
                "Telegram" -> {
                    broadcastService.sendTelegram(
                        token = channel.telegramToken,
                        chatId = channel.telegramChatId,
                        message = message,
                        photoUrl = photoUrl,
                        photoBytes = photoBytes
                    )
                }
                "Discord" -> {
                    broadcastService.sendDiscord(
                        webhookUrl = channel.webhookUrl,
                        message = message,
                        photoUrl = photoUrl,
                        photoBytes = photoBytes
                    )
                }
                "Slack" -> {
                    broadcastService.sendSlack(
                        webhookUrl = channel.webhookUrl,
                        message = message,
                        photoUrl = photoUrl,
                        photoBytes = photoBytes
                    )
                }
                else -> ServiceResult.Failure("Bilinmeyen platform türü")
            }

            val statusText = when (result) {
                is ServiceResult.Success -> "Başarılı"
                is ServiceResult.Failure -> "Başarısız (${result.error})"
            }

            summaryLines.add("• ${channel.name} (${channel.platformType}): $statusText")
        }

        val dynamicSummary = summaryLines.joinToString("\n")

        // Eğer bir görsel yüklenmiş/iliştirilmiş ise geçmiş listesinde ve raporda bunun takibi gösterilir
        val finalMessageDisplay = if (!photoUrl.isNullOrBlank()) {
            "$message\n[Görsel URL: $photoUrl]"
        } else if (photoBytes != null) {
            "$message\n[Yerel Görsel İliştirildi]"
        } else {
            message
        }

        val log = AnnouncementEntity(
            message = finalMessageDisplay,
            resultSummary = dynamicSummary
        )

        announcementDao.insertAnnouncement(log)
        return log
    }

    /**
     * Connection tests for a specific platform setup
     */
    suspend fun testTelegram(token: String, chatId: String): ServiceResult {
        return broadcastService.sendTelegram(token, chatId, "OmniAnnounce Test: Telegram bağlantısı sorunsuz!")
    }

    suspend fun testDiscord(url: String): ServiceResult {
        return broadcastService.sendDiscord(url, "OmniAnnounce Test: Discord bağlantısı sorunsuz!")
    }

    suspend fun testSlack(url: String): ServiceResult {
        return broadcastService.sendSlack(url, "OmniAnnounce Test: Slack bağlantısı sorunsuz!")
    }
}
