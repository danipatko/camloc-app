package com.dapa.camloc.events

import java.net.InetAddress

class BrokerInfo (host: InetAddress, port: Int) {
    val connectionString: String
    val display: String

    init {
        connectionString = "${MQTT_PROTOCOL}:/$host:$port"
        display = host.hostAddress ?: ""
    }

    companion object {
        const val MQTT_PROTOCOL = "tcp"
    }
}