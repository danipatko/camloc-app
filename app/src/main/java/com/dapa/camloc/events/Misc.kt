package com.dapa.camloc.events

import java.net.Inet4Address

class StartTrackerActivity

data class BrokerState (val isConnected: Boolean)

data class IpInfo(val local: Inet4Address?, val gateway: Inet4Address?)

class ConfigChanged /*(val positionX: Float, val positionY: Float, val rotation: Float)*/
