package com.sancharsaathi.app

import android.app.Application
import com.sancharsaathi.app.di.AppModule
import com.sancharsaathi.app.domain.capture.SmsCaptureManager

class SancharSaathiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppModule.initialize(this)
        SmsCaptureManager.startListening(this)
    }
}
