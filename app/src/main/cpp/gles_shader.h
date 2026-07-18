#pragma once
#include <GLES3/gl3.h>
#include <string>

class GLESShader {
private:
    GLuint mProgramId = 0;
    GLuint compileShader(GLenum type, const std::string& source);

public:
    GLESShader() = default;
    ~GLESShader() { release(); }

    bool loadFromSources(const std::string& vertexSource, const std::string& fragmentSource);
    void use() const { glUseProgram(mProgramId); }
    void release();

    // Uniform setters
    void setUniform1f(const std::string& name, float value) const;
    void setUniform1i(const std::string& name, int value) const;
    void setUniformMatrix4fv(const std::string& name, const float* matrix) const;
};
