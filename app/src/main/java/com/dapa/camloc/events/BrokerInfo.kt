package com.dapa.camloc.events

import java.net.InetAddress

class BrokerInfo (host: InetAddress, port: Int) {
    val connectionString: String

    init {
        connectionString = "${MQTT_PROTOCOL}:/$host:$port"
    }

    companion object {
        const val MQTT_PROTOCOL = "tcp"
    }
}