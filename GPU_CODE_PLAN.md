# GPU (OpenGL ES 3.0) Code Implementation Blueprint
## মোশন স্টুডিও (Motion Studio) - কোড ডিজাইন ও বাস্তবায়ন পরিকল্পনা

এই ডকুমেন্টটিতে OpenGL ES 3.0 ভিত্তিক জিপিইউ রেন্ডারিং আর্কিটেকচারকে বাস্তবে রূপ দেওয়ার জন্য প্রয়োজনীয় প্রতিটি ফাইল, ক্লাস, কাস্টম ওপেনজিএল র্যাপার এবং জেএনআই (JNI) মেথড সমূহের বিস্তারিত কোড ডিজাইন (Blueprint) দেওয়া হলো।

---

## ১. ফাইল এবং ডিরেক্টরি স্ট্রাকচার (Target File Map)

রেন্ডারিং পাইপলাইনকে কোডবেসে যুক্ত করার জন্য নিচের নতুন ফাইলগুলো তৈরি করা হবে:

```
app/src/main/
├── cpp/
│   ├── CMakeLists.txt              <-- (OpenGL ES এবং GLES3 লাইব্রেরি লিংক করা হবে)
│   ├── gles_bridge.cpp             <-- JNI পোর্টস (GPURenderer এবং C++ GLES কানেকশন)
│   ├── gles_compositor.h           <-- C++ GLES Compositor ডিক্লারেশন
│   ├── gles_compositor.cpp         <-- C++ VBO/VAO, টেক্সচার বাইন্ডিং ও ড্র-কল লজিক
│   ├── gles_shader.h               <-- GLSL কম্পাইলেশন, লিঙ্কিং এবং প্রোগ্রাম ম্যানেজমেন্ট
│   └── gles_shader.cpp
└── java/com/example/
    ├── render/
    │   ├── EGLCore.kt              <-- EGLDisplay, EGLContext, EGLConfig র্যাপার ক্লাস
    │   ├── WindowSurface.kt        <-- EGLSurface র্যাপার (Preview & MediaCodec Surface)
    │   └── GPURenderer.kt          <-- প্রধান কোটলিন জিপিইউ কন্ট্রোলার ও JNI ব্রিজিং
    └── VideoRenderer.kt            <-- (বিদ্যমান ফাইলে জিপিইউ রেন্ডার কল যুক্ত করা হবে)
```

---

## ২. কোটলিন ওপেনজিএল র্যাপার ক্লাস (Kotlin OpenGL Wrappers)

অ্যান্ড্রয়েডে গ্রাফিক্স কনটেক্সট এবং সারফেস ম্যানেজ করার জন্য লো-লেভেল `EGL14` এপিআই ব্যবহার করা হয়। ডেভেলপমেন্ট সহজ করার জন্য আমরা নিচের র্যাপার ক্লাসগুলো ডিজাইন করব।

### ক) `EGLCore.kt`
এটি OpenGL ES কনটেক্সট লাইফসাইকেল এবং কনফিগারেশন র্যাপ করবে।

```kotlin
package com.example.render

import android.opengl.*
import android.util.Log

class EGLCore(sharedContext: EGLContext? = null, flags: Int = 0) {
    private var eglDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null

    init {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("EGL display initialization failed")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("EGL initialization failed")
        }

        // Setup OpenGL ES 3.0 configuration
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT or 0x0040, // ES3 support flag
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)
        eglConfig = configs[0] ?: throw RuntimeException("EGLConfig not found")

        // Create EGL Context
        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, // OpenGL ES 3.0 context
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, sharedContext ?: EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("EGL Context creation failed")
        }
    }

    fun makeCurrent(eglSurface: EGLSurface) {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    fun swapBuffers(eglSurface: EGLSurface): Boolean {
        return EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun getEGLConfig(): EGLConfig? = eglConfig
    fun getEGLDisplay(): EGLDisplay = eglDisplay

    fun release() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
    }
}
```

### খ) `WindowSurface.kt`
যেকোনো `Surface` (যেমন `SurfaceView`, `TextureView` বা `MediaCodec.createInputSurface()`) কে এটি ওপেনজিএল রেন্ডার টার্গেট হিসেবে ম্যাপিং করে।

```kotlin
package com.example.render

import android.opengl.EGL14
import android.opengl.EGLSurface
import android.view.Surface

class WindowSurface(private val eglCore: EGLCore, val surface: Surface) {
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    init {
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(
            eglCore.getEGLDisplay(),
            eglCore.getEGLConfig(),
            surface,
            surfaceAttribs,
            0
        )
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("Failed to create EGL window surface")
        }
    }

    fun makeCurrent() {
        eglCore.makeCurrent(eglSurface)
    }

    fun swapBuffers(): Boolean {
        return eglCore.swapBuffers(eglSurface)
    }

    fun release() {
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(eglCore.getEGLDisplay(), eglSurface)
            eglSurface = EGL14.EGL_NO_SURFACE
        }
        surface.release()
    }
}
```

### গ) `GPURenderer.kt` (JNI Interface)
এটি কোটলিন স্তরের প্রধান কন্ট্রোলার যা সরাসরি আমাদের নেটিভ জিপিইউ কম্পোজিটরের সাথে কথা বলবে।

```kotlin
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
```

---

## ৩. সি++ নেটিভ গ্রাফিক্স ইঞ্জিন (Native GLES C++ Engine)

জিপিইউ কোডের প্রসেসিং পার্ট সম্পূর্ণ সি++ স্তরে কাজ করবে। নিচে এর ক্লাস ডিজাইন দেওয়া হলো।

### ক) `gles_shader.h`
ওপেনজিএল শেডার কম্পাইল ও লিঙ্ক করার জন্য ইউটিলিটি ক্লাস।

```cpp
#pragma once
#include <GLES3/gl3.h>
#include <string>

class GLESShader {
private:
    GLuint mProgramId = 0;
    GLuint compileShader(GLenum type, const std::string& source);

public:
    GLESShader() = default;
    ~GLESShader() { release(); }

    bool loadFromSources(const std::string& vertexSource, const std::string& fragmentSource);
    void use() const { glUseProgram(mProgramId); }
    void release();

    // Uniform setters
    void setUniform1f(const std::string& name, float value) const;
    void setUniform1i(const std::string& name, int value) const;
    void setUniformMatrix4fv(const std::string& name, const float* matrix) const;
};
```

### খ) `gles_compositor.h`
লেয়ারগুলো জিপিইউ কোর ব্যবহার করে আকৃতি এবং সঠিক জ্যামিতিক ট্রান্সফর্ম অনুযায়ী রেন্ডার করবে।

```cpp
#pragma once
#include <GLES3/gl3.h>
#include <vector>
#include "gles_shader.h"

struct GLESLayer {
    GLuint textureId = 0;
    float opacity = 1.0f;
    float offsetX = 0.0f;
    float offsetY = 0.0f;
    float rotationDegrees = 0.0f;
    float scaleX = 1.0f;
    float scaleY = 1.0f;
    int blendMode = 0;
    int effectType = 0;
    float effectStrength = 0.0f;
};

class GLESCompositor {
private:
    GLESShader mDefaultShader;
    GLuint mVao = 0;
    GLuint mVboVertices = 0;
    GLuint mVboTexCoords = 0;

    void setupGeometry();
    void computeMVPMatrix(float* matrix, float outW, float outH, const GLESLayer& layer);

public:
    GLESCompositor() = default;
    ~GLESCompositor() { release(); }

    bool init();
    void renderFrame(int outWidth, int outHeight, const std::vector<GLESLayer>& layers);
    void release();
};
```

### গ) `gles_compositor.cpp`
নিচে জিপিইউ রেন্ডার কল এবং ট্রান্সফর্মেশনের বিস্তারিত লজিক ইমপ্লিমেন্টেশন দেওয়া হলো:

```cpp
#include "gles_compositor.h"
#include <cmath>
#include <android/log.h>

#define LOG_TAG "GLESCompositor"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

const char* VERTEX_SHADER_SRC = R"(#version 300 es
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aTexCoord;
out vec2 vTexCoord;
uniform mat4 uMVPMatrix;
void main() {
    gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
    vTexCoord = aTexCoord;
})";

const char* FRAGMENT_SHADER_SRC = R"(#version 300 es
precision mediump float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uTexture;
uniform float uOpacity;
uniform int uEffectType;
uniform float uEffectStrength;
void main() {
    vec4 texColor = texture(uTexture, vTexCoord);
    
    // Premultiplied alpha blend mode standard
    texColor.rgb *= uOpacity;
    texColor.a *= uOpacity;

    // Fast Color Temperature / Warmth Effect on GPU
    if (uEffectType == 2) { 
        texColor.r += uEffectStrength * 0.15 * texColor.a;
        texColor.b -= uEffectStrength * 0.10 * texColor.a;
    }
    
    fragColor = texColor;
})";

bool GLESCompositor::init() {
    if (!mDefaultShader.loadFromSources(VERTEX_SHADER_SRC, FRAGMENT_SHADER_SRC)) {
        LOGE("Failed to load shaders.");
        return false;
    }
    setupGeometry();
    return true;
}

void GLESCompositor::setupGeometry() {
    // Standard Quad [-1, 1] for 2D layer rendering
    GLfloat vertices[] = {
        -1.0f,  1.0f, 0.0f,
        -1.0f, -1.0f, 0.0f,
         1.0f,  1.0f, 0.0f,
         1.0f, -1.0f, 0.0f
    };
    GLfloat texCoords[] = {
        0.0f, 0.0f, // Top-Left
        0.0f, 1.0f, // Bottom-Left
        1.0f, 0.0f, // Top-Right
        1.0f, 1.0f  // Bottom-Right
    };

    glGenVertexArrays(1, &mVao);
    glBindVertexArray(mVao);

    glGenBuffers(1, &mVboVertices);
    glBindBuffer(GL_ARRAY_BUFFER, mVboVertices);
    glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 0, nullptr);
    glEnableVertexAttribArray(0);

    glGenBuffers(1, &mVboTexCoords);
    glBindBuffer(GL_ARRAY_BUFFER, mVboTexCoords);
    glBufferData(GL_ARRAY_BUFFER, sizeof(texCoords), texCoords, GL_STATIC_DRAW);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 0, nullptr);
    glEnableVertexAttribArray(1);

    glBindVertexArray(0);
}

void GLESCompositor::computeMVPMatrix(float* out, float outW, float outH, const GLESLayer& layer) {
    // 2D Projection, Translation, Scale, and Rotation Matrix calculations
    float rad = layer.rotationDegrees * M_PI / 180.0f;
    float cosR = std::cos(rad);
    float sinR = std::sin(rad);

    // Calculate dynamic scaling factors based on output dimensions
    float sx = layer.scaleX;
    float sy = layer.scaleY;

    // Convert pixel offset coordinates to normalized device coordinates [-1, 1]
    float tx = (layer.offsetX / outW) * 2.0f;
    float ty = -(layer.offsetY / outH) * 2.0f; // Flip Y axis to match Android UI coordinate system

    // Basic 2D Affine MVP Matrix setup
    out[0] = cosR * sx;  out[4] = -sinR * sy; out[8] = 0.0f;  out[12] = tx;
    out[1] = sinR * sx;  out[5] = cosR * sy;  out[9] = 0.0f;  out[13] = ty;
    out[2] = 0.0f;       out[6] = 0.0f;       out[10] = 1.0f; out[14] = 0.0f;
    out[3] = 0.0f;       out[7] = 0.0f;       out[11] = 0.0f; out[15] = 1.0f;
}

void GLESCompositor::renderFrame(int outWidth, int outHeight, const std::vector<GLESLayer>& layers) {
    glViewport(0, 0, outWidth, outHeight);
    
    // Clear back buffer with dark background
    glClearColor(0.12f, 0.12f, 0.12f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    // Blend Setup: Premultiplied Alpha
    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

    mDefaultShader.use();
    glBindVertexArray(mVao);

    for (const auto& layer : layers) {
        if (layer.textureId == 0 || layer.opacity <= 0.0f) continue;

        float mvpMatrix[16];
        computeMVPMatrix(mvpMatrix, (float)outWidth, (float)outHeight, layer);

        mDefaultShader.setUniformMatrix4fv("uMVPMatrix", mvpMatrix);
        mDefaultShader.setUniform1f("uOpacity", layer.opacity);
        mDefaultShader.setUniform1i("uEffectType", layer.effectType);
        mDefaultShader.setUniform1f("uEffectStrength", layer.effectStrength);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, layer.textureId);
        mDefaultShader.setUniform1i("uTexture", 0);

        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    }

    glBindVertexArray(0);
    glDisable(GL_BLEND);
}

void GLESCompositor::release() {
    if (mVao != 0) {
        glDeleteVertexArrays(1, &mVao);
        glDeleteBuffers(1, &mVboVertices);
        glDeleteBuffers(1, &mVboTexCoords);
        mVao = 0;
    }
    mDefaultShader.release();
}
```

---

## ৪. জেএনআই ব্রিজ সংযোগ (`gles_bridge.cpp`)

অ্যান্ড্রয়েডের কোটলিন স্তরের রেন্ডারার মেথডগুলোর নেটিভ সংযোগ তৈরি করা:

```cpp
#include <jni.h>
#include <android/bitmap.h>
#include <GLES3/gl3.h>
#include <vector>
#include "gles_compositor.h"

static GLESCompositor* gCompositor = nullptr;

extern "C" JNIEXPORT void JNICALL
Java_com_example_render_GPURenderer_nativeInit(JNIEnv* env, jobject /* this */) {
    if (gCompositor == nullptr) {
        gCompositor = new GLESCompositor();
        gCompositor->init();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_render_GPURenderer_nativeRelease(JNIEnv* env, jobject /* this */) {
    if (gCompositor != nullptr) {
        gCompositor->release();
        delete gCompositor;
        gCompositor = nullptr;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_render_GPURenderer_nativeUploadTexture(JNIEnv* env, jobject /* this */, jobject bitmap) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return 0;

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return 0;

    GLuint textureId;
    glGenTextures(1, &textureId);
    glBindTexture(GL_TEXTURE_2D, textureId);

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, info.width, info.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

    AndroidBitmap_unlockPixels(env, bitmap);
    return (jint)textureId;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_render_GPURenderer_nativeDeleteTexture(JNIEnv* env, jobject /* this */, jint textureId) {
    GLuint tex = (GLuint)textureId;
    glDeleteTextures(1, &tex);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_render_GPURenderer_nativeRenderLayers(
        JNIEnv* env, jobject /* this */,
        jint outWidth, jint outHeight,
        jintArray textureIds, jfloatArray opacities,
        jfloatArray offsetsX, jfloatArray offsetsY,
        jfloatArray rotations, jfloatArray scalesX, jfloatArray scalesY,
        jintArray blendModes, jintArray effectTypes, jfloatArray effectStrengths
) {
    if (gCompositor == nullptr) return;

    jsize layerCount = env->GetArrayLength(textureIds);
    std::vector<GLESLayer> layers(layerCount);

    jint* texArr = env->GetIntArrayElements(textureIds, nullptr);
    jfloat* opArr = env->GetFloatArrayElements(opacities, nullptr);
    jfloat* offXArr = env->GetFloatArrayElements(offsetsX, nullptr);
    jfloat* offYArr = env->GetFloatArrayElements(offsetsY, nullptr);
    jfloat* rotArr = env->GetFloatArrayElements(rotations, nullptr);
    jfloat* scXArr = env->GetFloatArrayElements(scalesX, nullptr);
    jfloat* scYArr = env->GetFloatArrayElements(scalesY, nullptr);
    jint* blendArr = env->GetIntArrayElements(blendModes, nullptr);
    jint* effTypeArr = env->GetIntArrayElements(effectTypes, nullptr);
    jfloat* effStrengthArr = env->GetFloatArrayElements(effectStrengths, nullptr);

    for (jsize i = 0; i < layerCount; i++) {
        layers[i].textureId = (GLuint)texArr[i];
        layers[i].opacity = opArr[i];
        layers[i].offsetX = offXArr[i];
        layers[i].offsetY = offYArr[i];
        layers[i].rotationDegrees = rotArr[i];
        layers[i].scaleX = scXArr[i];
        layers[i].scaleY = scYArr[i];
        layers[i].blendMode = blendArr[i];
        layers[i].effectType = effTypeArr[i];
        layers[i].effectStrength = effStrengthArr[i];
    }

    gCompositor->renderFrame((int)outWidth, (int)outHeight, layers);

    env->ReleaseIntArrayElements(textureIds, texArr, JNI_ABORT);
    env->ReleaseFloatArrayElements(opacities, opArr, JNI_ABORT);
    env->ReleaseFloatArrayElements(offsetsX, offXArr, JNI_ABORT);
    env->ReleaseFloatArrayElements(offsetsY, offYArr, JNI_ABORT);
    env->ReleaseFloatArrayElements(rotations, rotArr, JNI_ABORT);
    env->ReleaseFloatArrayElements(scalesX, scXArr, JNI_ABORT);
    env->ReleaseFloatArrayElements(scalesY, scYArr, JNI_ABORT);
    env->ReleaseIntArrayElements(blendModes, blendArr, JNI_ABORT);
    env->ReleaseIntArrayElements(effectTypes, effTypeArr, JNI_ABORT);
    env->ReleaseFloatArrayElements(effectStrengths, effStrengthArr, JNI_ABORT);
}
```

---

## ৫. `VideoRenderer.kt` এ সংযোগ করার উপায় (Integration)

পুরানো `Canvas` লুপের পরিবর্তে কীভাবে কাস্টম জিপিইউ এক্সপোর্ট পাইপলাইন সংযোগ করতে হবে তার একটি ডিজাইন নমুনা নিচে দেওয়া হলো:

```kotlin
// VideoRenderer.kt এর রেন্ডার এপিআই এর আংশিক পরিবর্তন

fun exportVideoUsingGPU(context: Context, width: Int, height: Int, mediaLayers: List<ExportMediaLayer>) {
    // ১. মিডিয়া কোডেক এনকোডার এবং মিউজিক তৈরি
    val encoder = MediaCodec.createEncoderByType("video/avc")
    // ... কনফিগারেশন সেটআপ ...
    
    // ২. জিপিইউ রেন্ডারিং এর প্রধান উইন্ডো সারফেস তৈরি (মিডিয়া কোডেক থেকে)
    val inputSurface = encoder.createInputSurface()
    
    // ৩. OpenGL ES Context ইনিশিয়ালাইজেশন
    val eglCore = EGLCore()
    val renderSurface = WindowSurface(eglCore, inputSurface)
    renderSurface.makeCurrent()
    GPURenderer.nativeInit()

    // ৪. টেক্সচার জেনারেট এবং আপলোড লুপ
    val textureIds = IntArray(mediaLayers.size)
    for (i in mediaLayers.indices) {
        val bitmap = mediaLayers[i].imageBitmap ?: continue
        textureIds[i] = GPURenderer.nativeUploadTexture(bitmap)
    }

    // ৫. এক্সপোর্ট লুপ
    var frameIndex = 0
    val totalFrames = 300 // ধরি ১০ সেকেন্ড ৩০ এফপিএস-এ
    
    while (frameIndex < totalFrames) {
        // প্রতি ফ্রেমে কীফ্রেম থেকে প্যারামিটার নিয়ে অ্যারে সাজানো হবে
        val opacities = FloatArray(mediaLayers.size) { 1.0f }
        val offsetsX = FloatArray(mediaLayers.size) { 0.0f }
        val offsetsY = FloatArray(mediaLayers.size) { 0.0f }
        val rotations = FloatArray(mediaLayers.size) { 0.0f }
        val scalesX = FloatArray(mediaLayers.size) { 1.0f }
        val scalesY = FloatArray(mediaLayers.size) { 1.0f }
        val blendModes = IntArray(mediaLayers.size) { 0 }
        val effectTypes = IntArray(mediaLayers.size) { 0 }
        val effectStrengths = FloatArray(mediaLayers.size) { 0.0f }

        // জিপিইউ দিয়ে ক্যানভাসে সরাসরি রেন্ডারিং
        GPURenderer.nativeRenderLayers(
            width, height, textureIds, opacities,
            offsetsX, offsetsY, rotations, scalesX, scalesY,
            blendModes, effectTypes, effectStrengths
        )

        // ফ্রেমের টাইমস্ট্যাম্প সেট করা এবং বাফার সোয়্যাপ করা (রেন্ডার মেমরি সরাসরি কোডেকে পুশ হবে)
        EGLExt.eglPresentationTimeANDROID(eglCore.getEGLDisplay(), renderSurface, (frameIndex * 1000000000L) / 30)
        renderSurface.swapBuffers()
        
        frameIndex++
    }

    // ৬. রিসোর্স রিলিজ মেমরি লিক এড়াতে
    for (id in textureIds) {
        GPURenderer.nativeDeleteTexture(id)
    }
    GPURenderer.nativeRelease()
    renderSurface.release()
    eglCore.release()
}
```

---

*এই সম্পূর্ণ কোড ডিজাইন প্ল্যানটি `/home/user/xxx/GPU_CODE_PLAN.md` ফাইলে লিখে সেভ করা হয়েছে যাতে এটি দেখে আপনি এবং আপনার টিম সরাসরি কোডিং শুরু করতে পারেন।*
