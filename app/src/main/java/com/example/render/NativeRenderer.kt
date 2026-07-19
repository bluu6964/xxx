package com.example.render

import android.util.Log
import java.nio.ByteBuffer

object NativeRenderer {
    private const val TAG = "CPlusEngine"
    private const val ENGINE_VERSION = "3.0.0-max-cpp-option-c"

    init { try { System.loadLibrary("native-renderer") } catch (e: UnsatisfiedLinkError) { Log.e(TAG, "Load failed", e) } }

    val isAvailable: Boolean by lazy { try { getEngineVersion() == ENGINE_VERSION } catch (e: Throwable) { false } }
    fun loadFailureReason(): String = if (isAvailable) "Available ($ENGINE_VERSION)" else "Not available"

    fun nativeEngineVersion(): String = getEngineVersion()
    private external fun getEngineVersion(): String

    external fun runFullPipeline(outputPath: String, projectData: String, width: Int, height: Int, frameRate: Int, bitrate: Int): Boolean
    external fun compositeFrame(output: ByteBuffer, width: Int, height: Int, layers: Array<ByteBuffer>, transforms: FloatArray, alphas: FloatArray): Boolean
    external fun nativeBitmapToYuv420(bitmapBuffer: ByteBuffer, yuvBuffer: ByteBuffer, width: Int, height: Int): Boolean
}
