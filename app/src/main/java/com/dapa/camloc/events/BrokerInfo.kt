package com.dapa.camloc.events

import com.dapa.camloc.services.DiscoveryService
import java.net.InetAddress

class BrokerInfo (host: InetAddress, port: Int) {
    val connectionString: String

    init {
        connectionString = "${DiscoveryService.MQTT_PROTOCOL}://$host:$port/mqtt"
    }
}