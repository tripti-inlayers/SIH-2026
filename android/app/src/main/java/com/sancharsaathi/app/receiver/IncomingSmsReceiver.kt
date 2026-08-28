package com.sancharsaathi.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.sancharsaathi.app.domain.capture.SmsCaptureChannel

class IncomingSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (!messages.isNullOrEmpty()) {
                val sender = messages[0].displayOriginatingAddress
                val timestamp = messages[0].timestampMillis
                val bodyBuilder = StringBuilder()
                for (msg in messages) {
                    bodyBuilder.append(msg.displayMessageBody)
                }
                val body = bodyBuilder.toString()
                if (body.isNotBlank()) {
                    SmsCaptureChannel.emitSms(sender, body, timestamp)
                }
            }
        }
    }
}
