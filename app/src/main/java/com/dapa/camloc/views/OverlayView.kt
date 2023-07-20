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
    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, android.R.color.holo_red_light)
        strokeWidth = 10f
    }
    private var centerX: Float = Float.NaN
    private var flipX: Boolean = false

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if(canvas == null) return
        val x = if(flipX) width - (centerX * width) else centerX * width
        canvas.drawLine(x, 0f, x, height.toFloat(), paint);
    }

    fun drawX(x: Float, flip: Boolean) {
        centerX = x
        flipX = flip
        postInvalidate()
    }

    companion object {
        const val TAG = "CamLocOverlayView"
    }
}