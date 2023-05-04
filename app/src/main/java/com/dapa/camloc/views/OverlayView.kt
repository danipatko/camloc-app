package com.dapa.camloc.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.View
import androidx.core.content.ContextCompat
import org.opencv.core.Point
import kotlin.math.abs

class OverlayView(context: Context, attrs: AttributeSet): View(context, attrs) {
    private val markers: MutableList<Point> = mutableListOf()
    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, android.R.color.holo_red_light)
        strokeWidth = 10f
    }
    private var originalSize: Size? = null

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if(canvas == null || originalSize == null) return

        markers.forEach {
            val p = transformPoint(it, originalSize!!, width, height)
            canvas.drawPoint(p.x.toFloat(), p.y.toFloat(), paint);
        }
    }

    fun draw(m: Array<Point>, size: Size) {
        originalSize = size
        markers.clear()
        markers.addAll(m)
        postInvalidate()
    }

    private fun transformPoint(p: Point, ogSize: Size, dx: Int, dy: Int): Point {
        return Point(p.x * (dx / ogSize.height.toFloat()), p.y * (dy / ogSize.width.toFloat()))
    }

    companion object {
        val TAG = "OverlayView"
    }
}