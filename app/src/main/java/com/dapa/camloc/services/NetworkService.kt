package com.dapa.camloc.services

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

class NetworkService : Service() {
    private val binder = NetBinder()

    private var currentX: Float = Float.NaN

    // called by activity
    fun setX(x: Float) {
        currentX = x
    }

    override fun onCreate() {
        super.onCreate()
    }

    inner class NetBinder : Binder() {
        fun getService(): NetworkService = this@NetworkService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    companion object {
        private const val TAG = "CamLocNetworkService"
    }
}
