#include "native_compositor.h"
#include <cmath>
#include <algorithm>
#include <thread>
#include <vector>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

namespace {

// Samples a source RGBA buffer with bilinear filtering at fractional (sx, sy).
// Returns straight (non-premultiplied) RGBA in out[0..3]. Out-of-bounds reads
// return fully transparent black.
inline void sampleBilinear(const uint8_t* src, int srcW, int srcH, float sx, float sy, uint8_t out[4]) {
    // Out-of-bounds check: only reject pixels truly outside the source.
    if (sx < -0.5f || sy < -0.5f || sx > srcW - 0.5f || sy > srcH - 0.5f) {
        out[0] = out[1] = out[2] = out[3] = 0;
        return;
    }
    int x0 = std::clamp((int) std::floor(sx), 0, srcW - 1);
    int y0 = std::clamp((int) std::floor(sy), 0, srcH - 1);
    int x1 = std::min(x0 + 1, srcW - 1);
    int y1 = std::min(y0 + 1, srcH - 1);
    float fx = sx - x0;
    float fy = sy - y0;

    auto at = [&](int x, int y, int c) -> float {
        return src[(y * srcW + x) * 4 + c];
    };

    for (int c = 0; c < 4; c++) {
        float top = at(x0, y0, c) * (1 - fx) + at(x1, y0, c) * fx;
        float bot = at(x0, y1, c) * (1 - fx) + at(x1, y1, c) * fx;
        out[c] = (uint8_t) std::clamp(top * (1 - fy) + bot * fy, 0.0f, 255.0f);
    }
}

// Composites a specific slice (horizontal range of rows) of the layer bounding box
void compositeLayerSlice(
        uint8_t* outRgba,
        int outWidth,
        int outHeight,
        NativeLayerBitmap layer,
        float cosR,
        float sinR,
        float invScaleX,
        float invScaleY,
        float destCenterX,
        float destCenterY,
        float srcCenterX,
        float srcCenterY,
        int minX,
        int maxX,
        int minY,
        int maxY
) {
    for (int y = minY; y <= maxY; y++) {
        for (int x = minX; x <= maxX; x++) {
            // Inverse-map destination pixel back into source space:
            // undo offset -> undo rotation -> undo scale.
            float dx = (x + 0.5f - destCenterX);
            float dy = (y + 0.5f - destCenterY);
            float rx = dx * cosR - dy * sinR;
            float ry = dx * sinR + dy * cosR;
            float sx = rx * invScaleX + srcCenterX;
            float sy = ry * invScaleY + srcCenterY;

            uint8_t sample[4];
            sampleBilinear(layer.rgbaPixels, layer.width, layer.height, sx, sy, sample);
            // Android Bitmaps store PREMULTIPLIED alpha
            float srcAlpha = (sample[3] / 255.0f) * layer.opacity;
            if (srcAlpha <= 0.0f) continue;

            uint8_t* dst = outRgba + (y * outWidth + x) * 4;
            // Premultiplied-alpha "over" compositing (Porter-Duff).
            float srcPremulR = (sample[0] / 255.0f) * layer.opacity;
            float srcPremulG = (sample[1] / 255.0f) * layer.opacity;
            float srcPremulB = (sample[2] / 255.0f) * layer.opacity;
            float dstPremulR = dst[0] / 255.0f;
            float dstPremulG = dst[1] / 255.0f;
            float dstPremulB = dst[2] / 255.0f;
            float dstAlpha = dst[3] / 255.0f;
            float oneMinusSrcA = 1.0f - srcAlpha;

            dst[0] = (uint8_t) std::clamp((srcPremulR + dstPremulR * oneMinusSrcA) * 255.0f, 0.0f, 255.0f);
            dst[1] = (uint8_t) std::clamp((srcPremulG + dstPremulG * oneMinusSrcA) * 255.0f, 0.0f, 255.0f);
            dst[2] = (uint8_t) std::clamp((srcPremulB + dstPremulB * oneMinusSrcA) * 255.0f, 0.0f, 255.0f);
            float outAlpha = srcAlpha + dstAlpha * oneMinusSrcA;
            dst[3] = (uint8_t) std::clamp(outAlpha * 255.0f, 0.0f, 255.0f);
        }
    }
}

} // namespace

void compositeFrame(
        uint8_t* outRgba,
        int outWidth,
        int outHeight,
        const std::vector<NativeLayerBitmap>& layers
) {
    // Detect core count to leverage all available cores
    unsigned int numThreads = std::thread::hardware_concurrency();
    if (numThreads == 0) numThreads = 4;
    if (numThreads > 8) numThreads = 8; // Prevent too many context switches

    for (const auto& layer : layers) {
        if (layer.rgbaPixels == nullptr || layer.width <= 0 || layer.height <= 0) continue;
        if (layer.opacity <= 0.0f) continue;

        const float cosR = std::cos(-layer.transform.rotationDegrees * (float) M_PI / 180.0f);
        const float sinR = std::sin(-layer.transform.rotationDegrees * (float) M_PI / 180.0f);
        const float invScaleX = (layer.transform.scaleX != 0.0f) ? 1.0f / layer.transform.scaleX : 1.0f;
        const float invScaleY = (layer.transform.scaleY != 0.0f) ? 1.0f / layer.transform.scaleY : 1.0f;

        // Center mapping
        const float destCenterX = outWidth / 2.0f + layer.transform.offsetX;
        const float destCenterY = outHeight / 2.0f + layer.transform.offsetY;
        const float srcCenterX = layer.width / 2.0f;
        const float srcCenterY = layer.height / 2.0f;

        // Bounding box mapping
        float halfDiag = 0.5f * std::sqrt(
                (float) layer.width * layer.width * layer.transform.scaleX * layer.transform.scaleX +
                (float) layer.height * layer.height * layer.transform.scaleY * layer.transform.scaleY
        );
        int minX = std::max(0, (int) std::floor(destCenterX - halfDiag));
        int maxX = std::min(outWidth - 1, (int) std::ceil(destCenterX + halfDiag));
        int minY = std::max(0, (int) std::floor(destCenterY - halfDiag));
        int maxY = std::min(outHeight - 1, (int) std::ceil(destCenterY + halfDiag));

        int totalYLines = maxY - minY + 1;
        if (totalYLines <= 0) continue;

        // Single-threaded path for small bounds to avoid thread overhead
        if (totalYLines < 32 || numThreads == 1) {
            compositeLayerSlice(
                outRgba, outWidth, outHeight, layer,
                cosR, sinR, invScaleX, invScaleY,
                destCenterX, destCenterY, srcCenterX, srcCenterY,
                minX, maxX, minY, maxY
            );
        } else {
            // Multithreaded parallel composite over distinct row slices
            int linesPerThread = (totalYLines + numThreads - 1) / numThreads;
            std::vector<std::thread> threads;
            threads.reserve(numThreads);

            for (unsigned int t = 0; t < numThreads; t++) {
                int threadMinY = minY + t * linesPerThread;
                if (threadMinY > maxY) break;
                int threadMaxY = std::min(threadMinY + linesPerThread - 1, maxY);

                threads.emplace_back(
                    compositeLayerSlice,
                    outRgba, outWidth, outHeight, layer,
                    cosR, sinR, invScaleX, invScaleY,
                    destCenterX, destCenterY, srcCenterX, srcCenterY,
                    minX, maxX, threadMinY, threadMaxY
                );
            }

            for (auto& thread : threads) {
                if (thread.joinable()) {
                    thread.join();
                }
            }
        }
    }
}
