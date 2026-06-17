package com.local.paralleleyeconverter

import android.graphics.Bitmap

object FrameBus {
    @Volatile
    var latestFrame: Bitmap? = null

    @Volatile
    var frameVersion: Long = 0

    fun publish(bitmap: Bitmap) {
        latestFrame = bitmap
        frameVersion++
    }

    fun clear() {
        latestFrame = null
        frameVersion++
    }
}
