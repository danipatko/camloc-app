package com.dapa.camloc.services

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.dapa.camloc.events.BrokerInfo
import com.dapa.camloc.events.Empty
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.nio.ByteBuffer

class MQTTService : Service() {
    private var client: MqttClient? = null
    private var connectionString = ""
    private var lastX = Float.NaN

    var shouldFlash = false
    var shouldClose = false

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        EventBus.getDefault().register(this)
        shouldClose = false
        return START_STICKY
    }

    fun reconnect(): Boolean {
        val clientId = MqttClient.generateClientId()
        Log.d(TAG, "generated $clientId id")

        try {
            client = MqttClient(connectionString, clientId, MemoryPersistence()).apply {
                // connect
                val options = MqttConnectOptions()
                options.isCleanSession = true
                // pub disconnect
                options.setWill(replaceWildcard(TOPIC_DISCONNECT, this.clientId), ByteArray(0), 0, false)

                setCallback(object : MqttCallback {
                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        handleMessage(topic, message)
                    }

                    override fun connectionLost(cause: Throwable?) {
                        Log.e(TAG, "Lost broker connection")
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                         Log.d(TAG, "deliveryComplete ${token?.messageId}")
                    }
                })

                connect(options)
                // subs here
                subscribe(arrayOf(TOPIC_ASK_CONFIG, TOPIC_FLASH, TOPIC_CAM_ON, TOPIC_CAM_OFF, TOPIC_THIS_CAM_ON, TOPIC_THIS_CAM_OFF, TOPIC_DISCONNECT))
            }
            return true
        } catch (e: MqttException) {
            Log.e(TAG, "Failed to connect to MQTT broker\n$e")
            return false
        }
    }

    fun handleMessage(topic: String?, message: MqttMessage?) {
        Log.d(TAG, "Got message $topic")

        when(topic) {
            TOPIC_ASK_CONFIG -> pubConfig()

            TOPIC_CAM_ON -> handleCamState(true)
            replaceWildcard(TOPIC_THIS_CAM_ON) -> handleCamState(true)

            TOPIC_CAM_OFF -> handleCamState(false)
            replaceWildcard(TOPIC_THIS_CAM_OFF) -> handleCamState(false)

            replaceWildcard(TOPIC_FLASH) -> handleFlash()

            else -> Log.d(TAG, message.toString())
        }
    }

    private fun handleFlash() {
        if(client != null) {
            shouldFlash = true
        }
    }

    private fun handleCamState(on: Boolean) {
        if(on) {
            EventBus.getDefault().post(Empty())
        } else {
            shouldClose = true
        }
    }

    private fun pubConfig() {
        Log.d(TAG, replaceWildcard(TOPIC_GET_CONFIG))
        client?.publish(replaceWildcard(TOPIC_GET_CONFIG),
            // x, y, rot
            ByteBuffer.allocate(4 * 3).putFloat(1f).putFloat(2f).putFloat(3f).array(), 2, false)
    }

    fun pubLocation(x: Float) {
        if(lastX.isNaN() && x.isNaN()) return
        lastX = x
        client?.publish(replaceWildcard(TOPIC_LOCATE), ByteBuffer.allocate(4).putFloat(x).array(), 0, false)
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
            }
        }
    }

    // replaces the topic wildcard with the client id
    private fun replaceWildcard(topic: String, clientId: String ?= null): String {
        return topic.replace(Regex("\\+"), clientId ?: (client?.clientId ?: "lost"))
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
        return binder
    }

    companion object {
        const val TAG = "MQTTService"

        const val TOPIC_LOCATE = "camloc/+/locate"
        const val TOPIC_DISCONNECT = "camloc/+/dc"

        const val TOPIC_GET_CONFIG = "camloc/+/config/get"
        const val TOPIC_SET_CONFIG = "camloc/+/config/set"

        const val TOPIC_FLASH = "camloc/+/flash"

        const val TOPIC_CAM_ON = "camloc/camstate/on"
        const val TOPIC_CAM_OFF = "camloc/camstate/off"
        const val TOPIC_THIS_CAM_ON = "camloc/+/camstate/on"
        const val TOPIC_THIS_CAM_OFF = "camloc/+/camstate/off"

        const val TOPIC_ASK_CONFIG = "camloc/config"
    }
}