package com.example

import android.content.Context
import com.example.render.NativeRenderer
import android.util.Log

object VideoRenderer {
    private const val TAG = "VideoRenderer"

    fun renderAndSaveVideo(context: Context, projectId: String): Pair<String?, String?> {
        Log.i(TAG, "Delegating to C++ engine (Option C)")
        val path = context.cacheDir?.absolutePath + "/export_${projectId}.mp4"
        val ok = NativeRenderer.runFullPipeline(path, projectId, 1920, 1080, 30, 50_000_000)
        return if (ok) path to path else null to null
    }
}
