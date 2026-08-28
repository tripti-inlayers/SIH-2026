package com.sancharsaathi.app

import android.app.Application
import android.util.Log
import com.sancharsaathi.app.di.AppModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SancharSaathiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppModule.initialize(this)

        // Eagerly connect to backend API and warm up socket connection on app launch
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val targetUrl = AppModule.networkConfigStore.getBaseUrl()
                Log.d("SancharSaathiApp", "STARTUP_CONNECT: Connecting to backend at $targetUrl")
                val response = AppModule.apiService.health()
                Log.d("SancharSaathiApp", "STARTUP_CONNECT: Connected! status=${response.body()?.status}")
            } catch (e: Exception) {
                Log.d("SancharSaathiApp", "STARTUP_CONNECT: Initial check (${e.message}) - ready for on-device fallback.")
            }
        }
    }
}
