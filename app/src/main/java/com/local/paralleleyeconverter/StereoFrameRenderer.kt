package com.local.paralleleyeconverter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import kotlin.math.roundToInt

data class StereoEyes(val left: Bitmap, val right: Bitmap)

class StereoFrameRenderer {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var scaledFrame: Bitmap? = null
    private var eyes: StereoEyes? = null
    private var sourcePixels = IntArray(0)
    private var leftPixels = IntArray(0)
    private var rightPixels = IntArray(0)
    private var lastFrameVersion = -1L
    private var lastDepth: DepthFrame? = null
    private var lastDisparity = 0f
    private var lastRenderedAt = 0L

    fun render(
        frame: Bitmap,
        frameVersion: Long,
        depth: DepthFrame?,
        disparity: Float,
    ): StereoEyes? {
        if (depth == null) {
            clear()
            return null
        }
        val scale = (MAX_WARP_EDGE.toFloat() / maxOf(frame.width, frame.height)).coerceAtMost(1f)
        val width = (frame.width * scale).roundToInt().coerceAtLeast(1)
        val height = (frame.height * scale).roundToInt().coerceAtLeast(1)
        val now = SystemClock.elapsedRealtime()
        val cached = eyes
        val sameLayout = cached != null && cached.left.width == width && cached.left.height == height &&
            lastDisparity == disparity && lastDepth?.model == depth.model
        if (sameLayout && ((lastFrameVersion == frameVersion && lastDepth === depth) ||
                now - lastRenderedAt < MIN_WARP_INTERVAL_MS)
        ) {
            return cached
        }

        val count = width * height
        if (sourcePixels.size != count) {
            sourcePixels = IntArray(count)
            leftPixels = IntArray(count)
            rightPixels = IntArray(count)
        }
        val scaled = scaledFrame?.takeIf { it.width == width && it.height == height }
            ?: createBitmap(width, height).also { scaledFrame = it }
        scaled.eraseColor(0)
        Canvas(scaled).drawBitmap(frame, null, Rect(0, 0, width, height), paint)
        scaled.getPixels(sourcePixels, 0, width, 0, 0, width, height)

        val maxX = width - 1
        val shiftScale = disparity.coerceIn(0.2f, 4f) * 0.01f * maxX
        for (y in 0 until height) {
            val depthRow = y * depth.height / height * depth.width
            val row = y * width
            for (x in 0 until width) {
                val d = depth.values[depthRow + x * depth.width / width]
                val shift = (d - 0.5f) * shiftScale
                val leftX = (x - shift).roundToInt().coerceIn(0, maxX)
                val rightX = (x + shift).roundToInt().coerceIn(0, maxX)
                leftPixels[row + x] = sourcePixels[row + leftX]
                rightPixels[row + x] = sourcePixels[row + rightX]
            }
        }

        val pair = cached?.takeIf { it.left.width == width && it.left.height == height }
            ?: StereoEyes(createBitmap(width, height), createBitmap(width, height)).also { eyes = it }
        pair.left.setPixels(leftPixels, 0, width, 0, 0, width, height)
        pair.right.setPixels(rightPixels, 0, width, 0, 0, width, height)
        lastFrameVersion = frameVersion
        lastDepth = depth
        lastDisparity = disparity
        lastRenderedAt = now
        return pair
    }

    fun clear() {
        if (eyes == null && scaledFrame == null) return
        eyes = null
        scaledFrame = null
        lastFrameVersion = -1L
        lastDepth = null
        sourcePixels = IntArray(0)
        leftPixels = IntArray(0)
        rightPixels = IntArray(0)
    }

    private fun createBitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    private companion object {
        const val MAX_WARP_EDGE = 720
        const val MIN_WARP_INTERVAL_MS = 33L
    }
}
