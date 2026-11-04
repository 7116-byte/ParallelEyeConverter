package com.local.paralleleyeconverter

/** Horizontal picture placement, independent of depth disparity and image scaling. */
object StereoLayout {
    const val MIN_CENTER_DISTANCE_RATIO = 0.5f
    const val MAX_CENTER_DISTANCE_RATIO = 2f / 3f

    fun clampCenterDistanceRatio(ratio: Float): Float =
        if (ratio.isFinite()) {
            ratio.coerceIn(MIN_CENTER_DISTANCE_RATIO, MAX_CENTER_DISTANCE_RATIO)
        } else {
            MIN_CENTER_DISTANCE_RATIO
        }

    fun eyeCenterX(viewWidth: Float, centerDistanceRatio: Float, rightEye: Boolean): Float {
        val halfDistance = viewWidth * clampCenterDistanceRatio(centerDistanceRatio) / 2f
        return viewWidth / 2f + if (rightEye) halfDistance else -halfDistance
    }
}
