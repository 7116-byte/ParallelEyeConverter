package com.local.paralleleyeconverter

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
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
import android.view.KeyEvent
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
    private var floatingBallX = -1
    private var floatingBallY = -1
    private var floatingBallHalfHidden = false
    private var floatingBallIdleRunnable: Runnable? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        registerDisplayListener()
        showPlayer()
    }

    override fun onDestroy() {
        unregisterDisplayListener()
        cancelFloatingBallIdle()
        removeCurrentView()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshCurrentOverlayLayout()
    }

    private fun registerDisplayListener() {
        val displayManager = getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit

            override fun onDisplayRemoved(displayId: Int) = Unit

            override fun onDisplayChanged(displayId: Int) {
                mainHandler.removeCallbacksAndMessages(DISPLAY_REFRESH_TOKEN)
                mainHandler.postAtTime(
                    { refreshCurrentOverlayLayout() },
                    DISPLAY_REFRESH_TOKEN,
                    android.os.SystemClock.uptimeMillis() + 250L,
                )
            }
        }
        displayListener = listener
        displayManager.registerDisplayListener(listener, mainHandler)
    }

    private fun unregisterDisplayListener() {
        val listener = displayListener ?: return
        runCatching { getSystemService(DisplayManager::class.java).unregisterDisplayListener(listener) }
        displayListener = null
    }

    private fun refreshCurrentOverlayLayout() {
        if (showingPlayer) {
            currentView?.let { view ->
                val params = overlayParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT,
                    opaque = true,
                    focusable = true,
                ).apply {
                    gravity = Gravity.CENTER
                    x = 0
                    y = 0
                }
                runCatching { windowManager.updateViewLayout(view, params) }
                view.requestLayout()
                view.invalidate()
            }
        } else {
            val size = dp(62)
            clampFloatingBall(size, allowHalfHidden = floatingBallHalfHidden)
            updateFloatingBallLayout(size)
        }
    }

    private fun showFloatingBall() {
        ForegroundAppHelper.saveTargetPackage(this, ForegroundAppHelper.readLastTargetCandidatePackage(this))
        removeCurrentView()
        showingPlayer = false
        val size = dp(62)
        if (floatingBallX < 0 || floatingBallY < 0) {
            val (screenWidth, screenHeight) = realDisplaySize()
            floatingBallX = (screenWidth - size - dp(18)).coerceAtLeast(0)
            floatingBallY = dp(140).coerceIn(0, (screenHeight - size).coerceAtLeast(0))
        }
        floatingBallHalfHidden = false
        clampFloatingBall(size, allowHalfHidden = false)
        val ball = FloatingBallView(this).apply {
            alpha = 1f
            setOnOpenRequested { openTargetThenShowPlayer() }
            setOnTouchStarted { revealFloatingBall(size) }
            setOnMoveRequested { dx, dy -> moveFloatingBall(size, dx, dy) }
            setOnTouchFinished { scheduleFloatingBallIdle(size) }
        }
        val params = overlayParams(size, size).apply {
            gravity = Gravity.TOP or Gravity.START
            x = floatingBallX
            y = floatingBallY
        }
        currentView = ball
        windowManager.addView(ball, params)
        scheduleFloatingBallIdle(size)
    }

    private fun showPlayer() {
        cancelFloatingBallIdle()
        removeCurrentView()
        showingPlayer = true
        val root = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        showFloatingBall()
                    }
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(Color.BLACK)
            systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
        lateinit var hideControlsRunnable: Runnable
        val controls = createControls(
            onMinimize = { showFloatingBall() },
            onMaximize = {
                openHomePage()
            },
            onClose = {
                stopService(Intent(this@ConverterOverlayService, ConverterProjectionService::class.java).setAction(ConverterProjectionService.ACTION_STOP))
                stopSelf()
            },
        )
        fun showControlsTemporarily() {
            controls.visibility = View.VISIBLE
            mainHandler.removeCallbacks(hideControlsRunnable)
            mainHandler.postDelayed(hideControlsRunnable, 2000L)
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
        val params = overlayParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
            opaque = true,
            focusable = true,
        ).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
        }
        currentView = root
        windowManager.addView(root, params)
        root.requestFocus()
        showControlsTemporarily()
    }

    private fun openTargetThenShowPlayer() {
        val targetPackage = ForegroundAppHelper.readTargetPackage(this)
            ?: ForegroundAppHelper.readLastTargetCandidatePackage(this)
        val currentPackage = ForegroundAppHelper.readForegroundPackage(this)
        if (!targetPackage.isNullOrBlank() && currentPackage != targetPackage) {
            val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(launchIntent)
                mainHandler.postDelayed({ showPlayer() }, 700L)
                return
            }
        }
        showPlayer()
    }

    private fun openHomePage() {
        showFloatingBall()
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        })
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

    private fun moveFloatingBall(size: Int, dx: Float, dy: Float) {
        floatingBallHalfHidden = false
        floatingBallX += dx.toInt()
        floatingBallY += dy.toInt()
        clampFloatingBall(size, allowHalfHidden = false)
        updateFloatingBallLayout(size)
    }

    private fun revealFloatingBall(size: Int) {
        cancelFloatingBallIdle()
        floatingBallHalfHidden = false
        currentView?.alpha = 1f
        clampFloatingBall(size, allowHalfHidden = false)
        updateFloatingBallLayout(size)
    }

    private fun scheduleFloatingBallIdle(size: Int) {
        cancelFloatingBallIdle()
        val runnable = Runnable {
            if (!showingPlayer && currentView != null) {
                hideFloatingBallPartially(size)
            }
        }
        floatingBallIdleRunnable = runnable
        mainHandler.postDelayed(runnable, 3000L)
    }

    private fun cancelFloatingBallIdle() {
        floatingBallIdleRunnable?.let { mainHandler.removeCallbacks(it) }
        floatingBallIdleRunnable = null
    }

    private fun hideFloatingBallPartially(size: Int) {
        val (screenWidth, screenHeight) = realDisplaySize()
        val maxY = (screenHeight - size).coerceAtLeast(0)
        floatingBallY = floatingBallY.coerceIn(0, maxY)
        floatingBallX = if (floatingBallX + size / 2 < screenWidth / 2) {
            -size / 2
        } else {
            screenWidth - size / 2
        }
        floatingBallHalfHidden = true
        currentView?.alpha = 0.7f
        updateFloatingBallLayout(size)
    }

    private fun clampFloatingBall(size: Int, allowHalfHidden: Boolean) {
        val (screenWidth, screenHeight) = realDisplaySize()
        val minX = if (allowHalfHidden) -size / 2 else 0
        val maxX = if (allowHalfHidden) {
            screenWidth - size / 2
        } else {
            (screenWidth - size).coerceAtLeast(0)
        }
        floatingBallX = floatingBallX.coerceIn(minX, maxX.coerceAtLeast(minX))
        floatingBallY = floatingBallY.coerceIn(0, (screenHeight - size).coerceAtLeast(0))
    }

    private fun updateFloatingBallLayout(size: Int) {
        val view = currentView ?: return
        val params = overlayParams(size, size).apply {
            gravity = Gravity.TOP or Gravity.START
            x = floatingBallX
            y = floatingBallY
        }
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun overlayParams(
        width: Int,
        height: Int,
        opaque: Boolean = false,
        focusable: Boolean = false,
    ): LayoutParams {
        var flags = LayoutParams.FLAG_NOT_TOUCH_MODAL or LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!focusable) {
            flags = flags or LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (!opaque) {
            flags = flags or LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
        return LayoutParams(
            width,
            height,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            if (opaque) PixelFormat.OPAQUE else PixelFormat.TRANSLUCENT,
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
    private var onTouchStarted: (() -> Unit)? = null
    private var onMoveRequested: ((Float, Float) -> Unit)? = null
    private var onTouchFinished: (() -> Unit)? = null
    private var lastTapTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var moved = false

    fun setOnOpenRequested(listener: () -> Unit) {
        onOpenRequested = listener
    }

    fun setOnTouchStarted(listener: () -> Unit) {
        onTouchStarted = listener
    }

    fun setOnMoveRequested(listener: (Float, Float) -> Unit) {
        onMoveRequested = listener
    }

    fun setOnTouchFinished(listener: () -> Unit) {
        onTouchFinished = listener
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
                onTouchStarted?.invoke()
                downX = event.rawX
                downY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                moved = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastRawX
                val dy = event.rawY - lastRawY
                if (abs(event.rawX - downX) >= 12f || abs(event.rawY - downY) >= 12f) {
                    moved = true
                }
                if (abs(dx) >= 1f || abs(dy) >= 1f) {
                    onMoveRequested?.invoke(dx, dy)
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!moved && abs(event.rawX - downX) < 12f && abs(event.rawY - downY) < 12f) {
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < 360L) {
                        lastTapTime = 0L
                        onOpenRequested?.invoke()
                    } else {
                        lastTapTime = now
                    }
                }
                onTouchFinished?.invoke()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                onTouchFinished?.invoke()
                return true
            }
        }
        return true
    }
}

private val DISPLAY_REFRESH_TOKEN = Any()

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
