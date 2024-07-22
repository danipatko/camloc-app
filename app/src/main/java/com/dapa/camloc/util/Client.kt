package com.dapa.camloc.util

import android.util.Log
import com.dapa.camloc.activities.TrackerActivity
import java.nio.ByteBuffer

class ClientConfig(var state: Byte, var xPosition: Float, var yPosition: Float, var rotation: Float, var fov: Float, var cameraIndex: Byte, var resolution: Byte, var focus: Float, var battery: Byte) {
    fun toBytes(): ByteArray {
        return ByteBuffer.allocate(Byte.SIZE_BYTES * 4 + Float.SIZE_BYTES * 5).apply {
            put(state)
            putFloat(xPosition)
            putFloat(yPosition)
            putFloat(rotation)
            putFloat(fov)
            put(cameraIndex)
            put(resolution)
            putFloat(focus)
            put(battery)
        }.array()
    }

    fun setConfig(buf: ByteBuffer) {
        this.setConfig(buf.float, buf.float, buf.float)
    }

    fun setConfig(xPosition: Float, yPosition: Float, rotation: Float) {
        this.xPosition = xPosition
        this.yPosition = yPosition
        this.rotation = rotation
    }

    fun setCamera(buf: ByteBuffer) {
        this.setCamera(buf.get(), buf.get(), buf.float)
    }

    fun setCamera(index: Byte, resolution: Byte, focus: Float) {
        this.cameraIndex = index.coerceIn(0, 2)
        this.resolution = resolution.coerceIn(0, 5)
        this.focus = focus.coerceIn(0.1F, 5F)
    }

    override fun toString(): String {
        return "state: $state\nx: $xPosition, y: $yPosition, rotation: $rotation, fov: $fov\ncam: ${TrackerActivity.cameraSelectorDisplay[cameraIndex.toInt()]}, res: ${TrackerActivity.resolutionsDisplay[resolution.toInt()]}, focus: $focus\nbat: $battery"
    }

    companion object {
        fun fromBytes(buffer: ByteArray): ClientConfig {
            return fromBytes(ByteBuffer.wrap(buffer))
        }

        fun fromBytes(buffer: ByteBuffer): ClientConfig {
            return buffer.let {
                ClientConfig(it.get(), it.float, it.float, it.float, it.float, it.get(), it.get(), it.float, it.get())
            }
        }

        val DEFAULT get() = ClientConfig(0, -3F, 3F, 90F, 70F, 0, 3, 1F, 0)
    }
}
