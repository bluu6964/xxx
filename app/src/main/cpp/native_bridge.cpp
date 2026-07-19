#include <jni.h>
#include <android/log.h>
#include <string>
#include <cstring>
#include <vector>
#include "video_export_engine.h"
#include "native_compositor.h"
#include "yuv_convert.h"

#define LOG_TAG "CPlusEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace motion;

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_example_render_NativeRenderer_getEngineVersion(
    JNIEnv* env, jobject thiz
) {
    return env->NewStringUTF("3.0.0-max-cpp-option-c");
}

JNIEXPORT jboolean JNICALL
Java_com_example_render_NativeRenderer_runFullPipeline(
    JNIEnv* env, jobject thiz,
    jstring outputPath, jstring projectData,
    jint width, jint height,
    jint frameRate, jint bitrate
) {
    const char* path = env->GetStringUTFChars(outputPath, 0);
    const char* proj = env->GetStringUTFChars(projectData, 0);
    ExportConfig cfg;
    cfg.width = width; cfg.height = height;
    cfg.frameRate = frameRate; cfg.bitrate = bitrate;
    bool result = exportVideoPipeline(path, proj ? proj : "", cfg);
    env->ReleaseStringUTFChars(outputPath, path);
    if (proj) env->ReleaseStringUTFChars(projectData, proj);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_render_NativeRenderer_compositeFrame(
    JNIEnv* env, jobject thiz,
    jobject outBuffer, jint w, jint h,
    jobjectArray layers, jfloatArray transforms, jfloatArray alphas
) {
    uint8_t* out = static_cast<uint8_t*>(env->GetDirectBufferAddress(outBuffer));
    if (!out || w <= 0 || h <= 0) return JNI_FALSE;

    jsize layerCount = env->GetArrayLength(layers);
    std::vector<uint8_t*> layerPtrs;
    std::vector<LayerTransform> transformsVec;
    layerPtrs.reserve(layerCount);
    transformsVec.reserve(layerCount);

    for (jsize i = 0; i < layerCount; ++i) {
        jobject layerObj = env->GetObjectArrayElement(layers, i);
        uint8_t* ptr = static_cast<uint8_t*>(env->GetDirectBufferAddress(layerObj));
        layerPtrs.push_back(ptr ? ptr : nullptr);
        LayerTransform t{}; transformsVec.push_back(t);
    }

    compositeFrame(out, w, h, layerPtrs.data(), transformsVec.data(), layerCount);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_render_NativeRenderer_nativeBitmapToYuv420(
    JNIEnv* env, jobject thiz,
    jobject bitmapBuf, jobject yuvBuf, jint w, jint h
) {
    uint8_t* bmp = static_cast<uint8_t*>(env->GetDirectBufferAddress(bitmapBuf));
    uint8_t* yuv = static_cast<uint8_t*>(env->GetDirectBufferAddress(yuvBuf));
    if (!bmp || !yuv) return JNI_FALSE;
    return bitmapToYuv420(bmp, yuv, w, h) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
