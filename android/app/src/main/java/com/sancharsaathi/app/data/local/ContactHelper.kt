package com.sancharsaathi.app.data.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

object ContactHelper {
    private val contactCache = ConcurrentHashMap<String, String>()

    fun getContactName(context: Context, rawSender: String?): String? {
        if (rawSender.isNullOrBlank()) return null
        val sender = rawSender.trim()

        // Check in-memory cache first
        contactCache[sender]?.let { return it }

        // If it's an organization or service code with too few digits (e.g. "VK-HDFCBK", "SBIINB", "MANUAL_INPUT")
        val digitsOnly = sender.filter { it.isDigit() }
        if (digitsOnly.length < 5) {
            return null
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(sender)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameCol = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameCol != -1) {
                        val name = cursor.getString(nameCol)
                        if (!name.isNullOrBlank()) {
                            contactCache[sender] = name
                            name
                        } else null
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.e("ContactHelper", "Error resolving contact for $sender: ${e.message}")
            null
        }
    }
}
