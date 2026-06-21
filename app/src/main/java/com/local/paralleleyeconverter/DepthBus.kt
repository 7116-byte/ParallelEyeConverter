package com.local.paralleleyeconverter

import android.os.SystemClock

data class DepthFrame(
    val values: FloatArray,
    val width: Int,
    val height: Int,
    val sourceFrameVersion: Long,
    val model: String,
    val reused: Boolean,
    val timestampMs: Long = SystemClock.elapsedRealtime(),
)

object DepthBus {
    @Volatile
    var latestDepth: DepthFrame? = null
        private set

    @Volatile
    var depthVersion: Long = 0
        private set

    @Volatile
    var lastStatus: String = "3D off"

    @Volatile
    var lastInferenceMs: Long = 0
        private set

    fun publish(frame: DepthFrame, inferenceMs: Long) {
        latestDepth = frame
        lastInferenceMs = inferenceMs
        lastStatus = "${frame.model}, ${frame.width}x${frame.height}, ${inferenceMs}ms"
        depthVersion++
    }

    fun markReuse(model: String) {
        lastStatus = "$model, reuse depth"
    }

    fun markUnavailable(reason: String) {
        lastStatus = reason
    }

    fun clear() {
        latestDepth = null
        lastInferenceMs = 0
        lastStatus = "3D off"
        depthVersion++
    }
}
