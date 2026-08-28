package com.sancharsaathi.app.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sancharsaathi.app.domain.model.RiskLevel
import com.sancharsaathi.app.domain.model.RiskResult

object NotificationHelper {

    private const val CHANNEL_ID = "sms_threat_alerts"
    private const val CHANNEL_NAME = "SMS Threat Detection Alerts"
    private const val CHANNEL_DESC = "Alerts for detected phishing and spam SMS messages"
    private const val NOTIFICATION_ID = 1001

    fun showNotification(context: Context, result: RiskResult) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create Channel for API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                if (result.riskLevel == RiskLevel.HIGH) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val title = when (result.riskLevel) {
            RiskLevel.HIGH -> "⚠️ Phishing Attack Blocked"
            RiskLevel.SUSPICIOUS -> "🧐 Suspicious Message Detected"
            else -> "✓ Safe Message Received"
        }

        val body = "From: ${result.sender ?: "Unknown"}\nReason: ${result.reasons.firstOrNull() ?: "Standard SMS"}"

        val icon = android.R.drawable.ic_dialog_info

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText("From ${result.sender}: Click to inspect.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (result.riskLevel == RiskLevel.HIGH) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID + result.analysisId.hashCode(), builder.build())
    }
}
