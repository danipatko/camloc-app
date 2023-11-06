package com.dapa.camloc.events

class Empty () {
}

class BrokerState (isConnected: Boolean) {
    val connected: Boolean
    init {
        connected = isConnected
    }
}