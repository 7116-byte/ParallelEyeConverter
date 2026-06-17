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
import android.view.WindowManager
import java.nio.ByteBuffer
import kotlin.math.max

class ConverterProjectionService : Service() {
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

    private val rebuildRunnable = Runnable {
        rebuildCaptureSurface()
    }

    private val frameWatchdog = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val last = FrameBus.lastFrameTimeMs
            if (!rebuilding && projection != null && last > 0 && now - last > 2200L) {
                scheduleRebuild(0L)
            }
            handler?.postDelayed(this, 1200L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: return START_NOT_STICKY
        val resultData = getResultData(intent) ?: return START_NOT_STICKY
        if (projection != null) {
            return START_STICKY
        }
        val thread = HandlerThread("converter-capture").also { it.start() }
        handlerThread = thread
        handler = Handler(thread.looper)

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, resultData)
        registerProjectionCallback()
        registerDisplayListener()
        rebuildCaptureSurface()
        handler?.postDelayed(frameWatchdog, 1200L)
        return START_STICKY
    }

    override fun onDestroy() {
        handler?.removeCallbacksAndMessages(null)
        displayListener?.let { listener ->
            runCatching { getSystemService(DisplayManager::class.java).unregisterDisplayListener(listener) }
        }
        projectionCallback?.let { callback ->
            runCatching { projection?.unregisterCallback(callback) }
        }
        releaseCaptureSurface()
        projection?.stop()
        projection = null
        handlerThread?.quitSafely()
        FrameBus.clear()
        super.onDestroy()
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
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                releaseCaptureSurface()
                FrameBus.clear()
                stopSelf()
            }
        }
        projectionCallback = callback
        projection?.registerCallback(callback, handler)
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

    private fun scheduleRebuild(delayMs: Long) {
        val localHandler = handler ?: return
        localHandler.removeCallbacks(rebuildRunnable)
        localHandler.postDelayed(rebuildRunnable, delayMs)
    }

    private fun rebuildCaptureSurface() {
        val localProjection = projection ?: return
        val localHandler = handler ?: return
        if (rebuilding) return
        rebuilding = true
        try {
            val metrics = readDisplayMetrics()
            val scale = 2
            val width = max(320, metrics.widthPixels / scale)
            val height = max(320, metrics.heightPixels / scale)
            val dpi = metrics.densityDpi
            if (width == currentCaptureWidth && height == currentCaptureHeight && dpi == currentDensityDpi && virtualDisplay != null) {
                return
            }
            releaseCaptureSurface()
            FrameBus.clear()
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            imageReader = reader
            currentCaptureWidth = width
            currentCaptureHeight = height
            currentDensityDpi = dpi
            reader.setOnImageAvailableListener({ source ->
                source.acquireLatestImage()?.use { image ->
                    publishImage(image)
                }
            }, localHandler)
            virtualDisplay = localProjection.createVirtualDisplay(
                "ParallelEyeConverter",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                localHandler,
            )
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

    private fun releaseCaptureSurface() {
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

    private fun publishImage(image: Image) {
        val plane = image.planes.firstOrNull() ?: return
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
        FrameBus.publish(output)
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
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val ACTION_STOP = "com.local.paralleleyeconverter.STOP"
    }
}
