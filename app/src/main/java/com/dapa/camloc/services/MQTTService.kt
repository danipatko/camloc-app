package com.dapa.camloc.services

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.dapa.camloc.CameraConfig
import com.dapa.camloc.MQTTClientWrapper
import com.dapa.camloc.events.BrokerInfo
import com.dapa.camloc.events.BrokerState
import com.dapa.camloc.events.ConfigChanged
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MQTTService : Service() {
    lateinit var client: MQTTClientWrapper
    lateinit var mCameraConfig: CameraConfig

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        EventBus.getDefault().register(this)

        mCameraConfig = CameraConfig(this, object : CameraConfig.OnChangeListener {
            override fun onConfigChange(config: FloatArray) {
                client.config = config
                EventBus.getDefault().post(ConfigChanged())
            }
        })

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

                override fun onUpdateConfig(payload: ByteArray) {
                    mCameraConfig.set(payload)
                }
            })
        }

        return START_STICKY
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onBrokerFound(info: BrokerInfo) {
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
        fun onFlash()
        fun onFinish()
    }

    private lateinit var mChangeListener: OnChangeListener
    fun setOnChangeListener(onChangeListener: OnChangeListener) {
        mChangeListener = onChangeListener
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    companion object {
        const val TAG = "MQTTService"
    }
}
