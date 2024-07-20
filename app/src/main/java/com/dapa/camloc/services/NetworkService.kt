package com.dapa.camloc.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.util.Log
import com.dapa.camloc.util.ClientConfig
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.*
import java.nio.ByteBuffer


class NetworkService : Service() {
    // updates on tracker
    interface TrackerEventHandler {
        fun onConfigSet(config: ClientConfig)
        fun onFlash()
    }

    // UI updates on main
    interface MainEventHandler {
        fun onConfigSet(config: ClientConfig)
        fun onBrokerFound(name: String, ip: String, port: Int)
        fun onConnected(name: String, ip: String, port: Int)
        fun onDisconnected()
    }

    fun setConfig(positionX: Float, positionY: Float, rotation: Float) {
        //
    }

    fun setX(x: Float) {
        //
    }

    private fun setConfig(config: ClientConfig) {
        Log.d(TAG, "config: $config")
        mainHandler?.onConfigSet(config)
    }

    private fun onConfigAsked(): ClientConfig {
        return ClientConfig.DEFAULT
    }

    private var startId: Int = -1
    private lateinit var serviceLooper: Looper
    private lateinit var serviceHandler: ServiceHandler

    var mainHandler: MainEventHandler? = null
    var trackerHandler: TrackerEventHandler? = null

    val config = ClientConfig.DEFAULT

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        this.startId = startId
        registerReceiver(discoveryBroadcastReceiver, IntentFilter(DiscoveryService.INTENT_ACTION))
        return START_STICKY
    }

    // Handler that receives messages from the thread
    private inner class ServiceHandler(looper: Looper) : Handler(looper) {
        private lateinit var udpSocket: DatagramSocket
        private lateinit var dpPacket: DatagramPacket
        private lateinit var tcpSocket: Socket
        private lateinit var out: OutputStream

        private var udpBuffer = ByteArray(Double.SIZE_BYTES)
        private var tcpBuffer = ByteArray(1024)

        fun tcpLoop(ip: String, port: Int, name: String) {
            // TODO: handle failure
            try {
                tcpSocket = Socket(ip, port)
            } catch (e: Exception) {
                mainHandler?.onDisconnected()
                return
            }

            mainHandler?.onConnected(name, ip, port)

            out = tcpSocket.getOutputStream()
            val input = BufferedInputStream(tcpSocket.getInputStream())

            var read = 0
            val buf = ByteBuffer.wrap(tcpBuffer)

            while(input.read(tcpBuffer).also { read = it } != -1) {
                Log.d(TAG, "got $read bytes")
                Log.d(TAG, "$buf")

                val comm = buf.get()
                Log.d(TAG, "got: $comm")
                Log.d(TAG, "$buf")

                when(comm) {
                    SET_CONFIG -> setConfig(ClientConfig.fromBytes(buf))
                    ASK_CONFIG -> out.write(onConfigAsked().toBytes())
//                    SET_STATE -> handler.onSetState(ClientState.fromBytes(buf))
//                   ASK_STATE -> out.write(handler.onStateAsked().toBytes())
//                    FLASH -> handler.onFlash()
                    else -> Log.w(TAG, "unrecognised command")
                }

                buf.position(0)
            }
        }

        override fun handleMessage(msg: Message) {
            val name = msg.data.getString("name")!!
            val ip = msg.data.getString("ip")!!.drop(1)
            val port = msg.data.getInt("port")
            mainHandler?.onBrokerFound(name, ip, port)

            Log.d(TAG, "got: $name | $ip:$port")

            try {
                tcpLoop(ip, port, name)
            } catch (e: InterruptedException) {
                // Restore interrupt status.
                Thread.currentThread().interrupt()
            }

            stopSelf(msg.arg1)
        }
    }

    override fun onCreate() {
        HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND).apply {
            start()
            serviceLooper = looper
            serviceHandler = ServiceHandler(looper)
        }
    }

    private val discoveryBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            serviceHandler.obtainMessage().also { msg ->
                msg.arg1 = startId
                msg.data = intent.extras
                serviceHandler.sendMessage(msg)
            }
        }
    }

    // ---

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): NetworkService = this@NetworkService
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "got intent from ${intent.getStringExtra("type")}")
        return binder
    }

    companion object {
        const val TAG = "CamlocNetworkService"

        // request types
        const val SEND_CONFIG: Byte = 0x0
        const val SEND_STATE: Byte = 0x1

        const val SET_CONFIG: Byte = 0x0
        const val ASK_CONFIG: Byte = 0x3
        const val SET_STATE: Byte = 0x4
        const val ASK_STATE: Byte = 0x5
        const val FLASH: Byte = 0x6
    }
}