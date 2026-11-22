package com.local.paralleleyeconverter

import android.content.Context

private const val SETTINGS_PREFS = "converter_settings"
private const val KEY_TRUE_3D = "true_3d_rendering"
private const val KEY_DEPTH_ANYTHING = "depth_anything_v2"
private const val KEY_DEPTH_DISPARITY = "depth_disparity"
private const val KEY_DEPTH_PERF_MODE = "depth_perf_mode"
private const val KEY_DEPTH_PRECISION = "depth_precision"
private const val KEY_DEPTH_LONG_EDGE = "depth_long_edge"
private const val DEFAULT_DEPTH_LONG_EDGE = 644
private const val KEY_CENTER_DISTANCE_RATIO = "center_distance_ratio"

object AppSettings {
    const val PERF_PERFORMANCE = 0
    const val PERF_BALANCED = 1
    const val PERF_QUALITY = 2

    fun centerDistanceRatio(context: Context): Float =
        StereoLayout.clampCenterDistanceRatio(
            prefs(context).getFloat(KEY_CENTER_DISTANCE_RATIO, StereoLayout.MIN_CENTER_DISTANCE_RATIO),
        )

    fun setCenterDistanceRatio(context: Context, ratio: Float) {
        prefs(context).edit()
            .putFloat(KEY_CENTER_DISTANCE_RATIO, StereoLayout.clampCenterDistanceRatio(ratio))
            .apply()
    }

    fun true3dEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TRUE_3D, false)

    fun setTrue3dEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TRUE_3D, enabled).apply()
        DepthProcessor.reset()
    }

    fun depthAnythingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DEPTH_ANYTHING, false)

    fun setDepthAnythingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DEPTH_ANYTHING, enabled).apply()
        DepthProcessor.reset()
    }

    fun disparity(context: Context): Float {
        val value = prefs(context).getFloat(KEY_DEPTH_DISPARITY, 1.8f)
        return if (value.isFinite()) value.coerceIn(0.2f, 4.0f) else 1.8f
    }

    fun setDisparity(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_DEPTH_DISPARITY, value.coerceIn(0.2f, 4.0f)).apply()
    }

    fun perfMode(context: Context): Int =
        prefs(context).getInt(KEY_DEPTH_PERF_MODE, PERF_BALANCED).coerceIn(PERF_PERFORMANCE, PERF_QUALITY)

    fun setPerfMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_DEPTH_PERF_MODE, mode.coerceIn(PERF_PERFORMANCE, PERF_QUALITY)).apply()
    }

    fun inferenceInterval(context: Context): Int {
        return when (perfMode(context)) {
            PERF_PERFORMANCE -> 3
            PERF_QUALITY -> 1
            else -> 2
        }
    }

    internal fun depthModel(context: Context): DepthModel =
        if (depthAnythingEnabled(context)) DepthModel.DEPTH_ANYTHING else DepthModel.MIDAS

    internal fun depthPrecision(context: Context): DepthPrecision {
        val key = prefs(context).getString(KEY_DEPTH_PRECISION, null)
        return DepthPrecision.entries.firstOrNull { it.key == key } ?: DepthPrecision.ORIGINAL
    }

    internal fun setDepthPrecision(context: Context, precision: DepthPrecision) {
        prefs(context).edit().putString(KEY_DEPTH_PRECISION, precision.key)
            .putBoolean(KEY_DEPTH_ANYTHING, true).apply()
        DepthProcessor.reset()
    }

    internal fun depthLongEdge(context: Context): Int {
        val value = prefs(context).getInt(KEY_DEPTH_LONG_EDGE, DEFAULT_DEPTH_LONG_EDGE)
        return value.takeIf(DEPTH_LONG_EDGES::contains) ?: DEFAULT_DEPTH_LONG_EDGE
    }

    internal fun setDepthLongEdge(context: Context, longEdge: Int) {
        require(longEdge in DEPTH_LONG_EDGES)
        val precision = depthPrecision(context).takeUnless { it == DepthPrecision.ORIGINAL } ?: DepthPrecision.FP16
        prefs(context).edit().putInt(KEY_DEPTH_LONG_EDGE, longEdge)
            .putString(KEY_DEPTH_PRECISION, precision.key).putBoolean(KEY_DEPTH_ANYTHING, true).apply()
        DepthProcessor.reset()
    }

    internal fun gpuDepthPrecision(context: Context): DepthPrecision? =
        if (depthAnythingEnabled(context)) depthPrecision(context).takeUnless { it == DepthPrecision.ORIGINAL } else null

    fun perfLabel(context: Context): String {
        return when (perfMode(context)) {
            PERF_PERFORMANCE -> "Performance"
            PERF_QUALITY -> "Quality"
            else -> "Balanced"
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
}
