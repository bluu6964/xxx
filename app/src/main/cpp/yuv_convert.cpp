#include "yuv_convert.h"
#include <cmath>
#include <algorithm>

namespace motion {

bool bitmapToYuv420(const uint8_t* bitmap, uint8_t* yuv, int width, int height) {
    if (!bitmap || !yuv || width <= 0 || height <= 0) return false;
    int ySize = width * height;
    int uvSize = (width / 2) * (height / 2);

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int idx = (y * width + x) * 4;
            float r = bitmap[idx] / 255.0f;
            float g = bitmap[idx + 1] / 255.0f;
            float b = bitmap[idx + 2] / 255.0f;
            float yVal = 0.299f * r + 0.587f * g + 0.114f * b;
            yuv[y * width + x] = static_cast<uint8_t>(std::min(yVal, 1.0f) * 255.0f);
        }
    }

    for (int y = 0; y < height / 2; ++y) {
        for (int x = 0; x < width / 2; ++x) {
            float uSum = 0.0f, vSum = 0.0f;
            int count = 0;
            for (int dy = 0; dy < 2; ++dy) {
                for (int dx = 0; dx < 2; ++dx) {
                    int px = x * 2 + dx;
                    int py = y * 2 + dy;
                    if (px >= width || py >= height) continue;
                    int idx = (py * width + px) * 4;
                    float r = bitmap[idx] / 255.0f;
                    float g = bitmap[idx + 1] / 255.0f;
                    float b = bitmap[idx + 2] / 255.0f;
                    float yVal = 0.299f * r + 0.587f * g + 0.114f * b;
                    float uVal = std::clamp(0.492f * (b - yVal) + 0.5f, 0.0f, 1.0f);
                    float vVal = std::clamp(0.877f * (r - yVal) + 0.5f, 0.0f, 1.0f);
                    uSum += uVal;
                    vSum += vVal;
                    count++;
                }
            }
            int chromaIdx = y * (width / 2) + x;
            yuv[ySize + chromaIdx] = static_cast<uint8_t>((vSum / count) * 255.0f);
            yuv[ySize + uvSize + chromaIdx] = static_cast<uint8_t>((uSum / count) * 255.0f);
        }
    }
    return true;
}

} // namespace motion
