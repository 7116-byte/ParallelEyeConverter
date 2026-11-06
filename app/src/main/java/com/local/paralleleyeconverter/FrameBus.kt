package com.local.paralleleyeconverter

import android.graphics.Bitmap

enum class CaptureState { WAITING, LIVE, HIDDEN, RECOVERING, STOPPED }

object FrameBus {
    @Volatile
    var captureState: CaptureState = CaptureState.STOPPED
        private set

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
        captureState = CaptureState.LIVE
    }

    fun markRecovering() {
        captureState = CaptureState.RECOVERING
    }

    fun clear(state: CaptureState = CaptureState.STOPPED) {
        latestFrame = null
        frameVersion++
        lastFrameTimeMs = 0
        captureState = state
    }
}
