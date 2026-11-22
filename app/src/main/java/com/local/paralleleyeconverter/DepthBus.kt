package com.local.paralleleyeconverter

data class DepthFrame(
    val values: FloatArray,
    val width: Int,
    val height: Int,
    val sourceFrameVersion: Long,
    val model: String,
)

object DepthBus {
    @Volatile
    var latestDepth: DepthFrame? = null
        private set

    @Volatile
    var lastStatus: String = "3D off"

    fun publish(frame: DepthFrame, inferenceMs: Long) {
        latestDepth = frame
        lastStatus = "${frame.model}, ${frame.width}x${frame.height}, ${inferenceMs}ms"
    }

    fun clear() {
        latestDepth = null
        lastStatus = "3D off"
    }
}
