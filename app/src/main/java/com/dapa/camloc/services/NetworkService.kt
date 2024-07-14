package com.dapa.camloc.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.OutputStream
import java.net.*
import java.nio.ByteBuffer


class ClientConfig(val state: UByte, val xPosition: Float, val yPosition: Float, val rotation: Float, val fov: Float) {
    fun toBytes(): ByteArray {
        return ByteBuffer.allocate(Byte.SIZE_BYTES + Float.SIZE_BYTES * 4).apply {
            putChar(state.toInt().toChar())
            putFloat(xPosition)
            putFloat(yPosition)
            putFloat(rotation)
            putFloat(fov)
        }.array()
    }

    companion object {
        fun fromBytes(buffer: ByteArray): ClientConfig {
            return ByteBuffer.allocate(buffer.size).put(buffer).let {
                ClientConfig(it.char.code.toUByte(), it.float, it.float, it.float, it.float)
            }
        }

        fun fromBytes(buffer: ByteBuffer): ClientConfig {
            return buffer.let {
                ClientConfig(it.char.code.toUByte(), it.float, it.float, it.float, it.float)
            }
        }
    }
}

class ClientState(val state: Short, val battery: Short, val cameraIndex: Short, val xResolution: Int, val yResolution: Int, val focus: Float) {
    fun toBytes(): ByteArray {
        return ByteBuffer.allocate(Byte.SIZE_BYTES + Float.SIZE_BYTES * 4).apply {
            putShort(state)
            putShort(battery)
            putShort(cameraIndex)
            putInt(xResolution)
            putInt(yResolution)
            putFloat(focus)
         }.array()
    }

    companion object {
        fun fromBytes(buffer: ByteArray): ClientState {
            return ByteBuffer.allocate(buffer.size).put(buffer).let {
                ClientState(it.short, it.short, it.short, it.int, it.int, it.float)
            }
        }

        fun fromBytes(buffer: ByteBuffer): ClientState {
            return buffer.let {
                ClientState(it.short, it.short, it.short, it.int, it.int, it.float)
            }
        }
    }
}

class NetworkService : Service() {
    interface NetworkEventHandler {
        fun onSetConfig(config: ClientConfig): Any
        fun onSetState(state: ClientState): Any
        fun onConfigAsked(): ClientConfig
        fun onStateAsked(): ClientState
        fun onFlash(): Any
    }

    private var startId: Int = -1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        this.startId = startId
        registerReceiver(discoveryBroadcastReceiver, IntentFilter(DiscoveryService.INTENT_ACTION))

        // serviceHandler?.obtainMessage()?.also { msg ->
        //     msg.arg1 = startId
        //     serviceHandler?.sendMessage(msg)
        // }

        return START_STICKY
    }

    /*fun connect(addr: InetAddress, port: Int) {
        dpPacket = DatagramPacket(ByteArray(Double.SIZE_BYTES), Double.SIZE_BYTES, addr, port)
    }

    fun listenLoop() {
        val stream = tcpSocket.getInputStream()
        out = tcpSocket.getOutputStream()

        var read = 0
        val buf = ByteBuffer.allocate(tcpBuffer.size)

        while(stream.read(tcpBuffer).also { read = it } != -1) {
            Log.d(TAG, "got $read bytes")

            buf.reset()
            buf.put(tcpBuffer)

            when(buf.get()) {
                SET_CONFIG -> handler.onSetConfig(ClientConfig.fromBytes(buf))
                ASK_CONFIG -> out.write(handler.onConfigAsked().toBytes())
                SET_STATE -> handler.onSetState(ClientState.fromBytes(buf))
                ASK_STATE -> out.write(handler.onStateAsked().toBytes())
                FLASH -> handler.onFlash()
                else -> Log.w(TAG, "unrecognised command")
            }
        }
    }

    fun updateX(x: Double) {
        if(!udpSocket.isConnected)
            return

        dpPacket.data = ByteBuffer.allocate(Double.SIZE_BYTES).putDouble(x).array()
        udpSocket.send(dpPacket)
    }

    fun updateConfig(x: Float, y: Float, rotation: Float) {

    }
*/

    // discovery service stuff

    private lateinit var serviceLooper: Looper
    private lateinit var serviceHandler: ServiceHandler

    // Handler that receives messages from the thread
    private inner class ServiceHandler(looper: Looper) : Handler(looper) {

        private lateinit var udpSocket: DatagramSocket
        private lateinit var dpPacket: DatagramPacket
        private lateinit var tcpSocket: Socket
        private lateinit var out: OutputStream

        private var udpBuffer = ByteArray(Double.SIZE_BYTES)
        private var tcpBuffer = ByteArray(1024)

        var handler = object : NetworkEventHandler {
            override fun onSetConfig(config: ClientConfig): Any {
                TODO("Not yet implemented")
            }

            override fun onConfigAsked(): ClientConfig {
                TODO("Not yet implemented")
            }

            override fun onStateAsked(): ClientState {
                TODO("Not yet implemented")
            }

            override fun onSetState(state: ClientState): Any {
                TODO("Not yet implemented")
            }

            override fun onFlash(): Any {
                TODO("Not yet implemented")
            }
        }

        override fun handleMessage(msg: Message) {
            val name = msg.data.getString("name")!!
            val ip = msg.data.getString("ip")!!.drop(1)
            val port = msg.data.getInt("port")

            Log.d(TAG, "got: $name | $ip:$port")

            try {
                val x = InetAddress.getByName(ip)
                Log.d(TAG, "address: ${x.hostAddress}")
            } catch (e: SecurityException) {
                Log.e(TAG, "$e")
            } catch (e: UnknownHostException) {
                Log.e(TAG, "$e")
            }

            try {
                tcpSocket = Socket(ip, port)
                out = tcpSocket.getOutputStream()
                val input = BufferedInputStream(tcpSocket.getInputStream())

                // while(true) {
                //     Log.d(TAG, "running task")
                //     out.write("eyo".toByteArray())
                //     Thread.sleep(2000)
                // }

                var read = 0
                val buf = ByteBuffer.allocate(tcpBuffer.size)

                while(input.read(tcpBuffer).also { read = it } != -1) {
                    Log.d(TAG, "got $read bytes")
                    buf.put(tcpBuffer)

                    while(buf.hasRemaining()) {
                        val first = buf.get()
                        Log.d(TAG, "0: $first")
                    }

                    // when(buf.get()) {
                    //     SET_CONFIG -> handler.onSetConfig(ClientConfig.fromBytes(buf))
                    //     ASK_CONFIG -> out.write(handler.onConfigAsked().toBytes())
                    //     SET_STATE -> handler.onSetState(ClientState.fromBytes(buf))
                    //     ASK_STATE -> out.write(handler.onStateAsked().toBytes())
                    //     FLASH -> handler.onFlash()
                    //     else -> Log.w(TAG, "unrecognised command")
                    // }
                }

            } catch (e: InterruptedException) {
                // Restore interrupt status.
                Thread.currentThread().interrupt()
            }

            stopSelf(msg.arg1)
        }
    }

    override fun onCreate() {
        HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND).apply {
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
        return binder
    }

    companion object {
        const val TAG = "CamlocNetworkService"
        // DNS SRV record to search for
        const val REG_TYPE = "_camloc._tcp"

        // request types
        const val SEND_CONFIG: Byte = 0x0
        const val SEND_STATE: Byte = 0x1

        const val SET_CONFIG: Byte = 0x2
        const val ASK_CONFIG: Byte = 0x3
        const val SET_STATE: Byte = 0x4
        const val ASK_STATE: Byte = 0x5
        const val FLASH: Byte = 0x6
    }
}