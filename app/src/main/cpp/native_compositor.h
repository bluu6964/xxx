#pragma once
#include <cstdint>
#include <cstddef>

namespace motion {

struct LayerTransform {
    float offsetX = 0.0f;
    float offsetY = 0.0f;
    float rotationDeg = 0.0f;
    float scaleX = 1.0f;
    float scaleY = 1.0f;
    float alpha = 1.0f;
};

void compositeFrame(
    uint8_t* outBuffer,
    int width,
    int height,
    uint8_t* const* layers,
    const LayerTransform* transforms,
    size_t layerCount
);

} // namespace motion
