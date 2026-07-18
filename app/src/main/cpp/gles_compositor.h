#pragma once
#include <GLES3/gl3.h>
#include <vector>
#include "gles_shader.h"

struct GLESLayer {
    GLuint textureId = 0;
    float opacity = 1.0f;
    float offsetX = 0.0f;
    float offsetY = 0.0f;
    float rotationDegrees = 0.0f;
    float scaleX = 1.0f;
    float scaleY = 1.0f;
    int blendMode = 0;
    int effectType = 0;
    float effectStrength = 0.0f;
};

class GLESCompositor {
private:
    GLESShader mDefaultShader;
    GLuint mVao = 0;
    GLuint mVboVertices = 0;
    GLuint mVboTexCoords = 0;

    void setupGeometry();
    void computeMVPMatrix(float* matrix, float outW, float outH, const GLESLayer& layer);

public:
    GLESCompositor() = default;
    ~GLESCompositor() { release(); }

    bool init();
    void renderFrame(int outWidth, int outHeight, const std::vector<GLESLayer>& layers);
    void release();
};
