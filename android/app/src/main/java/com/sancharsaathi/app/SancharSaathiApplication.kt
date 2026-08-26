package com.sancharsaathi.app

import android.app.Application
import com.sancharsaathi.app.di.AppModule

class SancharSaathiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppModule.initialize(this)
    }
}
