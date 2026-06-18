package com.local.paralleleyeconverter

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

class ConverterOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var currentView: View? = null
    private var showingPlayer = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showPlayer()
    }

    override fun onDestroy() {
        removeCurrentView()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (showingPlayer) {
            currentView?.let { view ->
                val (screenWidth, screenHeight) = realDisplaySize()
                val params = overlayParams(screenWidth, screenHeight).apply {
                    gravity = Gravity.CENTER
                    x = 0
                    y = 0
                }
                runCatching { windowManager.updateViewLayout(view, params) }
                view.requestLayout()
                view.invalidate()
            }
        }
    }

    private fun showFloatingBall() {
        ForegroundAppHelper.saveTargetPackage(this, ForegroundAppHelper.readForegroundPackage(this))
        removeCurrentView()
        showingPlayer = false
        val size = dp(62)
        val ball = FloatingBallView(this).apply {
            setOnOpenRequested { openTargetThenShowPlayer() }
        }
        val params = overlayParams(size, size).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(18)
            y = dp(140)
        }
        currentView = ball
        windowManager.addView(ball, params)
    }

    private fun showPlayer() {
        removeCurrentView()
        showingPlayer = true
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        root.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        lateinit var hideControlsRunnable: Runnable
        val controls = createControls(
            onMinimize = { showFloatingBall() },
            onMaximize = {
                refreshPlayerLayout(root)
                (root.getChildAt(0) as? ConverterSbsView)?.resetZoom()
            },
            onClose = {
                stopService(Intent(this@ConverterOverlayService, ConverterProjectionService::class.java).setAction(ConverterProjectionService.ACTION_STOP))
                stopSelf()
            },
        )
        fun showControlsTemporarily() {
            controls.visibility = View.VISIBLE
            mainHandler.removeCallbacks(hideControlsRunnable)
            mainHandler.postDelayed(hideControlsRunnable, 3000L)
        }
        hideControlsRunnable = Runnable { controls.visibility = View.GONE }
        val sbsView = ConverterSbsView(this).apply {
            setOnTap { showControlsTemporarily() }
        }
        root.addView(sbsView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        root.addView(controls, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(58),
            Gravity.CENTER,
        ))
        val (screenWidth, screenHeight) = realDisplaySize()
        val params = overlayParams(screenWidth, screenHeight).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
        }
        currentView = root
        windowManager.addView(root, params)
        showControlsTemporarily()
    }

    private fun openTargetThenShowPlayer() {
        val targetPackage = ForegroundAppHelper.readTargetPackage(this)
        val currentPackage = ForegroundAppHelper.readForegroundPackage(this)
        if (!targetPackage.isNullOrBlank() && currentPackage != null && currentPackage != targetPackage) {
            val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(launchIntent)
                mainHandler.postDelayed({ showPlayer() }, 450L)
                return
            }
        }
        showPlayer()
    }

    private fun refreshPlayerLayout(view: View) {
        val (screenWidth, screenHeight) = realDisplaySize()
        val params = overlayParams(screenWidth, screenHeight).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
        }
        runCatching { windowManager.updateViewLayout(view, params) }
        view.requestLayout()
        view.invalidate()
    }

    private fun createControls(onMinimize: () -> Unit, onMaximize: () -> Unit, onClose: () -> Unit): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(Color.argb(190, 20, 24, 31), dp(29))
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        val minus = controlButton("\u2212", Color.argb(235, 255, 255, 255), Color.rgb(28, 35, 44)).apply {
            setOnClickListener { onMinimize() }
        }
        val max = controlButton("\u25a1", Color.argb(235, 255, 255, 255), Color.rgb(28, 35, 44)).apply {
            setOnClickListener { onMaximize() }
        }
        val close = controlButton("\u00d7", Color.rgb(0, 210, 130), Color.WHITE).apply {
            setOnClickListener { onClose() }
        }
        val buttonParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply {
            marginStart = dp(4)
            marginEnd = dp(4)
        }
        panel.addView(minus, buttonParams)
        panel.addView(max, LinearLayout.LayoutParams(dp(46), dp(46)).apply {
            marginStart = dp(4)
            marginEnd = dp(4)
        })
        panel.addView(close, LinearLayout.LayoutParams(dp(46), dp(46)).apply {
            marginStart = dp(4)
            marginEnd = dp(4)
        })
        panel.layoutParams = ViewGroup.LayoutParams(dp(170), dp(58))
        return panel
    }

    private fun controlButton(textValue: String, bg: Int, fg: Int): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(fg)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(bg, dp(23))
        }
    }

    private fun removeCurrentView() {
        currentView?.let { view -> runCatching { windowManager.removeView(view) } }
        currentView = null
    }

    private fun overlayParams(width: Int, height: Int): LayoutParams {
        return LayoutParams(
            width,
            height,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            LayoutParams.FLAG_NOT_FOCUSABLE or
                LayoutParams.FLAG_NOT_TOUCH_MODAL or
                LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT,
        )
    }

    private fun realDisplaySize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

private class FloatingBallView(context: Context) : View(context) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(235, 62, 66) }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 25f * context.resources.displayMetrics.scaledDensity
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private var onOpenRequested: (() -> Unit)? = null
    private var lastTapTime = 0L
    private var downX = 0f
    private var downY = 0f

    fun setOnOpenRequested(listener: () -> Unit) {
        onOpenRequested = listener
    }

    override fun onDraw(canvas: Canvas) {
        val radius = min(width, height) / 2f - 3f
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, radius, fillPaint)
        canvas.drawCircle(cx, cy, radius - 5f, ringPaint)
        val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("\u8f6c", cx, baseline, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (abs(event.rawX - downX) < 12f && abs(event.rawY - downY) < 12f) {
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < 360L) {
                        lastTapTime = 0L
                        onOpenRequested?.invoke()
                    } else {
                        lastTapTime = now
                    }
                }
                return true
            }
        }
        return true
    }
}

private class ConverterSbsView(context: Context) : View(context) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 3f * context.resources.displayMetrics.density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f * context.resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private var zoom = 1f
    private var pinchStartDistance = 0f
    private var pinchStartZoom = 1f
    private var downX = 0f
    private var downY = 0f
    private var pinching = false
    private var onTap: (() -> Unit)? = null

    fun setOnTap(listener: () -> Unit) {
        onTap = listener
    }

    fun resetZoom() {
        zoom = 1f
        pinchStartDistance = 0f
        pinchStartZoom = 1f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        val frame = FrameBus.latestFrame
        if (frame == null) {
            canvas.drawText("\u7b49\u5f85\u5f55\u5c4f\u753b\u9762...", width / 2f, height / 2f, textPaint)
        } else {
            val eyeWidth = width / 2f
            drawEye(canvas, frame, 0f, eyeWidth)
            drawEye(canvas, frame, eyeWidth, eyeWidth)
        }
        val centerX = width / 2f
        canvas.drawLine(centerX, 0f, centerX, height.toFloat(), dividerPaint)
        postInvalidateOnAnimation()
    }

    private fun drawEye(canvas: Canvas, frame: android.graphics.Bitmap, left: Float, eyeWidth: Float) {
        val scale = eyeWidth / frame.width * zoom
        val drawWidth = frame.width * scale
        val drawHeight = frame.height * scale
        val dx = left + (eyeWidth - drawWidth) / 2f
        val dy = (height - drawHeight) / 2f
        val dst = RectF(dx, dy, dx + drawWidth, dy + drawHeight)
        canvas.save()
        canvas.clipRect(left, 0f, left + eyeWidth, height.toFloat())
        canvas.drawBitmap(frame, null, dst, paint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                pinching = false
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    pinching = true
                    pinchStartDistance = pointerDistance(event)
                    pinchStartZoom = zoom
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2 && pinchStartDistance > 8f) {
                    val distance = pointerDistance(event)
                    zoom = (pinchStartZoom * distance / pinchStartDistance).coerceIn(0.6f, 4f)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                pinchStartDistance = 0f
                pinchStartZoom = zoom
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!pinching && abs(event.x - downX) < 24f && abs(event.y - downY) < 24f) {
                    onTap?.invoke()
                }
                pinching = false
                return true
            }
        }
        return true
    }

    private fun pointerDistance(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return sqrt(dx * dx + dy * dy)
    }
}
