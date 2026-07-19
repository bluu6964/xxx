#include "native_compositor.h"
#include <cmath>
#include <algorithm>

namespace motion {

constexpr float PI = 3.14159265358979323846f;

void compositeFrame(
    uint8_t* outBuffer,
    int width,
    int height,
    uint8_t* const* layers,
    const LayerTransform* transforms,
    size_t layerCount
) {
    if (!outBuffer || !layers || layerCount == 0 || width <= 0 || height <= 0) return;

    const int totalPixels = width * height;
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int outIdx = (y * width + x) * 4;
            float r = 0.0f, g = 0.0f, b = 0.0f, a = 0.0f;

            for (size_t i = 0; i < layerCount; ++i) {
                uint8_t* layer = layers[i];
                if (!layer) continue;
                const LayerTransform& t = transforms ? transforms[i] : LayerTransform{};

                float cosR = std::cos(t.rotationDeg * PI / 180.0f);
                float sinR = std::sin(t.rotationDeg * PI / 180.0f);
                float srcX = (x * t.scaleX - t.offsetX) * cosR - (y * t.scaleY - t.offsetY) * sinR;
                float srcY = (x * t.scaleX - t.offsetX) * sinR + (y * t.scaleY - t.offsetY) * cosR;

                int srcXi = static_cast<int>(srcX);
                int srcYi = static_cast<int>(srcY);
                if (srcXi < 0 || srcXi >= width || srcYi < 0 || srcYi >= height) continue;

                int srcIdx = (srcYi * width + srcXi) * 4;
                float layerA = (layer[srcIdx + 3] / 255.0f) * t.alpha;
                float lr = layer[srcIdx] / 255.0f;
                float lg = layer[srcIdx + 1] / 255.0f;
                float lb = layer[srcIdx + 2] / 255.0f;

                float overA = layerA * (1.0f - a);
                r = lr * overA + r * (1.0f - overA);
                g = lg * overA + g * (1.0f - overA);
                b = lb * overA + b * (1.0f - overA);
                a = layerA + a * (1.0f - layerA);
            }

            outBuffer[outIdx]     = static_cast<uint8_t>(std::min(r, 1.0f) * 255.0f);
            outBuffer[outIdx + 1] = static_cast<uint8_t>(std::min(g, 1.0f) * 255.0f);
            outBuffer[outIdx + 2] = static_cast<uint8_t>(std::min(b, 1.0f) * 255.0f);
            outBuffer[outIdx + 3] = static_cast<uint8_t>(std::min(a, 1.0f) * 255.0f);
        }
    }
}

} // namespace motion
