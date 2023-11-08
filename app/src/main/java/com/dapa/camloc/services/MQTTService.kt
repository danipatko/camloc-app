package com.dapa.camloc.services

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.dapa.camloc.CameraConfig
import com.dapa.camloc.MQTTClientWrapper
import com.dapa.camloc.events.BrokerInfo
import com.dapa.camloc.events.BrokerState
import com.dapa.camloc.events.StartTrackerActivity
import org.eclipse.paho.client.mqttv3.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class MQTTService : Service() {
    lateinit var client: MQTTClientWrapper

    private lateinit var mCameraConfig: CameraConfig
    var mCameraIndex: Int = 0
        set(value) {
            field = value
            //
        }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        EventBus.getDefault().register(this)
        mCameraConfig = CameraConfig(this)

        client = MQTTClientWrapper().apply {
            setMessageListener(object : MQTTClientWrapper.OnMessageListener {
                override fun onConnectionLost() {
                    EventBus.getDefault().post(BrokerState(false))
                }

                override fun onFinish() {
                    mChangeListener.onFinish()
                }

                override fun onFlash() {
                    mChangeListener.onFlash()
                }
            })
        }

        return START_STICKY
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onConnect(info: BrokerInfo) {
        Log.d(TAG, "onConnect was fired! ${info.connectionString}")

        val success = client.connect(info.connectionString)
        EventBus.getDefault().post(BrokerState(success))
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        client.destroy()
        super.onDestroy()
    }

    // bind to trackeractivity
    private val binder = ServiceBinder()
    inner class ServiceBinder : Binder() {
        fun getService(): MQTTService = this@MQTTService
    }

    // ---

    interface OnChangeListener {
        fun onChanged(progress: Int)
        fun onFlash()
        fun onFinish()
    }

    private lateinit var mChangeListener: OnChangeListener
    fun setOnChangeListener(onChangeListener: OnChangeListener) {
        mChangeListener = onChangeListener
    }

    override fun onBind(intent: Intent?): IBinder {
        client.isBound = true
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        client.isBound = false
        return super.onUnbind(intent)
    }

    companion object {
        const val TAG = "MQTTService"
    }
}