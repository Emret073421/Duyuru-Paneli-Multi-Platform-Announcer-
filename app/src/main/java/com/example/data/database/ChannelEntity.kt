package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_channels")
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val platformType: String, // "Telegram", "Discord", "Slack"
    val isEnabled: Boolean = true,
    // Telecom Token / ChatID
    val telegramToken: String = "",
    val telegramChatId: String = "",
    // Discord or Slack Webhook URL
    val webhookUrl: String = ""
)
