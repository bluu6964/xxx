#include "gles_compositor.h"
#include <cmath>
#include <android/log.h>

#define LOG_TAG "GLESCompositor"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

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
        const NativeLayerBitmap& layer,
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
uniform int uEffectType;          // 0 = None, 1 = Brightness/Contrast, 2 = Saturation, 3 = Invert, 4 = Vignette, 5 = Pixelate, 6 = Hue Shift/Warmth
uniform float uEffectStrength;    // Dynamic slider intensity [0.0 - 1.0]

// Helper functions to shift color parameters on GPU
vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    vec2 uv = vTexCoord;
    
    // Effect 5: Pixelate (Quantize UV mapping)
    if (uEffectType == 5) {
        float size = 15.0 + (1.0 - uEffectStrength) * 150.0;
        uv = floor(uv * size) / size;
    }
    
    vec4 texColor = texture(uTexture, uv);
    if (texColor.a == 0.0) {
        discard;
    }

    // Un-premultiply alpha to apply precise color filters on GLES GPU
    vec3 rgb = texColor.rgb / texColor.a;
    
    // Effect 1: Brightness & Contrast
    if (uEffectType == 1) {
        rgb += (uEffectStrength - 0.5) * 0.4;
        rgb = (rgb - 0.5) * (uEffectStrength * 1.5 + 0.25) + 0.5;
    }
    // Effect 2: Saturation
    else if (uEffectType == 2) {
        float gray = dot(rgb, vec3(0.299, 0.587, 0.114));
        rgb = mix(vec3(gray), rgb, uEffectStrength * 2.0);
    }
    // Effect 3: Invert
    else if (uEffectType == 3) {
        rgb = 1.0 - rgb;
    }
    // Effect 4: Vignette (darken edges)
    else if (uEffectType == 4) {
        float dist = distance(uv, vec2(0.5, 0.5));
        float vignette = smoothstep(0.8, 0.5 - uEffectStrength * 0.3, dist);
        rgb *= vignette;
    }
    // Effect 6: Hue Shift / Warmth
    else if (uEffectType == 6) {
        vec3 hsv = rgb2hsv(rgb);
        hsv.x += uEffectStrength * 0.2;
        rgb = hsv2rgb(hsv);
    }

    // Re-premultiply alpha for correct hardware compositing
    rgb = clamp(rgb, 0.0, 1.0);
    texColor.rgb = rgb * texColor.a * uOpacity;
    texColor.a *= uOpacity;
    
    fragColor = texColor;
}
)";

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
