package com.example.sancharsaathi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SmsReceiver : BroadcastReceiver() {

    private val retrofit = Retrofit.Builder()
        // Wi-Fi LAN IP to allow wireless backend access without USB cable
        .baseUrl("http://192.168.29.242:8000/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(FastApiService::class.java)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val sender = sms.displayOriginatingAddress ?: ""
                val body = sms.displayMessageBody ?: ""

                // 1. Broadcast to MainActivity (if active)
                val payloadMap = mapOf("sender" to sender, "content" to body)
                val payloadJson = Gson().toJson(payloadMap)

                val localIntent = Intent("com.example.sancharsaathi.SMS_INTERCEPTED")
                localIntent.putExtra("payload", payloadJson)
                context.sendBroadcast(localIntent)

                // 2. Dispatch to Backend
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val response = apiService.analyze(AnalyzeRequest(content = body, sender = sender))
                        if (response.isSuccessful) {
                            val analysis = response.body()
                            if (analysis != null && (analysis.risk_level == "BLOCK" || analysis.risk_level == "WARN")) {
                                showNotification(context, sender, analysis.risk_level)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SmsReceiver", "Failed to analyze SMS", e)
                    }
                }
            }
        }
    }

    private fun showNotification(context: Context, sender: String, level: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "sancharsaathi_alerts"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Security Alerts", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val title = if (level == "BLOCK") "Malicious SMS Blocked" else "Suspicious SMS Warning"
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText("Message from $sender has been flagged by SancharSaathi.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
