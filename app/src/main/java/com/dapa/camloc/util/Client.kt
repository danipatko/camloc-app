package com.dapa.camloc.util

import android.util.Log
import java.nio.ByteBuffer

class ClientConfig(var state: Byte, var xPosition: Float, var yPosition: Float, var rotation: Float, var fov: Float, var cameraIndex: Byte, var xResolution: Int, var yResolution: Int, var focus: Float, var battery: Byte) {
    fun toBytes(): ByteArray {
        return ByteBuffer.allocate(Byte.SIZE_BYTES * 2 + Int.SIZE_BYTES * 2 + Float.SIZE_BYTES * 5).apply {
            put(state)
            putFloat(xPosition)
            putFloat(yPosition)
            putFloat(rotation)
            putFloat(fov)
            put(cameraIndex)
            putInt(xResolution)
            putInt(yResolution)
            putFloat(focus)
            put(battery)
        }.array()
    }

    override fun toString(): String {
        return "state: $state\nx: $xPosition, y: $yPosition, rotation: $rotation, fov: $fov\ncam: $cameraIndex, resX: $xResolution, resY: $yResolution, focus: $focus\nbat: $battery"
    }

    companion object {
        fun fromBytes(buffer: ByteArray): ClientConfig {
            return fromBytes(ByteBuffer.wrap(buffer))
        }

        fun fromBytes(buffer: ByteBuffer): ClientConfig {
            return buffer.let {
                ClientConfig(it.get(), it.float, it.float, it.float, it.float, it.get(), it.int, it.int, it.float, it.get())
            }
        }

        val DEFAULT get() = ClientConfig(0, -3F, 3F, 90F, 70F, 0, 1280, 720, 1F, 0)
    }
}
