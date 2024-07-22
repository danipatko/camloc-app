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
        fun onConfigSet(config: ClientConfig) // relevant fields: cameraIndex, resolution, focus
        fun onStateSet(state: Byte)
        fun onFlash()
    }

    // UI updates on main
    interface MainEventHandler {
        fun onConfigSet(config: ClientConfig) // relevant fields: x pos, y pos, rotation
        fun onStateSet(state: Byte)
        fun onBrokerFound(name: String, ip: String, port: Int)
        fun onConnected(name: String, ip: String, port: Int)
        fun onDisconnected()
    }

    // called by TrackerActivity
    fun setX(x: Float) {
        //
    }

    fun onStateChanged(state: Byte) {
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

    private lateinit var out: OutputStream

    // Handler that receives messages from the thread
    private inner class ServiceHandler(looper: Looper) : Handler(looper) {
        private lateinit var udpSocket: DatagramSocket
        private lateinit var dpPacket: DatagramPacket
        private lateinit var tcpSocket: Socket

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
                    SET_CONFIG -> {
                        config.setConfig(buf)
                        mainHandler?.onConfigSet(config)
                        out.write(byteArrayOf(SET_CONFIG, *config.toBytes())) // update
                    }
                    ASK_CONFIG -> out.write(config.toBytes())
                    SET_STATE -> {
                        val state = buf.get()
                        config.state = state
                        mainHandler?.onStateSet(state)
                        trackerHandler?.onStateSet(state)
                    }
                    SET_CAMERA -> {
                        config.setCamera(buf)
                    }
                    SET_FLASH -> trackerHandler?.onFlash()

                    else -> Log.w(TAG, "unrecognised command")
                }

                buf.position(0)
            }
        }

        override fun handleMessage(msg: Message) {
            val name = msg.data.getString("name")!!
            val ip = msg.data.getString("ip")!!.drop(1)
            val port = msg.data.getInt("port")
            Log.d(TAG, "got: $name | $ip:$port")

            mainHandler?.onBrokerFound(name, ip, port)

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

        const val ASK_CONFIG: Byte = 0x0
        const val SET_CONFIG: Byte = 0x1
        const val SET_STATE: Byte = 0x2
        const val SET_CAMERA: Byte = 0x3
        const val SET_FLASH: Byte = 0x4

    }
}