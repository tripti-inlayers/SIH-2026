package com.example.sancharsaathi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        notifyPermissionStatus()
    }

    private val localSmsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val payload = intent?.getStringExtra("payload") ?: return
            Log.d("MainActivity", "Received local SMS intent: $payload")
            val escapedPayload = payload.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
            runOnUiThread {
                webView.evaluateJavascript("if(window.onSmsReceived) { window.onSmsReceived('$escapedPayload'); }", null)
            }
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
                val cursor = contentResolver.query(
                    android.provider.Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(
                        android.provider.Telephony.Sms._ID,
                        android.provider.Telephony.Sms.ADDRESS,
                        android.provider.Telephony.Sms.BODY,
                        android.provider.Telephony.Sms.DATE
                    ),
                    null,
                    null,
                    "${android.provider.Telephony.Sms.DATE} DESC"
                )
                cursor?.use {
                    var count = 0
                    val max = if (limit <= 0) 30 else limit
                    while (it.moveToNext() && count < max) {
                        val id = it.getString(it.getColumnIndexOrThrow(android.provider.Telephony.Sms._ID)) ?: ""
                        val address = it.getString(it.getColumnIndexOrThrow(android.provider.Telephony.Sms.ADDRESS)) ?: "Unknown"
                        val body = it.getString(it.getColumnIndexOrThrow(android.provider.Telephony.Sms.BODY)) ?: ""
                        val date = it.getLong(it.getColumnIndexOrThrow(android.provider.Telephony.Sms.DATE))
                        
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
            return com.google.gson.Gson().toJson(smsList)
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
        val filter = IntentFilter("com.example.sancharsaathi.SMS_INTERCEPTED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(localSmsReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(localSmsReceiver, filter)
        }
        notifyPermissionStatus()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(localSmsReceiver)
        } catch (e: Exception) {
            Log.w("MainActivity", "Receiver not registered", e)
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
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
        }
    }
}
