#pragma once
#include <cstdint>
#include <cstddef>

namespace motion {

bool bitmapToYuv420(const uint8_t* bitmap, uint8_t* yuv, int width, int height);

} // namespace motion
