package com.dapa.camloc.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.View
import androidx.core.content.ContextCompat
import com.dapa.camloc.Marker
import org.opencv.core.Point

class OverlayView(context: Context, attrs: AttributeSet): View(context, attrs) {
    private val markers: MutableList<Marker> = mutableListOf()
    private val paint = Paint().apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, android.R.color.holo_red_light)
        textSize = 40f
    }
    private var originalSize: Size? = null

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if(canvas == null || originalSize == null) return
        markers.forEach {
            val (x, y) = transformPoint(it.point, width, height)
            canvas.drawText(it.id.toString(), x, y, paint)
        }
    }

    fun draw(m: Array<Marker>, size: Size) {
        originalSize = size
        markers.clear()
        if(m.isNotEmpty()) markers.addAll(m)
        postInvalidate()
    }

    data class CanvasPoint(val x: Float, val y: Float)
    private fun transformPoint(p: Point, dx: Int, dy: Int): CanvasPoint {
        return CanvasPoint(p.x.toFloat() * (dx / originalSize!!.width.toFloat()),  p.y.toFloat() * (dy / originalSize!!.height.toFloat()))
    }

    companion object {
        const val TAG = "OverlayView"
    }
}