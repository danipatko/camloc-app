package com.dapa.camloc.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.View
import androidx.core.content.ContextCompat

class OverlayView(context: Context, attrs: AttributeSet): View(context, attrs) {
    // private val markers: MutableList<Marker> = mutableListOf()
    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, android.R.color.holo_red_light)
        strokeWidth = 10f
    }
    private var centerX: Float = Float.NaN

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if(canvas == null) return
        canvas.drawLine(centerX * width, 0f, centerX * width, height.toFloat(), paint);
    }

    fun drawX(x: Float) {
        centerX = x
        postInvalidate()
    }

    companion object {
        const val TAG = "CamLocOverlayView"
    }
}