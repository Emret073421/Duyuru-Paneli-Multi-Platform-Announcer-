package com.example.ui.components.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object PlatformColors {
    val Telegram = Color(0xFF229ED9)
    val Slack = Color(0xFFE01E5A)
    val Default = Color.Gray

    fun forPlatform(platform: String): Color = when (platform) {
        "Telegram" -> Telegram
        "Slack" -> Slack
        else -> Default
    }
}

@Composable
fun whiteOnPrimaryTextFieldColors(): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color.White,
        unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
        focusedContainerColor = Color.White.copy(alpha = 0.1f),
        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
    )

@Composable
fun primaryBroadcastCardColors(): CardColors =
    CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = Color.White
    )

fun pillButtonBorderStroke(alpha: Float = 0.5f): BorderStroke =
    BorderStroke(0.8.dp, Color.White.copy(alpha = alpha))
