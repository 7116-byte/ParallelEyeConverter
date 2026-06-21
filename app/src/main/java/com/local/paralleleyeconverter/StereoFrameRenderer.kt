package com.local.paralleleyeconverter

import android.graphics.Bitmap
import kotlin.math.roundToInt

class StereoFrameRenderer {
    private var leftBitmap: Bitmap? = null
    private var rightBitmap: Bitmap? = null
    private var sourcePixels: IntArray = IntArray(0)
    private var outputPixels: IntArray = IntArray(0)
    private var lastLeftKey: RenderKey? = null
    private var lastRightKey: RenderKey? = null

    fun renderEye(
        frame: Bitmap,
        frameVersion: Long,
        depth: DepthFrame?,
        depthVersion: Long,
        disparity: Float,
        rightEye: Boolean,
    ): Bitmap {
        if (depth == null || depth.values.isEmpty()) return frame
        val key = RenderKey(
            frameVersion = frameVersion,
            depthVersion = depthVersion,
            width = frame.width,
            height = frame.height,
            disparity = (disparity * 100f).roundToInt(),
            rightEye = rightEye,
        )
        if (rightEye && key == lastRightKey) return rightBitmap ?: frame
        if (!rightEye && key == lastLeftKey) return leftBitmap ?: frame

        val width = frame.width
        val height = frame.height
        val count = width * height
        if (sourcePixels.size != count) sourcePixels = IntArray(count)
        if (outputPixels.size != count) outputPixels = IntArray(count)
        frame.getPixels(sourcePixels, 0, width, 0, 0, width, height)

        val factor = if (rightEye) 1f else -1f
        val shiftSize = disparity.coerceIn(0.2f, 4.0f) * 0.01f
        val convergence = 0.5f
        val maxSourceX = width - 1
        val maxSourceY = height - 1
        for (y in 0 until height) {
            val depthY = (y * depth.height / height).coerceIn(0, depth.height - 1)
            val row = y * width
            for (x in 0 until width) {
                val depthX = (x * depth.width / width).coerceIn(0, depth.width - 1)
                val d = depth.values[depthY * depth.width + depthX]
                val indexShift = (d - convergence) * shiftSize
                val shiftedNorm = (x.toFloat() / maxSourceX.coerceAtLeast(1)) + indexShift * factor
                val sourceX = (shiftedNorm * maxSourceX).roundToInt().coerceIn(0, maxSourceX)
                outputPixels[row + x] = sourcePixels[y.coerceIn(0, maxSourceY) * width + sourceX]
            }
        }

        val target = if (rightEye) {
            rightBitmap?.takeIf { it.width == width && it.height == height }
                ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { rightBitmap = it }
        } else {
            leftBitmap?.takeIf { it.width == width && it.height == height }
                ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { leftBitmap = it }
        }
        target.setPixels(outputPixels, 0, width, 0, 0, width, height)
        if (rightEye) {
            lastRightKey = key
        } else {
            lastLeftKey = key
        }
        return target
    }

    fun clear() {
        leftBitmap = null
        rightBitmap = null
        lastLeftKey = null
        lastRightKey = null
        sourcePixels = IntArray(0)
        outputPixels = IntArray(0)
    }
}

private data class RenderKey(
    val frameVersion: Long,
    val depthVersion: Long,
    val width: Int,
    val height: Int,
    val disparity: Int,
    val rightEye: Boolean,
)
