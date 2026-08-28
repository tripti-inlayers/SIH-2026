package com.sancharsaathi.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sancharsaathi.app.domain.model.RiskLevel
import com.sancharsaathi.app.domain.model.RiskResult
import com.sancharsaathi.app.presentation.MainActivity

object NotificationHelper {

    const val CHANNEL_ID = "sanchar_saathi_threat_alerts"
    private const val CHANNEL_NAME = "Sanchar Saathi Security Alerts"
    private const val CHANNEL_DESC = "Notifications for detected phishing and scam SMS messages"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showThreatNotification(context: Context, result: RiskResult) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("ANALYSIS_ID", result.analysisId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            result.analysisId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (result.riskLevel) {
            RiskLevel.HIGH -> "🚨 HIGH RISK Phishing SMS Intercepted"
            RiskLevel.SUSPICIOUS -> "⚠️ Suspicious SMS Intercepted"
            RiskLevel.LOW -> "✅ SMS Clean (Low Risk)"
        }

        val senderText = result.sender ?: "Unknown Sender"
        val bodyText = result.reasons.firstOrNull() ?: "Message analyzed by Sanchar Saathi AI"
        val contentText = "From $senderText: $bodyText"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(result.analysisId.hashCode(), builder.build())
    }
}
