package com.local.paralleleyeconverter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
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
import android.util.DisplayMetrics
import android.view.WindowManager
import java.nio.ByteBuffer
import kotlin.math.max

class ConverterProjectionService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var reusableBitmap: Bitmap? = null
    private var projectionCallback: MediaProjection.Callback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: return START_NOT_STICKY
        val resultData = getResultData(intent) ?: return START_NOT_STICKY
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        if (projection != null) {
            return START_STICKY
        }
        projection = manager.getMediaProjection(resultCode, resultData)
        startCapture()
        return START_STICKY
    }

    override fun onDestroy() {
        virtualDisplay?.release()
        imageReader?.close()
        projectionCallback?.let { callback ->
            runCatching { projection?.unregisterCallback(callback) }
        }
        projection?.stop()
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

    private fun startCapture() {
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

        val scale = 2
        val width = max(320, metrics.widthPixels / scale)
        val height = max(320, metrics.heightPixels / scale)
        val thread = HandlerThread("converter-capture").also { it.start() }
        handlerThread = thread
        val handler = Handler(thread.looper)
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ source ->
            source.acquireLatestImage()?.use { image ->
                publishImage(image)
            }
        }, handler)
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                virtualDisplay?.release()
                virtualDisplay = null
                imageReader?.close()
                imageReader = null
                FrameBus.clear()
                stopSelf()
            }
        }
        projectionCallback = callback
        projection?.registerCallback(callback, handler)
        virtualDisplay = projection?.createVirtualDisplay(
            "ParallelEyeConverter",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler,
        )
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
        val cropped = Bitmap.createBitmap(source, 0, 0, width, height)
        FrameBus.publish(cropped)
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
