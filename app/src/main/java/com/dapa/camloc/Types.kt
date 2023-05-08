package com.dapa.camloc

import org.opencv.core.Point

class Marker(mid: Int, x: Double, y: Double) {
    var point: Point
    var id: Int

    init {
        point = Point(x, y)
        id = mid
    }

    override fun toString(): String {
        return "$id | $point"
    }
}

data class RotationVector(val x: Float, val y: Float, val z: Float);
