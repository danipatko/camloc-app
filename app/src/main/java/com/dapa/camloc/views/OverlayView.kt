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
            val p = transformPoint(it.point, originalSize!!, width, height)
            // canvas.drawPoint(p.x.toFloat(), p.y.toFloat(), paint)
            canvas.drawText(it.id.toString(), p.x.toFloat(), p.y.toFloat(), paint)
        }
    }

    fun draw(m: Array<Marker>, size: Size) {
        originalSize = size
        markers.clear()
        if(m.isNotEmpty()) markers.addAll(m)
        postInvalidate()
    }

    private fun transformPoint(p: Point, ogSize: Size, dx: Int, dy: Int): Point {
        return Point(p.x * (dx / ogSize.height.toFloat()), p.y * (dy / ogSize.width.toFloat()))
    }

    companion object {
        val TAG = "OverlayView"
    }
}