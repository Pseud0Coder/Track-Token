package com.tracktoken

import android.app.Application
import com.tracktoken.work.RefreshWorker

class TrackTokenApp : Application() {

    override fun onCreate() {
        super.onCreate()
        RefreshWorker.schedule(this)
    }
}
