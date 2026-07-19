#pragma once
#include <cstdint>
#include <cstddef>
#include <string>

namespace motion {

/**
 * Maximum C++ Engine — Option C.
 * C++ manages full pipeline; Kotlin is thin wrapper.
 * JNI callbacks to Kotlin for Android MediaCodec / MediaMuxer.
 */

struct ExportConfig {
    int width = 1920;
    int height = 1080;
    int frameRate = 30;
    int bitrate = 50_000_000;
    int iframeInterval = 1;
    int totalFrames = 30;
};

/**
 * Full C++ export pipeline with JNI encoding callbacks.
 */
bool exportVideoPipeline(
    const char* outputPath,
    const char* projectData,
    const ExportConfig& config
);

/**
 * Initialize native resources.
 */
bool initEngine();
void shutdownEngine();
const char* getEngineVersion();

/**
 * JNI callback: Kotlin provides encoder instance reference.
 */
extern "C" {
    // These are called from Kotlin side to register encoder callbacks
    void registerEncoderCallback(void* encoderRef);
    void registerMuxerCallback(void* muxerRef);
}

} // namespace motion
