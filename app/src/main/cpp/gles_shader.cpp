#include "gles_shader.h"
#include <android/log.h>
#include <vector>

#define LOG_TAG "GLESShader"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

GLuint GLESShader::compileShader(GLenum type, const std::string& source) {
    GLuint shader = glCreateShader(type);
    const char* src = source.c_str();
    glShaderSource(shader, 1, &src, nullptr);
    glCompileShader(shader);

    GLint compiled = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        GLint infoLen = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 0) {
            std::vector<char> infoLog(infoLen);
            glGetShaderInfoLog(shader, infoLen, nullptr, infoLog.data());
            LOGE("Error compiling shader:\n%s\nShader Source:\n%s", infoLog.data(), src);
        }
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

bool GLESShader::loadFromSources(const std::string& vertexSource, const std::string& fragmentSource) {
    release();

    GLuint vertexShader = compileShader(GL_VERTEX_SHADER, vertexSource);
    if (vertexShader == 0) return false;

    GLuint fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource);
    if (fragmentShader == 0) {
        glDeleteShader(vertexShader);
        return false;
    }

    mProgramId = glCreateProgram();
    glAttachShader(mProgramId, vertexShader);
    glAttachShader(mProgramId, fragmentShader);
    glLinkProgram(mProgramId);

    GLint linked = 0;
    glGetProgramiv(mProgramId, GL_LINK_STATUS, &linked);
    if (!linked) {
        GLint infoLen = 0;
        glGetProgramiv(mProgramId, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 0) {
            std::vector<char> infoLog(infoLen);
            glGetProgramInfoLog(mProgramId, infoLen, nullptr, infoLog.data());
            LOGE("Error linking program:\n%s", infoLog.data());
        }
        release();
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        return false;
    }

    glDeleteShader(vertexShader);
    glDeleteShader(fragmentShader);
    return true;
}

void GLESShader::release() {
    if (mProgramId != 0) {
        glDeleteProgram(mProgramId);
        mProgramId = 0;
    }
}

void GLESShader::setUniform1f(const std::string& name, float value) const {
    GLint location = glGetUniformLocation(mProgramId, name.c_str());
    if (location != -1) {
        glUniform1f(location, value);
    }
}

void GLESShader::setUniform1i(const std::string& name, int value) const {
    GLint location = glGetUniformLocation(mProgramId, name.c_str());
    if (location != -1) {
        glUniform1i(location, value);
    }
}

void GLESShader::setUniformMatrix4fv(const std::string& name, const float* matrix) const {
    GLint location = glGetUniformLocation(mProgramId, name.c_str());
    if (location != -1) {
        glUniformMatrix4fv(location, 1, GL_FALSE, matrix);
    }
}
