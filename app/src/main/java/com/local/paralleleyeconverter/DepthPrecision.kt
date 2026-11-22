package com.local.paralleleyeconverter

import kotlin.math.roundToInt

internal enum class DepthPrecision(val key: String, val labelRes: Int, val asset: String?) {
    ORIGINAL("original", R.string.depth_original, null),
    FP16("fp16", R.string.depth_fp16, "dav2-gpu-fp16.onnx"),
    INT8("int8", R.string.depth_int8, "dav2-gpu-int8.onnx"),
    ROTATED_INT8("rotated_int8", R.string.depth_rotated_int8, "dav2-gpu-rotated-int8.onnx"),
}

internal val DEPTH_LONG_EDGES = listOf(448, 644, 812)
private const val DEPTH_PATCH_SIZE = 14

internal data class DepthInputSize(val width: Int, val height: Int) {
    companion object {
        fun forAspectRatio(width: Int, height: Int, longEdge: Int): DepthInputSize {
            val shortEdge = (longEdge.toDouble() * minOf(width, height) / maxOf(width, height) / DEPTH_PATCH_SIZE)
                .roundToInt().coerceAtLeast(1) * DEPTH_PATCH_SIZE
            return if (width >= height) DepthInputSize(longEdge, shortEdge) else DepthInputSize(shortEdge, longEdge)
        }
    }
}
