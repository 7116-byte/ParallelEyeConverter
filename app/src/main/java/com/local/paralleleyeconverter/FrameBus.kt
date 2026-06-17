package com.local.paralleleyeconverter

import android.graphics.Bitmap

object FrameBus {
    @Volatile
    var latestFrame: Bitmap? = null

    @Volatile
    var frameVersion: Long = 0

    @Volatile
    var lastFrameTimeMs: Long = 0

    fun publish(bitmap: Bitmap) {
        latestFrame = bitmap
        frameVersion++
        lastFrameTimeMs = android.os.SystemClock.elapsedRealtime()
    }

    fun clear() {
        latestFrame = null
        frameVersion++
        lastFrameTimeMs = 0
    }
}
