package com.example.render

import android.opengl.EGL14
import android.opengl.EGLSurface
import android.view.Surface

class WindowSurface(private val eglCore: EGLCore, val surface: Surface) {
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    init {
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(
            eglCore.getEGLDisplay(),
            eglCore.getEGLConfig(),
            surface,
            surfaceAttribs,
            0
        )
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("Failed to create EGL window surface")
        }
    }

    fun makeCurrent() {
        eglCore.makeCurrent(eglSurface)
    }

    fun swapBuffers(): Boolean {
        return eglCore.swapBuffers(eglSurface)
    }

    fun release() {
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(eglCore.getEGLDisplay(), eglSurface)
            eglSurface = EGL14.EGL_NO_SURFACE
        }
        surface.release()
    }
}
