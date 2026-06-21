package com.local.paralleleyeconverter

import android.content.Context

private const val SETTINGS_PREFS = "converter_settings"
private const val KEY_TRUE_3D = "true_3d_rendering"
private const val KEY_DEPTH_ANYTHING = "depth_anything_v2"
private const val KEY_DEPTH_DISPARITY = "depth_disparity"
private const val KEY_DEPTH_PERF_MODE = "depth_perf_mode"
private const val KEY_DEPTH_RESOLUTION_MODE = "depth_resolution_mode"

object AppSettings {
    const val PERF_PERFORMANCE = 0
    const val PERF_BALANCED = 1
    const val PERF_QUALITY = 2

    const val RES_NATIVE = 0
    const val RES_360P = 1
    const val RES_480P = 2
    const val RES_720P = 3

    fun true3dEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TRUE_3D, false)

    fun setTrue3dEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TRUE_3D, enabled).apply()
        if (!enabled) {
            DepthBus.clear()
        }
    }

    fun depthAnythingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DEPTH_ANYTHING, false)

    fun setDepthAnythingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DEPTH_ANYTHING, enabled).apply()
        DepthProcessor.reset()
    }

    fun disparity(context: Context): Float =
        prefs(context).getFloat(KEY_DEPTH_DISPARITY, 1.8f).coerceIn(0.2f, 4.0f)

    fun setDisparity(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_DEPTH_DISPARITY, value.coerceIn(0.2f, 4.0f)).apply()
    }

    fun perfMode(context: Context): Int =
        prefs(context).getInt(KEY_DEPTH_PERF_MODE, PERF_BALANCED).coerceIn(PERF_PERFORMANCE, PERF_QUALITY)

    fun setPerfMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_DEPTH_PERF_MODE, mode.coerceIn(PERF_PERFORMANCE, PERF_QUALITY)).apply()
    }

    fun depthResolutionMode(context: Context): Int =
        prefs(context).getInt(KEY_DEPTH_RESOLUTION_MODE, RES_NATIVE).coerceIn(RES_NATIVE, RES_720P)

    fun setDepthResolutionMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_DEPTH_RESOLUTION_MODE, mode.coerceIn(RES_NATIVE, RES_720P)).apply()
        DepthProcessor.reset()
    }

    fun inferenceInterval(context: Context): Int {
        return when (perfMode(context)) {
            PERF_PERFORMANCE -> 3
            PERF_QUALITY -> 1
            else -> 2
        }
    }

    fun modelName(context: Context): String =
        if (depthAnythingEnabled(context)) "Depth Anything V2" else "MiDaS"

    fun perfLabel(context: Context): String {
        return when (perfMode(context)) {
            PERF_PERFORMANCE -> "Performance"
            PERF_QUALITY -> "Quality"
            else -> "Balanced"
        }
    }

    fun resolutionLabel(context: Context): String {
        return when (depthResolutionMode(context)) {
            RES_360P -> "360P"
            RES_480P -> "480P"
            RES_720P -> "720P"
            else -> "Native"
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
}
