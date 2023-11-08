package com.dapa.camloc

import android.util.Log
import com.dapa.camloc.events.StartTrackerActivity
import org.eclipse.paho.client.mqttv3.*
import org.greenrobot.eventbus.EventBus
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class MQTTClientWrapper () {
    private var client: MqttClient? = null

    var connectionString: String? = null
        set(value) {
            field = value
            // reconnect on connection string change
            client?.disconnect()
            if(value != null) connect(value)
        }

    // same as 'state'
    var isBound: Boolean = false
        set(value) {
            field = value
            pub(TOPIC_PUB_STATE, value)
        }

    var lastX: Float = Float.NaN
        set(value) {
            if(!field.equals(value)) {
                pub(TOPIC_PUB_LOCATE, floatArrayOf(value))
            }
            field = value
        }

    interface OnMessageListener {
        fun onFlash()
        fun onFinish()
        fun onConnectionLost()
    }

    private lateinit var mMessageListener: OnMessageListener
    fun setMessageListener(onChangeListener: OnMessageListener) {
        mMessageListener = onChangeListener
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

    // returns true on success
    fun connect(connectionString: String): Boolean {
        val clientId = MqttClient.generateClientId()
        Log.d(TAG, "generated $clientId id")

        try {
            client = MqttClient(connectionString, clientId, null).apply {
                // connect
                val options = MqttConnectOptions()
                options.isCleanSession = false
                options.connectionTimeout = 15
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
                        // Log.d(TAG, "deliveryComplete ${token?.messageId}")
                    }
                })

                connect(options)

                // subs here
                subscribe(arrayOf(TOPIC_ASK_CONFIG, TOPIC_FLASH, TOPIC_DISCONNECT, TOPIC_ASK_STATE, TOPIC_SET_ALL_STATE, TOPIC_SET_STATE))
            }

            pubConfig()

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
                // config
                TOPIC_ASK_CONFIG -> pubConfig()
                replaceWildcard(TOPIC_SET_CONFIG) -> if(message != null) setConfig(message.payload)

                // state
                TOPIC_ASK_STATE -> pub(TOPIC_PUB_STATE, isBound)
                TOPIC_SET_ALL_STATE -> if(message != null) handleSetState(message.payload)
                replaceWildcard(TOPIC_SET_STATE) -> if(message != null) handleSetState(message.payload)

                // flash
                replaceWildcard(TOPIC_FLASH) -> mMessageListener.onFlash()

                else -> Log.d(TAG, message.toString())
            }
        }
    }

    // TOPIC_SET_ALL_STATE, TOPIC_SET_STATE
    private fun handleSetState(payload: ByteArray) {
        if(payload[0] == 0x0.toByte()) {
            mMessageListener.onFinish()
        } else {
            EventBus.getDefault().post(StartTrackerActivity())
        }
    }

    // TOPIC_PUB_CONFIG
    private fun pubConfig() {
        // TODO get config
        // mCameraConfig.cameraParams[mCameraIndex].fovX
        // x, y, rot, fov
        pub(TOPIC_PUB_CONFIG, floatArrayOf(1f, 2f, 3f, 0f))
    }

    private fun setConfig(payload: ByteArray) {
        // pub(TOPIC_PUB_CONFIG, floatArrayOf(1f, 2f, 3f, 0f))
    }

    fun destroy() {
        isBound = false
        client?.disconnect()
    }

    companion object {
        const val TAG = "MQTTClientWrapper"

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