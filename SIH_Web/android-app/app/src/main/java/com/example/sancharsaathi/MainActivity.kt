package com.example.sancharsaathi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import com.google.gson.Gson
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var lastSeenSmsId: String = ""

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        notifyPermissionStatus()
        checkLatestSms()
    }

    // Real-time ContentObserver on the SMS provider
    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            checkLatestSms()
        }
    }

    // Dual-purpose receiver: catches local broadcast & direct SMS_RECEIVED when app is in foreground
    private val localSmsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            
            if (intent.action == "com.example.sancharsaathi.SMS_INTERCEPTED") {
                val payload = intent.getStringExtra("payload") ?: return
                dispatchPayloadToWeb(payload)
            } else if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                for (sms in messages) {
                    val sender = sms.displayOriginatingAddress ?: ""
                    val body = sms.displayMessageBody ?: ""
                    val date = sms.timestampMillis
                    val payloadMap = mapOf(
                        "id" to "live-${System.currentTimeMillis()}",
                        "sender" to sender,
                        "content" to body,
                        "date" to date
                    )
                    dispatchPayloadToWeb(Gson().toJson(payloadMap))
                }
            }
        }
    }

    private fun dispatchPayloadToWeb(payloadJson: String) {
        Log.d("MainActivity", "Dispatching real-time SMS to web: $payloadJson")
        val escapedPayload = payloadJson.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        runOnUiThread {
            webView.evaluateJavascript("if(window.onSmsReceived) { window.onSmsReceived('$escapedPayload'); }", null)
        }
    }

    private fun checkLatestSms() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return
        try {
            val cursor = contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE
                ),
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getString(it.getColumnIndexOrThrow(Telephony.Sms._ID)) ?: ""
                    if (id.isNotEmpty() && id != lastSeenSmsId) {
                        lastSeenSmsId = id
                        val address = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "Unknown"
                        val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                        val date = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))
                        val payloadMap = mapOf(
                            "id" to id,
                            "sender" to address,
                            "content" to body,
                            "date" to date
                        )
                        dispatchPayloadToWeb(Gson().toJson(payloadMap))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error reading latest SMS", e)
        }
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun isNative(): Boolean = true

        @JavascriptInterface
        fun checkSmsPermission(): Boolean {
            val granted = ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED
            return granted
        }

        @JavascriptInterface
        fun requestSmsPermission() {
            runOnUiThread {
                requestPermissions()
            }
        }

        @JavascriptInterface
        fun getInboxSms(limit: Int): String {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                return "[]"
            }
            val smsList = mutableListOf<Map<String, Any>>()
            try {
                val max = if (limit <= 0) 15 else limit.coerceAtMost(20)
                val cursor = contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(
                        Telephony.Sms._ID,
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE
                    ),
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC"
                )
                cursor?.use {
                    var count = 0
                    while (it.moveToNext() && count < max) {
                        val id = it.getString(it.getColumnIndexOrThrow(Telephony.Sms._ID)) ?: ""
                        val address = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "Unknown"
                        val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                        val date = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))
                        
                        smsList.add(mapOf(
                            "id" to id,
                            "sender" to address,
                            "content" to body,
                            "date" to date
                        ))
                        count++
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error reading SMS inbox", e)
            }
            return Gson().toJson(smsList)
        }

        // Native HTTP bridge that bypasses all WebView CORS / Mixed Content restrictions
        @JavascriptInterface
        fun performNativeFetch(targetUrl: String, postDataJson: String): String {
            return try {
                Log.d("MainActivity", "Native fetch calling: $targetUrl")
                val url = URL(targetUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.doOutput = true
                conn.doInput = true

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(postDataJson)
                    writer.flush()
                }

                val responseCode = conn.responseCode
                Log.d("MainActivity", "Native fetch HTTP response: $responseCode")
                val inputStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseText = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                conn.disconnect()
                responseText
            } catch (e: Exception) {
                Log.e("MainActivity", "Native fetch error to $targetUrl: ${e.message}", e)
                "{\"error\": \"${e.message}\"}"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        webView = WebView(this)
        setContentView(webView)

        setupWebView()
        requestPermissions()
    }

    override fun onResume() {
        super.onResume()
        
        val filter = IntentFilter().apply {
            addAction("com.example.sancharsaathi.SMS_INTERCEPTED")
            addAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
            priority = 999
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(localSmsReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(localSmsReceiver, filter)
        }

        try {
            contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, smsObserver)
        } catch (e: Exception) {
            Log.e("MainActivity", "Could not register ContentObserver", e)
        }

        notifyPermissionStatus()
        checkLatestSms()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(localSmsReceiver)
        } catch (e: Exception) {
            Log.w("MainActivity", "Receiver not registered", e)
        }
        try {
            contentResolver.unregisterContentObserver(smsObserver)
        } catch (e: Exception) {
            Log.w("MainActivity", "Observer not registered", e)
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            // Allow mixed content (HTTP API calls from HTTPS assets origin)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.addJavascriptInterface(AndroidBridge(), "AndroidNativeBridge")

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: android.webkit.WebResourceRequest
            ): android.webkit.WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                notifyPermissionStatus()
                checkLatestSms()
            }
        }

        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    private fun notifyPermissionStatus() {
        val isGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

        runOnUiThread {
            webView.evaluateJavascript(
                "if(window.onNativeBridgeReady) { window.onNativeBridgeReady($isGranted); }",
                null
            )
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted.toTypedArray())
        } else {
            notifyPermissionStatus()
            checkLatestSms()
        }
    }
}
