package com.dapa.camloc.events

import java.nio.ByteBuffer

class XInfo(x: Float) {
    val bytes: ByteArray

    init {
        bytes = ByteBuffer.allocate(4).putFloat(x).array()
    }
}