package com.dapa.camloc

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread


/*
- minden big-endian
- ports:
    - M = main
    - S = starter
    - ? = sender
    - * = any

outer loop
    wait for messages [RECV UDP:M]
    if ping [TODO(Command::Ping)]
        reply with status [SEND UDP:M->? TODO(HostStatus(ConfiglessClientStatus::Idle))]
        continue
    if organizer start [
            RECV UDP:S TODO(Command::StartConfigless),
            ip_len: u16, ip: [u8],
        ]
        connect [SEND UDP:M->M TODO(Command::Connect), x: f64, y: f64, rotation: f64, fov: f64]
    inner loop
        receive message
            if stop [RECV UDP:M TODO(Command::Stop)]
                send disconnect [SEND UDP:M->M TODO(Command::Disconnect)]
                break (inner)
            if server shutdown [RECV UDP:M TODO(Command::ServerShutdown)]
                break (inner)
            if ping [RECV UDP:M TODO(Command::Ping)]
                reply with status [SEND TODO(HostStatus(ClientStatus::Running))]
        find x value
        send value to server [SEND UDP:M->M TODO(Command::UpdateValue), value: f64]
        if info changed
            send info update [SEND UDP:M->M TODO(Command::UpdateInfo), x: f64, y: f64, rotation: f64, fov: f64]
*/

enum class Command(val value: Byte) {
    PING(0x01),
    CONNECT(0x03),
    DISCONNECT(0x05),
    UPDATE_VALUE(0x06),
    UPDATE_INFO(0x07),
    START_CONFIGLESS(0x02),
    STOP(0x04),
}

enum class HostStatus(val value: Byte) {
    CLIENT_RUNNING(0x01),
    CLIENT_IDLE(0x02),
}

class NetService : Service() {
    private val binder = NetBinder()

    // udp & buffer
    private lateinit var udpSocket: DatagramSocket
    private val buffer = ByteArray(UDP_BUF_SIZE)
    private val udpPacket = DatagramPacket(buffer, buffer.size)

    private lateinit var tcpSocket: Socket

    private var currentX: Float = Float.NaN

    // called by activity
    fun setX(x: Float) {
        currentX = x
    }

    private fun logic() {
        while (true) {
            Log.d(TAG, "Listening on port $PORT_MAIN")
            udpSocket = DatagramSocket(PORT_MAIN)
            Log.d(TAG, "Waiting for connections")

            // wait for ping [RECV UDP:M TODO(Command::Ping)]
            do udpSocket.receive(udpPacket)
            while (udpPacket.data[0] != Command.PING.value)
            // reply with status [SEND UDP:M->? TODO(HostStatus(ConfiglessClientStatus::Idle))]
            udpSocket.send(DatagramPacket(byteArrayOf(HostStatus.CLIENT_IDLE.value), 1))
            // if organizer start [
            //     RECV UDP:S TODO(Command::StartConfigless),
            //     ip_len: u16, ip: [u8],
            // ]
            do udpSocket.receive(udpPacket)
            while (udpPacket.data[0] != Command.START_CONFIGLESS.value)

            val wrapped = ByteBuffer.wrap(udpPacket.data).order(ByteOrder.BIG_ENDIAN)
            wrapped.position(1) // skip first byte
            val len = wrapped.short // number of bytes to read
            val ipBytes = ByteArray(len.toInt())
            wrapped.get(ipBytes)

            Log.d(TAG, "Remote: ${udpSocket.remoteSocketAddress}")
            Log.d(TAG, "Received connection")
            // Log.d(TAG, buff.toString())
        }
    }

    override fun onCreate() {
        super.onCreate()
        thread {
            logic()
        }
    }

    inner class NetBinder : Binder() {
        fun getService(): NetService = this@NetService
    }

    override fun onBind(intent: Intent): IBinder {
         return binder
    }

    companion object {
        private const val TAG = "CamLocNetService"
        private const val UDP_BUF_SIZE = 32

        private const val PORT_MAIN = 1234
        private const val PORT_STARTER = 1236
        private const val PORT_ANY = 1111
    }
}
