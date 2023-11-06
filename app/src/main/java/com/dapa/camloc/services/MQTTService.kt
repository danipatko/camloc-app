package com.dapa.camloc.services

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.dapa.camloc.events.BrokerInfo
import com.dapa.camloc.events.BrokerState
import com.dapa.camloc.events.Empty
import org.eclipse.paho.client.mqttv3.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class MQTTService : Service() {
    private var client: MqttClient? = null
    private var connectionString = ""
    private var lastX = Float.NaN

    var shouldFlash = false
    var shouldClose = false
    private var isBound = false

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        EventBus.getDefault().register(this)
        shouldClose = false
        return START_STICKY
    }

    // replaces the topic wildcard with the client id
    private fun replaceWildcard(topic: String, clientId: String ?= null): String {
        return topic.replace(Regex("\\+"), clientId ?: (client?.clientId ?: "lost"))
    }

    private fun pub(topic: String, payload: ByteArray) {
        client?.publish(replaceWildcard(topic), payload, QOS, RETAIN)
    }

    private fun pub(topic: String, payload: FloatArray) {
        val buf = ByteBuffer.allocate(Float.SIZE_BYTES * payload.size)
        for (x in payload) buf.putFloat(x)
        client?.publish(replaceWildcard(topic), buf.array(), QOS, RETAIN)
    }

    private fun pub(topic: String, payload: Boolean) {
        val buf = ByteBuffer.allocate(1).put(if(payload) 0x1 else 0x0)
        client?.publish(replaceWildcard(topic), buf.array(), QOS, RETAIN)
    }

    fun reconnect(): Boolean {
        val clientId = MqttClient.generateClientId()
        Log.d(TAG, "generated $clientId id")

        try {
            client = MqttClient(connectionString, clientId, null).apply {
                // connect
                val options = MqttConnectOptions()
                options.isCleanSession = false
                // pub disconnect
                options.setWill(replaceWildcard(TOPIC_DISCONNECT, this.clientId), ByteArray(0), 0, false)

                setCallback(object : MqttCallback {
                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        handleMessage(topic, message)
                    }

                    override fun connectionLost(cause: Throwable?) {
                        Log.e(TAG, "Lost broker connection")
                        EventBus.getDefault().post(BrokerState(false))
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                         // Log.d(TAG, "deliveryComplete ${token?.messageId}")
                    }
                })

                connect(options)
                // subs here
                subscribe(arrayOf(TOPIC_ASK_CONFIG, TOPIC_FLASH, TOPIC_DISCONNECT, TOPIC_ASK_STATE, TOPIC_SET_ALL_STATE, TOPIC_SET_STATE))
            }
            return true
        } catch (e: MqttException) {
            Log.e(TAG, "Failed to connect to MQTT broker\n$e")
            return false
        }
    }

    fun handleMessage(topic: String?, message: MqttMessage?) {
        thread {
            Log.d(TAG, "Got message $topic: $message")
            when(topic) {
                TOPIC_ASK_CONFIG -> pubConfig()
                TOPIC_ASK_STATE -> pub(TOPIC_PUB_STATE, isBound)

                TOPIC_SET_ALL_STATE -> if(message != null) handleSetState(message.payload)
                replaceWildcard(TOPIC_SET_STATE) -> if(message != null) handleSetState(message.payload)

                replaceWildcard(TOPIC_FLASH) -> handleFlash()

                else -> Log.d(TAG, message.toString())
            }
        }
    }

    private fun handleFlash() {
        if(client != null) {
            shouldFlash = true
        }
    }

    private fun handleSetState(payload: ByteArray) {
        if(payload[0] == 0x0.toByte()) {
            shouldClose = true
        } else {
            EventBus.getDefault().post(Empty())
        }
    }

    private fun pubConfig() {
        // TODO CONFIG ui
        // x, y, rot
        pub(TOPIC_PUB_CONFIG, floatArrayOf(1f, 2f, 3f))
    }

    fun pubLocation(x: Float) {
        if(lastX.isNaN() && x.isNaN()) return
        lastX = x
        pub(TOPIC_PUB_LOCATE, floatArrayOf(x))
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onConnect(info: BrokerInfo) {
        Log.d(TAG, "onConnect was fired! ${info.connectionString}")
        connectionString = info.connectionString
        if(client == null || !client!!.isConnected) {
            val success = reconnect()
            Toast.makeText(this, if(success) "Broker connected" else "Connection failed", Toast.LENGTH_SHORT).show()
            if(success) {
                pubConfig()
                EventBus.getDefault().post(BrokerState(true))
            }
        }
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        client?.disconnect()
        super.onDestroy()
    }

    // bind to trackeractivity
    private val binder = ServiceBinder()
    inner class ServiceBinder : Binder() {
        fun getService(): MQTTService = this@MQTTService
    }

    override fun onBind(intent: Intent?): IBinder {
        shouldClose = false
        isBound = true
        pub(TOPIC_PUB_STATE, true)
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isBound = false
        pub(TOPIC_PUB_STATE, false)
        return super.onUnbind(intent)
    }

    companion object {
        const val TAG = "MQTTService"

        // pub
        const val TOPIC_PUB_LOCATE = "camloc/+/locate"
        const val TOPIC_PUB_CONFIG = "camloc/+/config"
        const val TOPIC_PUB_STATE = "camloc/+/state"

        // sub
        const val TOPIC_DISCONNECT = "camloc/+/dc"

        const val TOPIC_ASK_CONFIG = "camloc/config"
        const val TOPIC_SET_CONFIG = "camloc/+/config/set"

        const val TOPIC_FLASH = "camloc/+/flash"

        const val TOPIC_ASK_STATE = "camloc/state"
        const val TOPIC_SET_STATE = "camloc/+/state/set"
        const val TOPIC_SET_ALL_STATE = "camloc/state/set"

        const val QOS = 0
        const val RETAIN = false
    }
}