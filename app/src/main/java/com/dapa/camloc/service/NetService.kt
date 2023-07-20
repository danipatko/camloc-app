package com.dapa.camloc.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.Arrays
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
            RECV UDP:M TODO(Command::StartConfigless),
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

enum class Command(val v: Byte) {
    PING(0x30),
    CONNECT(0x31),
    DISCONNECT(0x32),
    UPDATE_VALUE(0x33),
    UPDATE_INFO(0x34),
    START_CONFIGLESS(0x35),
    SERVER_SHUTDOWN(0x36),
}

enum class Status(val v: Byte) {
    RUNNING(0x0),
    IDLE(0x1)
}

class NetService : Service() {
    // Binder given to clients.
    private val binder = LocalBinder()

    private lateinit var socket: DatagramSocket
    private val buffer = ByteArray(BUF_SIZE)
    private val packet = DatagramPacket(buffer, BUF_SIZE)

    private var serverAddress: InetAddress? = null;

    private fun loop() {
        socket = DatagramSocket(PORT_MAIN)

        while (true) {
            socket.receive(packet)

            val xd = packet.data.joinToString(",") { it.toString(16) }
            Log.d(TAG, "GOT: [$xd]")

            // check ping
            if(packet.data[0] == Command.PING.v) {
                Log.d(TAG, "SENDING BACK")
                socket.send(DatagramPacket(byteArrayOf(Status.IDLE.v), 1, packet.address, packet.port))
                continue
            }

            if(packet.data[0] == Command.START_CONFIGLESS.v) {
                val len = (packet.data[1].toInt() + packet.data[2] shl (8)).toShort()
                Log.d(TAG, "length: $len")
                Log.d(TAG, Arrays.copyOfRange(packet.data, 3, 3 + len).joinToString(","))
                serverAddress = InetAddress.getByAddress(Arrays.copyOfRange(packet.data, 3, 3 + len))
                Log.d(TAG, "$serverAddress")

                val x: Double = 0.0
                val y: Double = 0.0
                val rotation: Double = 0.0
                val fov: Double = 0.0

                val conv = ByteBuffer.allocate(java.lang.Long.BYTES).putLong(java.lang.Double.doubleToLongBits(x)).array()
                Log.d(TAG, conv.joinToString(",") { it.toString(16) })

                // send details
                val data = byteArrayOf(Command.CONNECT.v)
                socket.send(DatagramPacket(data, data.size, serverAddress, PORT_MAIN))

                while (true) {
                    break;
                }

            }

        }

        Log.d(TAG, "LOOP WAS CALLED")
    }

    inner class LocalBinder : Binder() {
        // Return this instance of com.dapa.camloc.service.LocalService so clients can call public methods.
        fun getService(): NetService = this@NetService
    }

    override fun onBind(intent: Intent): IBinder {
        thread {
            loop()
        }

        return binder
    }

    companion object {
        const val TAG = "CamLocNetService"

        const val BUF_SIZE = 512

        const val PORT_MAIN = 1111
        const val PORT_STARTER = 1111
        const val PORT_SENDER = 1111
        const val PORT_ANY = 1111

    }
}
