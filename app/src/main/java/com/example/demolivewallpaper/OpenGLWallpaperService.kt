package com.example.demolivewallpaper // Package name đã đổi

import android.content.Context
import android.opengl.GLSurfaceView
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlinx.coroutines.* // Import coroutines nếu bạn muốn dùng cho các tác vụ khác

// Lớp chính định nghĩa Live Wallpaper Service
class OpenGLWallpaperService : WallpaperService() {

    // Được gọi khi hệ thống cần tạo một instance mới của Wallpaper Engine.
    // Engine quản lý một surface và vòng đời của một wallpaper instance.
    override fun onCreateEngine(): Engine {
        return OpenGLEngine()
    }

    // Lớp nội bộ đại diện cho một instance đang chạy của wallpaper.
    // Nó xử lý các sự kiện vòng đời, tương tác người dùng và vẽ lên Surface.
    inner class OpenGLEngine : Engine() {

        // Khai báo lateinit vì chúng sẽ được khởi tạo chắc chắn trong onCreate
        private lateinit var glSurfaceView: WallpaperGLSurfaceView // View để vẽ OpenGL
        private lateinit var renderer: OpenGLRenderer // Đối tượng thực hiện logic vẽ OpenGL

        // Coroutine scope gắn với vòng đời của Engine (tùy chọn, hữu ích nếu có tác vụ nền)
        private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        // Được gọi khi Engine được tạo. Khởi tạo các thành phần OpenGL.
        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            // Khởi tạo GLSurfaceView tùy chỉnh
            glSurfaceView = WallpaperGLSurfaceView(this@OpenGLWallpaperService)
            // Khởi tạo Renderer
            renderer = OpenGLRenderer(this@OpenGLWallpaperService)

            // Cấu hình GLSurfaceView
            glSurfaceView.apply {
                setEGLContextClientVersion(2) // Yêu cầu sử dụng OpenGL ES 2.0
                setRenderer(renderer) // Gắn Renderer để xử lý việc vẽ
                // Đặt chế độ render liên tục để hiệu ứng động (ripple) được cập nhật
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        }

        // Được gọi khi trạng thái hiển thị của wallpaper thay đổi (ẩn/hiện).
        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            // Chỉ gọi onResume/onPause nếu glSurfaceView đã được khởi tạo
            if (::glSurfaceView.isInitialized) {
                if (visible) {
                    // Khi wallpaper hiển thị, tiếp tục vòng lặp render của GLSurfaceView
                    glSurfaceView.onResume()
                    // Nếu dùng RENDERMODE_WHEN_DIRTY, cần gọi requestRender() ở đây
                } else {
                    // Khi wallpaper bị che khuất, tạm dừng vòng lặp render để tiết kiệm pin
                    glSurfaceView.onPause()
                }
            }
        }

        // Được gọi khi Engine bị hủy. Dọn dẹp tài nguyên.
        override fun onDestroy() {
            super.onDestroy()
            // Hủy coroutine scope để tránh memory leak
            engineScope.cancel()
            // Giải phóng tài nguyên OpenGL nếu renderer đã được khởi tạo
            if (::renderer.isInitialized) {
                // Gọi hàm dọn dẹp tài nguyên trong Renderer
                // Nên thực hiện trên luồng GL nếu có thể, nhưng onDestroy có thể quá muộn
                renderer.releaseResources()
            }
            // Gọi hàm dọn dẹp tùy chỉnh của GLSurfaceView nếu có
            if (::glSurfaceView.isInitialized) {
                glSurfaceView.onDestroy()
            }
        }

        // Được gọi khi có sự kiện chạm trên màn hình wallpaper.
        override fun onTouchEvent(event: MotionEvent?) {
            // Sử dụng let để xử lý event có thể null một cách an toàn
            event?.let {
                // Chỉ xử lý khi người dùng bắt đầu chạm xuống (ACTION_DOWN)
                if (it.action == MotionEvent.ACTION_DOWN) {
                    val touchX = it.x // Lấy tọa độ X
                    val touchY = it.y // Lấy tọa độ Y
                    // Đảm bảo glSurfaceView đã sẵn sàng
                    if (::glSurfaceView.isInitialized) {
                        // Đưa sự kiện xử lý chạm vào hàng đợi của luồng OpenGL
                        // Lambda này sẽ được thực thi trên luồng GL một cách an toàn
                        glSurfaceView.queueEvent {
                            renderer.handleTouch(touchX, touchY)
                        }
                    }
                }
            }
            // Gọi hàm xử lý mặc định của lớp cha
            super.onTouchEvent(event)
        }

        // Lớp nội bộ kế thừa GLSurfaceView để tích hợp với WallpaperService.Engine.
        inner class WallpaperGLSurfaceView(context: Context) : GLSurfaceView(context) {
            // Ghi đè getHolder để trả về SurfaceHolder của Engine cha.
            // Điều này cho phép GLSurfaceView vẽ lên đúng surface của wallpaper.
            override fun getHolder(): SurfaceHolder = this@OpenGLEngine.surfaceHolder

            // Hàm dọn dẹp tùy chỉnh (nếu cần) được gọi từ onDestroy của Engine.
            fun onDestroy() {
                // Logic dọn dẹp đặc biệt cho View này nếu cần.
                // super.onDetachedFromWindow() thường được quản lý bởi vòng đời của Engine.
            }
        }
    }
}
