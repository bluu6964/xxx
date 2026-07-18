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
