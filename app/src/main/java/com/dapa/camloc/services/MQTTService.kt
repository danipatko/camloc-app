package com.dapa.camloc.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.dapa.camloc.events.BrokerInfo
import com.dapa.camloc.events.XInfo
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.nio.ByteBuffer

class MQTTService : Service() {
    private var client: MqttClient? = null
    private var connectionString = ""

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        EventBus.getDefault().register(this)
        return START_STICKY
    }

    fun reconnect() {
        val clientId = MqttClient.generateClientId()

        try {
            client = MqttClient(connectionString, clientId, MemoryPersistence()).apply {
                // connect
                val options = MqttConnectOptions()
                options.isCleanSession = true

                setCallback(object : MqttCallback {
                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        handleMessage(topic, message)
                    }

                    override fun connectionLost(cause: Throwable?) {
                        Log.e(TAG, "Lost broker connection")
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                        Log.d(TAG, "Delivered '${token?.message}'")
                    }
                })

                connect(options)
            }
        } catch (e: MqttException) {
            Log.e(TAG, "Failed to connect to MQTT broker\n$e")
        }
    }

    fun handleMessage(topic: String?, message: MqttMessage?) {
        if(topic == TOPIC_CONFIG_UPDATE) {
            // TODO: update config
            Log.d(TAG, message.toString())
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onFoundX(x: XInfo) {
        client?.publish(TOPIC_LOCATE, x.bytes, 0, false)
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onConnect(info: BrokerInfo) {
        Log.d(TAG, "onConnect was fired! ${info.connectionString}")
        connectionString = info.connectionString
        Log.d(TAG, "$client")
        if(client == null || !client!!.isConnected) {
            reconnect()
        }
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        client?.disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        const val TAG = "MQTTService"
        const val TOPIC_CONFIG_UPDATE = "camloc/config"
        const val TOPIC_LOCATE = "camloc/locate"
    }
}