package com.sancharsaathi.app.data.local

import android.content.Context
import android.content.SharedPreferences

enum class ConnectionMode {
    USB,
    WIFI
}

class NetworkConfigStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("network_config_prefs", Context.MODE_PRIVATE)

    var connectionMode: ConnectionMode
        get() {
            val modeStr = prefs.getString(KEY_MODE, ConnectionMode.USB.name)
            return try {
                ConnectionMode.valueOf(modeStr ?: ConnectionMode.USB.name)
            } catch (e: Exception) {
                ConnectionMode.USB
            }
        }
        set(value) {
            prefs.edit().putString(KEY_MODE, value.name).commit()
            android.util.Log.d("NetworkConfig", "CONNECTION_MODE_CHANGED\nmode=${value.name}\nactiveBaseUrl=${getBaseUrl()}")
        }

    var wifiHost: String
        get() = prefs.getString(KEY_WIFI_HOST, DEFAULT_WIFI_HOST) ?: DEFAULT_WIFI_HOST
        set(value) {
            prefs.edit().putString(KEY_WIFI_HOST, value.trim()).commit()
        }

    var wifiPort: Int
        get() = prefs.getInt(KEY_WIFI_PORT, DEFAULT_WIFI_PORT)
        set(value) {
            prefs.edit().putInt(KEY_WIFI_PORT, value).commit()
        }

    fun getBaseUrl(): String {
        return when (connectionMode) {
            ConnectionMode.USB -> "http://127.0.0.1:8000/"
            ConnectionMode.WIFI -> {
                val host = wifiHost.ifBlank { DEFAULT_WIFI_HOST }
                val port = if (wifiPort in 1..65535) wifiPort else DEFAULT_WIFI_PORT
                "http://$host:$port/"
            }
        }
    }

    companion object {
        private const val KEY_MODE = "connection_mode"
        private const val KEY_WIFI_HOST = "wifi_host"
        private const val KEY_WIFI_PORT = "wifi_port"

        const val DEFAULT_WIFI_HOST = "192.168.29.24"
        const val DEFAULT_WIFI_PORT = 8000
    }
}
