package com.koel.app

import android.app.Application
import com.koel.app.service.ShizukuManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuManager.init()
    }
}
