package com.local.paralleleyeconverter

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import android.os.Process
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

internal class GpuDepthSession private constructor(
    val precision: DepthPrecision,
    val size: DepthInputSize,
    private val session: OrtSession,
    environment: OrtEnvironment,
) : AutoCloseable {
    private val pixelCount = size.width * size.height
    private val pixels = IntArray(pixelCount)
    private val inputShape = longArrayOf(1, 3, size.height.toLong(), size.width.toLong())
    private val outputShape = longArrayOf(1, size.height.toLong(), size.width.toLong())

    init {
        requireShape(session.inputInfo.getValue(INPUT).info as TensorInfo, inputShape)
        requireShape(session.outputInfo.getValue(OUTPUT).info as TensorInfo, outputShape)
    }

    private val rgb = ByteBuffer.allocateDirect(pixelCount * 3 * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val input = OnnxTensor.createTensor(environment, rgb, inputShape)
    private val inputs = mapOf(INPUT to input)

    fun run(frame: Bitmap): FloatArray? {
        frame.getPixels(pixels, 0, size.width, 0, 0, size.width, size.height)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            rgb.put(index, red[(pixel ushr 16) and 0xff])
            rgb.put(index + pixelCount, green[(pixel ushr 8) and 0xff])
            rgb.put(index + 2 * pixelCount, blue[pixel and 0xff])
        }
        return session.run(inputs).use { result ->
            val output = (result.get(0) as OnnxTensor).floatBuffer
            normalizeDepth(FloatArray(pixelCount).also { output.get(it) })
        }
    }

    override fun close() {
        try {
            input.close()
        } finally {
            session.close()
        }
    }

    companion object {
        private const val INPUT = "pixel_values"
        private const val OUTPUT = "predicted_depth"
        private val red = rgbNorm(0.485f, 0.229f)
        private val green = rgbNorm(0.456f, 0.224f)
        private val blue = rgbNorm(0.406f, 0.225f)

        fun open(context: Context, precision: DepthPrecision, size: DepthInputSize): GpuDepthSession {
            val backend = File(context.applicationInfo.nativeLibraryDir, "libQnnGpu.so")
            check(Process.is64Bit() && backend.isFile) { "QNN GPU backend unavailable" }
            val model = context.assets.openFd(requireNotNull(precision.asset)).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.length)
                }
            }
            val environment = OrtEnvironment.getEnvironment()
            val session = OrtSession.SessionOptions().use { options ->
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                options.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR)
                options.setIntraOpNumThreads(1)
                options.setSymbolicDimensionValue("batch_size", 1)
                options.setSymbolicDimensionValue("height", size.height.toLong())
                options.setSymbolicDimensionValue("width", size.width.toLong())
                options.addConfigEntry("session.disable_cpu_ep_fallback", "1")
                options.addQnn(mapOf("backend_path" to backend.absolutePath))
                environment.createSession(model, options)
            }
            try {
                return GpuDepthSession(precision, size, session, environment)
            } catch (error: Throwable) {
                session.close()
                throw error
            }
        }

        private fun requireShape(info: TensorInfo, shape: LongArray) {
            require(info.type == OnnxJavaType.FLOAT && info.shape.contentEquals(shape)) { "Unexpected depth tensor: $info" }
        }

        private fun rgbNorm(mean: Float, sd: Float) = FloatArray(256) { (it / 255f - mean) / sd }
    }
}
