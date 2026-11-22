package com.local.paralleleyeconverter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import kotlin.math.roundToInt

internal enum class DepthModel(val label: String, val asset: String, val side: Int) {
    MIDAS("MiDaS", "midas-v2-w8a8.tflite", 256),
    DEPTH_ANYTHING("Depth Anything V2", "depth_anything_v2.tflite", 518),
}

object DepthProcessor {
    private const val TAG = "ParallelEyeDepth"
    private const val RETRY_DELAY_MS = 5000L
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "eye-depth").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val lock = Any()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var generation = 0L
    private var framesSinceSubmit = 0
    private var busy = false
    private var retryAfterMs = 0L
    private var snapshot: Bitmap? = null

    // Only the inference worker accesses the interpreter and its buffers.
    private var session: DepthSession? = null

    fun reset() = synchronized(lock) {
        clearFrame()
        snapshot = null
        worker.execute {
            closeSession()
        }
        Unit
    }

    fun clearFrame() = synchronized(lock) {
        generation++
        framesSinceSubmit = 0
        retryAfterMs = 0L
        DepthBus.clear()
    }

    fun submitFrame(context: Context, bitmap: Bitmap, frameVersion: Long) = synchronized(lock) {
        if (!AppSettings.true3dEnabled(context)) return
        val interval = AppSettings.inferenceInterval(context)
        framesSinceSubmit = (framesSinceSubmit + 1).coerceAtMost(interval)
        if (busy || SystemClock.elapsedRealtime() < retryAfterMs) return
        if (framesSinceSubmit < interval && DepthBus.latestDepth != null) return

        val requested = AppSettings.depthModel(context)
        val side = requested.side
        val copy = snapshot?.takeIf { it.width == side }
            ?: Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).also { snapshot = it }
        // Capture updates its full-size bitmap in place. Copy it before handing work off.
        copy.eraseColor(0)
        Canvas(copy).drawBitmap(bitmap, null, Rect(0, 0, side, side), paint)
        val submittedGeneration = generation
        val app = context.applicationContext
        busy = true
        framesSinceSubmit = 0
        if (DepthBus.latestDepth == null) DepthBus.lastStatus = "3D computing..."
        worker.execute {
            try {
                val start = SystemClock.elapsedRealtime()
                val (model, values) = infer(app, requested, copy)
                synchronized(lock) {
                    if (generation != submittedGeneration) return@execute
                    val elapsed = SystemClock.elapsedRealtime() - start
                    val label = if (model == requested) model.label else "${model.label} fallback"
                    if (values == null) {
                        DepthBus.lastStatus = if (DepthBus.latestDepth == null) {
                            "$label: no usable depth (2D)"
                        } else {
                            "$label: reusing last usable depth"
                        }
                    } else {
                        val first = DepthBus.latestDepth == null
                        DepthBus.publish(DepthFrame(values, model.side, model.side, frameVersion, label), elapsed)
                        if (first) Log.i(TAG, "First usable depth: ${DepthBus.lastStatus}")
                    }
                }
            } catch (error: Throwable) {
                if (!isInferenceFailure(error)) throw error
                closeSession()
                Log.w(TAG, "${requested.label} inference failed", error)
                synchronized(lock) {
                    if (generation == submittedGeneration) {
                        retryAfterMs = SystemClock.elapsedRealtime() + RETRY_DELAY_MS
                        DepthBus.clear()
                        DepthBus.lastStatus = "3D failed (2D): ${error.javaClass.simpleName}"
                    }
                }
            } finally {
                synchronized(lock) { busy = false }
            }
        }
    }

    private fun infer(context: Context, requested: DepthModel, frame: Bitmap): Pair<DepthModel, FloatArray?> {
        val model = session?.model ?: requested
        return try {
            val active = session ?: DepthSession.open(context, model).also { session = it }
            model to active.run(frame)
        } catch (error: Throwable) {
            if (model == DepthModel.MIDAS || !isInferenceFailure(error)) throw error
            Log.w(TAG, "${model.label} failed; trying MiDaS", error)
            closeSession()
            val fallback = DepthSession.open(context, DepthModel.MIDAS).also { session = it }
            fallback.model to fallback.run(frame)
        }
    }

    private fun closeSession() {
        val previous = session
        session = null
        previous?.close()
    }

    private fun isInferenceFailure(error: Throwable): Boolean =
        error is Exception || error is LinkageError || error is OutOfMemoryError
}

private class DepthSession private constructor(
    val model: DepthModel,
    private val interpreter: Interpreter,
) {
    private val side = model.side
    private val inputTensor = interpreter.getInputTensor(0)
    private val outputTensor = interpreter.getOutputTensor(0)
    private val inputType = inputTensor.dataType()
    private val outputType = outputTensor.dataType()

    init {
        require(inputTensor.shape().contentEquals(intArrayOf(1, side, side, 3))) { "Unexpected input tensor" }
        require(outputTensor.shape().contentEquals(intArrayOf(1, side, side, 1))) { "Unexpected output tensor" }
        require(inputType == DataType.FLOAT32 || inputType == DataType.UINT8) { "Unsupported input type: $inputType" }
        require(outputType == DataType.FLOAT32 || outputType == DataType.UINT8) { "Unsupported output type: $outputType" }
    }

    private val input = ByteBuffer.allocateDirect(inputTensor.numBytes()).order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(outputTensor.numBytes()).order(ByteOrder.nativeOrder())
    private val pixels = IntArray(side * side)
    private val quantizedRgb = if (inputType == DataType.UINT8) {
        val params = inputTensor.quantizationParams()
        require(params.scale.isFinite() && params.scale > 0f) { "Invalid RGB quantization scale" }
        ByteArray(256) { value ->
            (value / 255f / params.scale + params.zeroPoint).roundToInt().coerceIn(0, 255).toByte()
        }
    } else {
        null
    }
    private var fallbackBitmap: Bitmap? = null

    fun run(snapshot: Bitmap): FloatArray? {
        val frame = if (snapshot.width == side) snapshot else {
            val scaled = fallbackBitmap ?: Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
                .also { fallbackBitmap = it }
            scaled.eraseColor(0)
            Canvas(scaled).drawBitmap(snapshot, null, Rect(0, 0, side, side), Paint(Paint.FILTER_BITMAP_FLAG))
            scaled
        }
        frame.getPixels(pixels, 0, side, 0, 0, side, side)
        input.clear()
        val rgb = quantizedRgb
        for (pixel in pixels) {
            val r = (pixel ushr 16) and 0xff
            val g = (pixel ushr 8) and 0xff
            val b = pixel and 0xff
            if (rgb == null) {
                input.putFloat(r / 255f)
                input.putFloat(g / 255f)
                input.putFloat(b / 255f)
            } else {
                input.put(rgb[r])
                input.put(rgb[g])
                input.put(rgb[b])
            }
        }
        input.rewind()
        output.clear()
        interpreter.run(input, output)
        output.rewind()

        // For UINT8 output, affine dequantization cancels during min-max normalization.
        val raw = FloatArray(pixels.size) {
            if (outputType == DataType.FLOAT32) output.float else (output.get().toInt() and 0xff).toFloat()
        }
        return normalizeDepth(raw)
    }

    fun close() {
        interpreter.close()
        fallbackBitmap?.recycle()
    }

    companion object {
        fun open(context: Context, model: DepthModel): DepthSession {
            val buffer = context.assets.openFd(model.asset).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.length)
                }
            }
            val interpreter = Interpreter(buffer, Interpreter.Options().setNumThreads(2))
            try {
                interpreter.allocateTensors()
                return DepthSession(model, interpreter)
            } catch (error: Throwable) {
                interpreter.close()
                throw error
            }
        }
    }
}

internal fun normalizeDepth(values: FloatArray): FloatArray? {
    var low = Float.POSITIVE_INFINITY
    var high = Float.NEGATIVE_INFINITY
    for (value in values) {
        if (value.isFinite()) {
            low = minOf(low, value)
            high = maxOf(high, value)
        }
    }
    val range = high.toDouble() - low
    if (!range.isFinite() || range <= 0.00001) return null
    for (index in values.indices) {
        val value = values[index]
        values[index] = if (value.isFinite()) ((value.toDouble() - low) / range).toFloat() else 0.5f
    }
    return values
}
