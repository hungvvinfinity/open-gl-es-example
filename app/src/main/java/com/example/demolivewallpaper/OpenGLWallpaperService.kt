package com.example.demolivewallpaper // Thay bằng package của bạn

import android.content.Context
import android.opengl.GLSurfaceView
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlinx.coroutines.*

class OpenGLWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return OpenGLEngine()
    }

    inner class OpenGLEngine : Engine() {

        private lateinit var glSurfaceView: WallpaperGLSurfaceView
        private lateinit var renderer: OpenGLRenderer
        private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            glSurfaceView = WallpaperGLSurfaceView(this@OpenGLWallpaperService)
            renderer = OpenGLRenderer(this@OpenGLWallpaperService)

            glSurfaceView.apply {
                setEGLContextClientVersion(2)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (::glSurfaceView.isInitialized) {
                if (visible) {
                    glSurfaceView.onResume()
                } else {
                    glSurfaceView.onPause()
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            engineScope.cancel()
            if (::glSurfaceView.isInitialized) {
                glSurfaceView.onDestroy()
            }
        }

        override fun onTouchEvent(event: MotionEvent?) {
            event?.let {
                if (it.action == MotionEvent.ACTION_DOWN) {
                    val touchX = it.x
                    val touchY = it.y
                    if (::glSurfaceView.isInitialized) {
                        glSurfaceView.queueEvent {
                            renderer.handleTouch(touchX, touchY)
                        }
                    }
                }
            }
            super.onTouchEvent(event)
        }

        inner class WallpaperGLSurfaceView(context: Context) : GLSurfaceView(context) {
            override fun getHolder(): SurfaceHolder = this@OpenGLEngine.surfaceHolder

            fun onDestroy() {
                // Optional: Call GLSurfaceView's cleanup if needed, but Engine lifecycle manages surface
                // super.onDetachedFromWindow() - Usually not called directly here
            }
        }
    }
}