package com.local.paralleleyeconverter

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import kotlin.math.min

class ConverterOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlay: ConverterSbsView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = ConverterSbsView(this).also { overlay = it }
        val params = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
        }
        windowManager.addView(view, params)
    }

    override fun onDestroy() {
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
        super.onDestroy()
    }
}

private class ConverterSbsView(context: Context) : View(context) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        val frame = FrameBus.latestFrame
        if (frame == null) {
            canvas.drawText("\u7b49\u5f85\u5f55\u5c4f\u753b\u9762...", 48f, 96f, textPaint)
        } else {
            val eyeWidth = width / 2f
            drawEye(canvas, frame, 0f, eyeWidth)
            drawEye(canvas, frame, eyeWidth, eyeWidth)
        }
        postInvalidateOnAnimation()
    }

    private fun drawEye(canvas: Canvas, frame: android.graphics.Bitmap, left: Float, eyeWidth: Float) {
        val scale = min(eyeWidth / frame.width, height.toFloat() / frame.height)
        val drawWidth = frame.width * scale
        val drawHeight = frame.height * scale
        val dx = left + (eyeWidth - drawWidth) / 2f
        val dy = (height - drawHeight) / 2f
        val dst = RectF(dx, dy, dx + drawWidth, dy + drawHeight)
        canvas.drawBitmap(frame, null, dst, paint)
    }
}
