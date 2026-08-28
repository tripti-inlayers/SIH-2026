package com.sancharsaathi.app.data.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat

data class PhoneSmsMessage(
    val id: Long,
    val sender: String,
    val body: String,
    val timestamp: Long
)

class SmsInboxReader(
    private val context: Context,
    private val historyStore: HistoryStore
) {

    fun getLatestInboxMessages(limit: Int = 10): List<PhoneSmsMessage> {
        runForensicSearchForRaghib(context)

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.d("SmsInboxReader", "SMS_READ_PERMISSION=DENIED")
            return emptyList()
        }

        Log.d("SmsInboxReader", "SMS_READ_PERMISSION=GRANTED")
        Log.d("SmsInboxReader", "SMS_QUERY_STARTED: limit=$limit")

        val messages = mutableListOf<PhoneSmsMessage>()
        val smsUri = Uri.parse("content://sms")
        val projection = arrayOf("_id", "address", "body", "date")

        try {
            val cursor = context.contentResolver.query(
                smsUri,
                projection,
                null, // Query all SMS messages (including spam, inbox, sent, and blocked)
                null,
                "date DESC"
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndex("_id")
                val addressCol = c.getColumnIndex("address")
                val bodyCol = c.getColumnIndex("body")
                val dateCol = c.getColumnIndex("date")

                Log.d("SmsInboxReader", "SMS_QUERY_RESULT_COUNT=${c.count}")

                var count = 0
                while (c.moveToNext() && count < limit) {
                    val id = if (idCol != -1) c.getLong(idCol) else System.currentTimeMillis()
                    val sender = if (addressCol != -1) c.getString(addressCol) ?: "Unknown" else "Unknown"
                    val body = if (bodyCol != -1) c.getString(bodyCol) ?: "" else ""
                    var date = if (dateCol != -1) c.getLong(dateCol) else System.currentTimeMillis()
                    
                    // Normalize timestamp to milliseconds if returned in seconds
                    if (date < 10000000000L) {
                        date *= 1000
                    }

                    if (body.isNotBlank()) {
                        messages.add(PhoneSmsMessage(id, sender, body, date))
                        Log.d("SmsInboxReader", "SMS_INBOX_ITEM: phoneSmsId=$id, sender=$sender, timestamp=$date")
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsInboxReader", "Error reading SMS inbox: ${e.message}", e)
        }

        return messages
    }

    private fun runForensicSearchForRaghib(context: Context) {
        Log.d("ForensicSearch", "PHONE_MESSAGE_SYNC_START")
        Log.d("ForensicSearch", "PHONE_MESSAGE_PROVIDER = Telephony.Sms.Inbox")

        val inboxUri = Uri.parse("content://sms/inbox")
        var totalCount = 0
        try {
            context.contentResolver.query(inboxUri, arrayOf("_id"), null, null, null)?.use { c ->
                totalCount = c.count
            }
        } catch (e: Exception) {
            Log.e("ForensicSearch", "Error counting inbox: ${e.message}")
        }
        Log.d("ForensicSearch", "PHONE_MESSAGE_COUNT = $totalCount")

        // Log the newest 20 records
        try {
            context.contentResolver.query(inboxUri, arrayOf("_id", "date", "address", "body"), null, null, "date DESC")?.use { c ->
                val idCol = c.getColumnIndex("_id")
                val dateCol = c.getColumnIndex("date")
                val addrCol = c.getColumnIndex("address")
                val bodyCol = c.getColumnIndex("body")
                var logged = 0
                while (c.moveToNext() && logged < 20) {
                    val id = if (idCol != -1) c.getLong(idCol) else -1
                    val timestamp = if (dateCol != -1) c.getLong(dateCol) else -1
                    val sender = if (addrCol != -1) c.getString(addrCol) ?: "Unknown" else "Unknown"
                    val body = if (bodyCol != -1) c.getString(bodyCol) ?: "" else ""
                    Log.d("ForensicSearch", "PHONE_MESSAGE: id=$id timestamp=$timestamp sender=$sender bodyLength=${body.length}")
                    logged++
                }
            }
        } catch (e: Exception) {
            Log.e("ForensicSearch", "Error logging newest 20: ${e.message}")
        }

        // Look for Raghib or plsgivemoney
        var raghibFound = false
        try {
            context.contentResolver.query(
                Uri.parse("content://sms"),
                arrayOf("_id", "date", "address", "body"),
                "body LIKE ? OR address LIKE ?",
                arrayOf("%plsgivemoney%", "%Raghib%"),
                "date DESC"
            )?.use { c ->
                if (c.moveToFirst()) {
                    raghibFound = true
                    val id = c.getLong(c.getColumnIndexOrThrow("_id"))
                    val date = c.getLong(c.getColumnIndexOrThrow("date"))
                    val address = c.getString(c.getColumnIndexOrThrow("address"))
                    Log.d("ForensicSearch", "RAGHIB_FOUND_IN_PROVIDER = true")
                    Log.d("ForensicSearch", "RAGHIB_PROVIDER = Telephony.Sms.Inbox")
                    Log.d("ForensicSearch", "RAGHIB_PROVIDER_ID = $id")
                    Log.d("ForensicSearch", "RAGHIB_PROVIDER_TIMESTAMP = $date")
                    Log.d("ForensicSearch", "RAGHIB_PROVIDER_SENDER = $address")
                }
            }
        } catch (e: Exception) {
            Log.e("ForensicSearch", "Error searching SMS for Raghib: ${e.message}")
        }

        if (!raghibFound) {
            // Check MMS
            var mmsFound = false
            try {
                val partUri = Uri.parse("content://mms/part")
                context.contentResolver.query(partUri, arrayOf("_id", "mid", "text"), "text LIKE ?", arrayOf("%plsgivemoney%"), null)?.use { c ->
                    if (c.moveToFirst()) {
                        mmsFound = true
                        val id = c.getLong(c.getColumnIndexOrThrow("_id"))
                        val mid = c.getLong(c.getColumnIndexOrThrow("mid"))
                        Log.d("ForensicSearch", "RAGHIB_FOUND_IN_PROVIDER = true")
                        Log.d("ForensicSearch", "RAGHIB_PROVIDER = Telephony.Mms")
                        Log.d("ForensicSearch", "RAGHIB_PROVIDER_ID = $id")
                        Log.d("ForensicSearch", "RAGHIB_PROVIDER_TIMESTAMP = ${System.currentTimeMillis()}")
                        Log.d("ForensicSearch", "RAGHIB_PROVIDER_SENDER = Unknown (MMS Part $mid)")
                    }
                }
            } catch (e: Exception) {
                Log.e("ForensicSearch", "Error searching MMS: ${e.message}")
            }

            if (!mmsFound) {
                Log.d("ForensicSearch", "RAGHIB_FOUND_IN_PROVIDER = false")
                Log.d("ForensicSearch", "RAGHIB_ACCESSIBILITY_DETERMINATION: Message is likely RCS (Rich Communication Services) or Samsung Chat Message, which is private to Samsung Messages app and not exposed to third-party apps through Telephony.Sms or Telephony.Mms Content Providers on Samsung devices.")
            }
        }
    }

    fun generateStableId(sender: String, body: String, timestamp: Long): String {
        val cleanSender = sender.filter { it.isLetterOrDigit() }.let {
            if (it.all { c -> c.isDigit() } && it.length > 10) it.takeLast(10) else it.lowercase()
        }
        val cleanBody = body.trim()
        val combined = "$cleanSender|$cleanBody"
        val hash = Math.abs(combined.hashCode())
        return "SMS-$hash"
    }

    // Keep backwards compatibility for any other parts of the app
    suspend fun readInboxAndSync(limit: Int = 10): List<com.sancharsaathi.app.domain.model.RiskResult> {
        val messages = getLatestInboxMessages(limit)
        return messages.map { msg ->
            val analysisId = generateStableId(msg.sender, msg.body, msg.timestamp)
            historyStore.get(analysisId) ?: com.sancharsaathi.app.domain.model.RiskResult(
                analysisId = analysisId,
                riskScore = -2, // Analyzing
                riskLevel = com.sancharsaathi.app.domain.model.RiskLevel.LOW,
                confidence = 0.0,
                reasons = listOf("Analyzing..."),
                signals = emptyList(),
                recommendedAction = "Analyzing message content...",
                shouldBlock = false,
                shouldReport = false,
                detectedUrl = null,
                sender = msg.sender,
                modelVersion = "1.0.0",
                degraded = true,
                smsBody = msg.body,
                timestamp = msg.timestamp
            )
        }
    }
}
