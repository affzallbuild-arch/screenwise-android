package com.screenwise.app

import android.app.Application
import com.screenwise.app.data.AppDatabase
import com.screenwise.app.data.Prefs
import com.screenwise.app.worker.AlertScheduler

class ScreenWiseApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val prefs by lazy { Prefs(this) }
    override fun onCreate() {
        super.onCreate()
        AlertScheduler.schedule(this)
    }
}
