package com.example.render

import android.graphics.Bitmap

object GPURenderer {
    init {
        System.loadLibrary("motionstudio_renderer")
    }

    // Initialize OpenGL state machine, buffers and default shaders
    external fun nativeInit()

    // Release all bound GPU textures, buffers and shaders
    external fun nativeRelease()

    // Uploads an Android Bitmap to a GPU 2D Texture and returns the texture ID
    external fun nativeUploadTexture(bitmap: Bitmap): Int

    // Deletes a texture from GPU memory
    external fun nativeDeleteTexture(textureId: Int)

    // Performs GPU compositing and draws directly on the currently active EGL Context/Surface
    external fun nativeRenderLayers(
        outWidth: Int,
        outHeight: Int,
        textureIds: IntArray,
        opacities: FloatArray,
        offsetsX: FloatArray,
        offsetsY: FloatArray,
        rotations: FloatArray,
        scalesX: FloatArray,
        scalesY: FloatArray,
        blendModes: IntArray,      // 0 = Normal, 1 = Multiply, 2 = Screen, etc.
        effectTypes: IntArray,     // 0 = None, 1 = Blur, 2 = Saturation
        effectStrengths: FloatArray
    )
}
