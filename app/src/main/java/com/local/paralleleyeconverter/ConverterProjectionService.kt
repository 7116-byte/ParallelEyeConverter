package com.local.paralleleyeconverter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.nio.ByteBuffer
import kotlin.math.max

class ConverterProjectionService : Service() {
    @Volatile
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var reusableBitmap: Bitmap? = null
    private var reusableOutputBitmap: Bitmap? = null
    private var reusableOutputCanvas: Canvas? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var currentCaptureWidth = 0
    private var currentCaptureHeight = 0
    private var currentDensityDpi = 0
    private var rebuilding = false
    private val captureLock = Any()
    private val recovery = CaptureRecovery()
    @Volatile
    private var captureStopped = true
    @Volatile
    private var capturedContentVisible = true
    private var capturedContentWidth = 0
    private var capturedContentHeight = 0
    private var forceRebuildPending = false
    private var displayCreationAttempted = false

    private val rebuildRunnable = Runnable {
        val force = forceRebuildPending
        forceRebuildPending = false
        runCatching { rebuildCaptureSurface(force) }
            .onFailure { Log.w(TAG, "Capture surface replacement failed", it) }
    }

    private val frameWatchdog = object : Runnable {
        override fun run() {
            val localHandler = handler ?: return
            if (captureStopped || projection == null) return
            if (!rebuilding && recovery.claimRecovery(SystemClock.elapsedRealtime())) {
                FrameBus.markRecovering()
                scheduleRebuild(0L, force = true)
            }
            localHandler.postDelayed(this, 1200L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = intent?.let { getResultData(it) }
        if (resultCode == 0 || resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        if (projection != null) {
            closeCaptureSession()
        }
        FrameBus.clear(CaptureState.WAITING)
        val thread = HandlerThread("converter-capture").also { it.start() }
        handlerThread = thread
        val localHandler = Handler(thread.looper)
        handler = localHandler

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val activeProjection = runCatching { checkNotNull(manager.getMediaProjection(resultCode, resultData)) }.getOrElse {
            Log.w(TAG, "Capture authorization could not start a session", it)
            FrameBus.clear()
            stopSelf()
            return START_NOT_STICKY
        }
        projection = activeProjection
        captureStopped = false
        capturedContentVisible = true
        capturedContentWidth = 0
        capturedContentHeight = 0
        displayCreationAttempted = false
        recovery.start(SystemClock.elapsedRealtime())
        registerProjectionCallback()
        registerDisplayListener()
        localHandler.post {
            if (projection !== activeProjection || captureStopped) return@post
            runCatching { rebuildCaptureSurface() }.onFailure {
                Log.w(TAG, "Initial capture surface failed", it)
                FrameBus.clear()
                stopSelf()
                return@post
            }
            localHandler.postDelayed(frameWatchdog, 1200L)
        }
        // A projection cannot be restarted with a null intent or reused consent.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        closeCaptureSession()
        super.onDestroy()
    }

    private fun closeCaptureSession() {
        captureStopped = true
        capturedContentVisible = false
        recovery.stop()
        forceRebuildPending = false
        handler?.removeCallbacksAndMessages(null)
        displayListener?.let { listener ->
            runCatching { getSystemService(DisplayManager::class.java).unregisterDisplayListener(listener) }
        }
        displayListener = null
        projectionCallback?.let { callback ->
            runCatching { projection?.unregisterCallback(callback) }
        }
        projectionCallback = null
        releaseCaptureSurface()
        val oldProjection = projection
        projection = null
        oldProjection?.stop()
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
        capturedContentWidth = 0
        capturedContentHeight = 0
        DepthProcessor.reset()
        synchronized(captureLock) { FrameBus.clear() }
    }

    private fun getResultData(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
    }

    private fun registerProjectionCallback() {
        val activeProjection = projection ?: return
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                if (projection !== activeProjection) return
                captureStopped = true
                capturedContentVisible = false
                recovery.stop()
                synchronized(captureLock) { FrameBus.clear() }
                Log.i(TAG, "Projection stopped; fresh user authorization is required")
                stopSelf()
            }

            override fun onCapturedContentResize(width: Int, height: Int) {
                if (projection !== activeProjection || captureStopped || width <= 0 || height <= 0) return
                capturedContentWidth = width
                capturedContentHeight = height
                Log.i(TAG, "Captured region resized to ${width}x$height")
                scheduleRebuild(0L)
            }

            override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
                if (projection !== activeProjection || captureStopped) return
                val wasVisible = capturedContentVisible
                capturedContentVisible = isVisible
                recovery.setVisible(isVisible, SystemClock.elapsedRealtime())
                if (!isVisible) {
                    FrameBus.clear(CaptureState.HIDDEN)
                    DepthBus.clear()
                } else if (!wasVisible) {
                    FrameBus.clear(CaptureState.WAITING)
                    scheduleRebuild(0L, force = true)
                }
                Log.i(TAG, "Captured region visible=$isVisible")
            }
        }
        projectionCallback = callback
        activeProjection.registerCallback(callback, handler)
    }

    private fun registerDisplayListener() {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                scheduleRebuild(700L)
            }
        }
        displayListener = listener
        getSystemService(DisplayManager::class.java).registerDisplayListener(listener, handler)
    }

    private fun scheduleRebuild(delayMs: Long, force: Boolean = false) {
        val localHandler = handler ?: return
        if (captureStopped || (forceRebuildPending && !force)) return
        forceRebuildPending = forceRebuildPending || force
        localHandler.removeCallbacks(rebuildRunnable)
        localHandler.postDelayed(rebuildRunnable, delayMs)
    }

    private fun rebuildCaptureSurface(force: Boolean = false): Unit = synchronized(captureLock) {
        val localHandler = handler ?: return
        val localProjection = projection ?: return
        if (rebuilding || captureStopped) return
        if (force) recovery.onRecoveryAttempt(SystemClock.elapsedRealtime())
        rebuilding = true
        try {
            val metrics = readDisplayMetrics()
            val scale = 2
            val width = max(320, (capturedContentWidth.takeIf { it > 0 } ?: metrics.widthPixels) / scale)
            val height = max(320, (capturedContentHeight.takeIf { it > 0 } ?: metrics.heightPixels) / scale)
            val dpi = metrics.densityDpi
            val sameSize = width == currentCaptureWidth && height == currentCaptureHeight && dpi == currentDensityDpi
            if (!CaptureRecovery.needsSurfaceReplacement(force, sameSize, virtualDisplay != null, imageReader != null)) {
                return
            }
            if (virtualDisplay == null && displayCreationAttempted) {
                // Android 14+ permits only one createVirtualDisplay call per consent.
                captureStopped = true
                recovery.stop()
                FrameBus.clear()
                stopSelf()
                return
            }
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            reader.setOnImageAvailableListener({ source ->
                if (projection !== localProjection || captureStopped || source !== imageReader) return@setOnImageAvailableListener
                runCatching {
                    val frame = synchronized(captureLock) {
                        if (projection !== localProjection || captureStopped || source !== imageReader) return@synchronized null
                        source.acquireLatestImage()?.use { image ->
                            if (capturedContentVisible) publishImage(image, localProjection) else null
                        }
                    }
                    if (frame != null && projection === localProjection && !captureStopped) {
                        // Release the Image and resource lock before potentially slow inference.
                        DepthProcessor.processFrame(this, frame, FrameBus.frameVersion)
                    }
                }.onFailure {
                    if (projection === localProjection && !captureStopped) {
                        Log.w(TAG, "Capture frame could not be read; watchdog will retry", it)
                    }
                }
            }, localHandler)
            val oldReader = imageReader
            val display = virtualDisplay
            try {
                if (display == null) {
                    displayCreationAttempted = true
                    virtualDisplay = checkNotNull(localProjection.createVirtualDisplay(
                        "ParallelEyeConverter",
                        width,
                        height,
                        dpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        reader.surface,
                        null,
                        localHandler,
                    ))
                } else {
                    // Rebind the SAME display, even at the same size, after a stalled stream.
                    if (force) display.surface = null
                    display.resize(width, height, dpi)
                    display.surface = reader.surface
                }
            } catch (error: RuntimeException) {
                if (display != null) {
                    runCatching {
                        display.resize(currentCaptureWidth, currentCaptureHeight, currentDensityDpi)
                        display.surface = oldReader?.surface
                    }.onFailure { Log.w(TAG, "Previous capture surface could not be restored", it) }
                }
                reader.setOnImageAvailableListener(null, null)
                reader.close()
                throw error
            }
            imageReader = reader
            reusableBitmap = null
            reusableOutputBitmap = null
            reusableOutputCanvas = null
            oldReader?.setOnImageAvailableListener(null, null)
            oldReader?.close()
            currentCaptureWidth = width
            currentCaptureHeight = height
            currentDensityDpi = dpi
            Log.i(TAG, "Capture surface ready ${width}x$height force=$force")
        } finally {
            rebuilding = false
        }
    }

    private fun readDisplayMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(WindowManager::class.java)
        val display = @Suppress("DEPRECATION") windowManager.defaultDisplay
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < 30) {
            display.getRealMetrics(metrics)
        } else {
            val bounds = windowManager.currentWindowMetrics.bounds
            metrics.widthPixels = bounds.width()
            metrics.heightPixels = bounds.height()
            metrics.densityDpi = resources.displayMetrics.densityDpi
        }
        return metrics
    }

    private fun releaseCaptureSurface(): Unit = synchronized(captureLock) {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        reusableBitmap = null
        reusableOutputBitmap = null
        reusableOutputCanvas = null
        currentCaptureWidth = 0
        currentCaptureHeight = 0
        currentDensityDpi = 0
    }

    private fun publishImage(image: Image, activeProjection: MediaProjection): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val rowPadding = rowStride - pixelStride * width
        val bitmapWidth = width + rowPadding / pixelStride
        val buffer: ByteBuffer = plane.buffer
        val source = reusableBitmap?.takeIf { it.width == bitmapWidth && it.height == height }
            ?: Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888).also { reusableBitmap = it }
        buffer.rewind()
        source.copyPixelsFromBuffer(buffer)
        val output = reusableOutputBitmap?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                reusableOutputBitmap = it
                reusableOutputCanvas = Canvas(it)
            }
        val canvas = reusableOutputCanvas ?: Canvas(output).also { reusableOutputCanvas = it }
        canvas.drawBitmap(source, Rect(0, 0, width, height), Rect(0, 0, width, height), null)
        if (projection !== activeProjection || captureStopped || !capturedContentVisible) return null
        FrameBus.publish(output)
        recovery.onFrame(SystemClock.elapsedRealtime())
        return output
    }

    private fun startAsForeground() {
        val channelId = "eye_converter"
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "\u5b9e\u65f6\u5e73\u884c\u773c\u8f6c\u5316",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("\u5b9e\u65f6\u5e73\u884c\u773c\u8f6c\u5316\u8fd0\u884c\u4e2d")
            .setContentText("\u6b63\u5728\u6355\u83b7\u5c4f\u5e55\u5e76\u8f6c\u5316\u4e3a\u5de6\u53f3\u773c\u753b\u9762")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
    }

    companion object {
        private const val TAG = "ParallelEyeCapture"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val ACTION_STOP = "com.local.paralleleyeconverter.STOP"
    }
}
