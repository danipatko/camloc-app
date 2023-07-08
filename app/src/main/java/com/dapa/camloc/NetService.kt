package com.dapa.camloc

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import java.util.Random

class NetService : Service() {
    private val binder = NetBinder()

    private val mGenerator = Random()
    val randomNumber: Int
        get() = mGenerator.nextInt(100)

    inner class NetBinder : Binder() {
        fun getService(): NetService = this@NetService
    }

    override fun onBind(intent: Intent): IBinder {
         return binder
    }

    companion object {
        private const val TAG = "CamLocNetService"
    }
}
