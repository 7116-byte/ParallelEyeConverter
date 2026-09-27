package com.local.paralleleyeconverter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.io.FileInputStream
import kotlin.math.max
import kotlin.math.min

object DepthProcessor {
    private const val MIDAS_ASSET = "midas-v2-w8a8.tflite"
    private const val DEPTH_ANYTHING_ASSET = "depth_anything_v2.tflite"

    private val lock = Any()
    private var interpreter: Interpreter? = null
    private var activeModelBuffer: ByteBuffer? = null
    private var activeAsset: String? = null
    private var activeResolutionMode = -1
    private var inputWidth = 0
    private var inputHeight = 0
    private var inputType = DataType.FLOAT32
    private var outputWidth = 0
    private var outputHeight = 0
    private var outputType = DataType.FLOAT32
    private var outputElements = 0
    private var frameCounter = 0L
    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: ByteBuffer? = null
    private var scaledBitmap: Bitmap? = null
    private var inputPixels = IntArray(0)

    fun reset() {
        synchronized(lock) {
            interpreter?.close()
            interpreter = null
            activeModelBuffer = null
            activeAsset = null
            activeResolutionMode = -1
            frameCounter = 0L
            clearWorkingBuffers()
            DepthBus.clear()
        }
    }

    fun processFrame(context: Context, bitmap: Bitmap, frameVersion: Long) {
        if (!AppSettings.true3dEnabled(context)) {
            DepthBus.clear()
            return
        }
        synchronized(lock) {
            frameCounter++
            val interval = AppSettings.inferenceInterval(context)
            val model = AppSettings.modelName(context)
            if (frameCounter % interval != 0L && DepthBus.latestDepth != null) {
                DepthBus.markReuse(model)
                return
            }
            val localInterpreter = ensureInterpreter(context) ?: return
            val started = SystemClock.elapsedRealtime()
            runCatching {
                val input = buildInput(bitmap)
                val outputBytes = outputElements * bytesPerElement(outputType)
                val output = outputBuffer?.takeIf { it.capacity() == outputBytes }
                    ?: ByteBuffer.allocateDirect(outputBytes).order(ByteOrder.nativeOrder()).also { outputBuffer = it }
                output.clear()
                localInterpreter.run(input, output)
                output.rewind()
                val values = readOutput(output)
                DepthBus.publish(
                    DepthFrame(
                        values = values,
                        width = outputWidth,
                        height = outputHeight,
                        sourceFrameVersion = frameVersion,
                        model = model,
                        reused = false,
                    ),
                    SystemClock.elapsedRealtime() - started,
                )
            }.onFailure { error ->
                DepthBus.markUnavailable("3D failed: ${error.javaClass.simpleName}")
            }
        }
    }

    private fun ensureInterpreter(context: Context): Interpreter? {
        val wantedAsset = if (AppSettings.depthAnythingEnabled(context)) DEPTH_ANYTHING_ASSET else MIDAS_ASSET
        val wantedResolution = AppSettings.depthResolutionMode(context)
        val current = interpreter
        if (current != null && activeAsset == wantedAsset && activeResolutionMode == wantedResolution) {
            return current
        }
        interpreter?.close()
        interpreter = null
        activeModelBuffer = null
        clearWorkingBuffers()
        return createInterpreter(context, wantedAsset, wantedResolution)
            ?: if (wantedAsset != MIDAS_ASSET) createInterpreter(context, MIDAS_ASSET, AppSettings.RES_NATIVE) else null
    }

    private fun createInterpreter(context: Context, assetName: String, resolutionMode: Int): Interpreter? {
        var created: Interpreter? = null
        return runCatching {
            val modelBuffer = context.assets.openFd(assetName).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { input ->
                    input.channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.length)
                }
            }
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            val interpreter = Interpreter(modelBuffer, options)
            created = interpreter
            val nativeShape = interpreter.getInputTensor(0).shape()
            val target = targetInputShape(nativeShape, resolutionMode)
            if (target != null && !target.contentEquals(nativeShape)) {
                runCatching {
                    interpreter.resizeInput(0, target)
                    interpreter.allocateTensors()
                }.onFailure {
                    interpreter.resizeInput(0, nativeShape)
                    interpreter.allocateTensors()
                    DepthBus.markUnavailable("depth resolution: fallback native")
                }
            } else {
                interpreter.allocateTensors()
            }
            configureTensorInfo(interpreter)
            this.interpreter = interpreter
            activeModelBuffer = modelBuffer
            activeAsset = assetName
            activeResolutionMode = resolutionMode
            DepthBus.markUnavailable("3D ready: ${if (assetName == DEPTH_ANYTHING_ASSET) "Depth Anything V2" else "MiDaS"}")
            interpreter
        }.getOrElse { error ->
            created?.close()
            DepthBus.markUnavailable("3D init failed: ${error.javaClass.simpleName}")
            null
        }
    }

    private fun targetInputShape(nativeShape: IntArray, resolutionMode: Int): IntArray? {
        if (nativeShape.size != 4 || resolutionMode == AppSettings.RES_NATIVE) return null
        val targetSide = when (resolutionMode) {
            AppSettings.RES_360P -> 360
            AppSettings.RES_480P -> 480
            AppSettings.RES_720P -> 720
            else -> return null
        }
        return intArrayOf(nativeShape[0], targetSide, targetSide, nativeShape[3])
    }

    private fun configureTensorInfo(created: Interpreter) {
        val inputTensor = created.getInputTensor(0)
        val inputShape = inputTensor.shape()
        inputHeight = inputShape.getOrElse(1) { 256 }
        inputWidth = inputShape.getOrElse(2) { inputHeight }
        inputType = inputTensor.dataType()

        val outputTensor = created.getOutputTensor(0)
        val outputShape = outputTensor.shape()
        outputType = outputTensor.dataType()
        outputElements = outputShape.fold(1) { acc, value -> acc * max(1, value) }
        val dims = inferOutputDims(outputShape)
        outputHeight = dims.first
        outputWidth = dims.second
        if (outputWidth * outputHeight > outputElements) {
            outputWidth = max(1, outputElements)
            outputHeight = 1
        }
    }

    private fun inferOutputDims(shape: IntArray): Pair<Int, Int> {
        return when {
            shape.size >= 4 && shape[3] == 1 -> shape[1] to shape[2]
            shape.size >= 4 && shape[1] == 1 -> shape[2] to shape[3]
            shape.size >= 3 -> shape[1] to shape[2]
            shape.size >= 2 -> shape[0] to shape[1]
            else -> 1 to max(1, shape.firstOrNull() ?: 1)
        }
    }

    private fun buildInput(bitmap: Bitmap): ByteBuffer {
        val bytes = inputWidth * inputHeight * 3 * bytesPerElement(inputType)
        val input = inputBuffer?.takeIf { it.capacity() == bytes }
            ?: ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder()).also { inputBuffer = it }
        input.clear()
        val scaled = scaledBitmap?.takeIf { it.width == inputWidth && it.height == inputHeight }
            ?: Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888).also { scaledBitmap = it }
        Canvas(scaled).drawBitmap(
            bitmap,
            Rect(0, 0, bitmap.width, bitmap.height),
            Rect(0, 0, inputWidth, inputHeight),
            null,
        )
        val pixelCount = inputWidth * inputHeight
        if (inputPixels.size != pixelCount) inputPixels = IntArray(pixelCount)
        scaled.getPixels(inputPixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        for (pixel in inputPixels) {
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            when (inputType) {
                DataType.FLOAT32 -> {
                    input.putFloat(r / 255f)
                    input.putFloat(g / 255f)
                    input.putFloat(b / 255f)
                }
                DataType.INT8 -> {
                    input.put((r - 128).toByte())
                    input.put((g - 128).toByte())
                    input.put((b - 128).toByte())
                }
                else -> {
                    input.put(r.toByte())
                    input.put(g.toByte())
                    input.put(b.toByte())
                }
            }
        }
        input.rewind()
        return input
    }

    private fun clearWorkingBuffers() {
        inputBuffer = null
        outputBuffer = null
        scaledBitmap?.recycle()
        scaledBitmap = null
        inputPixels = IntArray(0)
    }

    private fun readOutput(output: ByteBuffer): FloatArray {
        val raw = FloatArray(outputWidth * outputHeight)
        var minValue = Float.MAX_VALUE
        var maxValue = -Float.MAX_VALUE
        for (index in raw.indices) {
            val value = when (outputType) {
                DataType.FLOAT32 -> output.float
                DataType.INT8 -> output.get().toInt().toFloat()
                else -> output.get().toInt().and(0xff).toFloat()
            }
            raw[index] = value
            minValue = min(minValue, value)
            maxValue = max(maxValue, value)
        }
        val range = (maxValue - minValue).takeIf { it > 0.00001f } ?: 1f
        for (index in raw.indices) {
            raw[index] = ((raw[index] - minValue) / range).coerceIn(0f, 1f)
        }
        return raw
    }

    private fun bytesPerElement(type: DataType): Int =
        if (type == DataType.FLOAT32) 4 else 1
}
