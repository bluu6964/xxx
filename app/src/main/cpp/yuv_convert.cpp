#include "yuv_convert.h"
#include <algorithm>
#include <thread>
#include <vector>

namespace {
inline uint8_t clampByte(int v) {
    return (uint8_t) std::clamp(v, 0, 255);
}

void rgbaToYuv420Slice(
        const uint8_t* rgba,
        int width,
        int height,
        uint8_t* yPlane,
        uint8_t* uPlane,
        uint8_t* vPlane,
        int chromaStride,
        int startRow,
        int endRow,
        bool semiPlanar
) {
    for (int j = startRow; j < endRow; j++) {
        for (int i = 0; i < width; i++) {
            const uint8_t* px = rgba + (j * width + i) * 4;
            int r = px[0], g = px[1], b = px[2];

            // BT.601 full-range RGB->YUV, matching the coefficients used by
            // the previous Kotlin bitmapToYuv implementation so exported
            // color matches exactly (no visible shift from the native port).
            int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
            yPlane[j * width + i] = clampByte(y);

            // Chroma is subsampled 2x2 — only compute/write it once per
            // 2x2 block, using the top-left pixel of each block.
            if ((i % 2 == 0) && (j % 2 == 0)) {
                int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                int chromaIndex = (j / 2) * (width / 2) * chromaStride + (i / 2) * chromaStride;
                uPlane[chromaIndex] = clampByte(u);
                vPlane[chromaIndex] = clampByte(v);
            }
        }
    }
}
}

void rgbaToYuv420(
        const uint8_t* rgba,
        int width,
        int height,
        uint8_t* outYuv,
        bool semiPlanar
) {
    uint8_t* yPlane = outYuv;
    const int frameSize = width * height;
    uint8_t* uPlane = outYuv + frameSize;
    uint8_t* vPlane = semiPlanar ? (uPlane + 1) : (uPlane + frameSize / 4);
    const int chromaStride = semiPlanar ? 2 : 1;

    // Detect the number of hardware threads/cores available on the device
    unsigned int numThreads = std::thread::hardware_concurrency();
    if (numThreads == 0) numThreads = 4;
    // Cap at a reasonable limit to prevent excessive context switching overhead
    if (numThreads > 8) numThreads = 8;

    // Each thread must process an even number of rows to respect 2x2 chroma subsampling boundaries
    int rowsPerThread = (height / numThreads);
    rowsPerThread = ((rowsPerThread + 1) / 2) * 2; // Round up to nearest even number

    std::vector<std::thread> threads;
    threads.reserve(numThreads);

    for (unsigned int t = 0; t < numThreads; t++) {
        int startRow = t * rowsPerThread;
        if (startRow >= height) break;
        int endRow = std::min(startRow + rowsPerThread, height);
        // Ensure last thread covers up to the end of the frame
        if (t == numThreads - 1) {
            endRow = height;
        }

        threads.emplace_back(
            rgbaToYuv420Slice,
            rgba, width, height,
            yPlane, uPlane, vPlane,
            chromaStride,
            startRow, endRow,
            semiPlanar
        );
    }

    for (auto& thread : threads) {
        if (thread.joinable()) {
            thread.join();
        }
    }
}
