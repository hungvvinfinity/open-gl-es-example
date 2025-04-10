package com.example.demolivewallpaper // Package name đã đổi

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Lớp triển khai logic vẽ OpenGL ES
class OpenGLRenderer(private val context: Context) : GLSurfaceView.Renderer {

    // Companion object chứa các hằng số và hàm tiện ích tĩnh
    companion object {
        private const val TAG = "OpenGLRenderer" // Tag dùng cho Logcat
        private const val BYTES_PER_FLOAT = 4 // Số byte cho mỗi giá trị float

        // Hằng số định nghĩa số thành phần trong dữ liệu đỉnh
        private const val POSITION_COMPONENT_COUNT_XY = 2 // X, Y cho background
        private const val POSITION_COMPONENT_COUNT_XYZ = 3 // X, Y, Z cho ripple
        private const val TEX_COORD_COMPONENT_COUNT = 2 // S, T (UV) cho texture background
        private const val RIPPLE_SEGMENTS = 1000 // Số đoạn thẳng để vẽ đường tròn ripple (độ mịn)

        // --- Đường dẫn đến file shader trong thư mục assets ---
        private const val RIPPLE_VERTEX_SHADER_PATH = "shaders/vertex_shader.glsl"
        private const val RIPPLE_FRAGMENT_SHADER_PATH = "shaders/fragment_shader.glsl"
        private const val BACKGROUND_VERTEX_SHADER_PATH = "shaders/background_vertex_shader.glsl"
        private const val BACKGROUND_FRAGMENT_SHADER_PATH =
            "shaders/background_fragment_shader.glsl"

        // --- Các hàm tiện ích ---

        /**
         * Đọc nội dung file text từ thư mục assets.
         * @param context Context ứng dụng để truy cập assets.
         * @param filePath Đường dẫn tương đối của file trong thư mục assets.
         * @return Nội dung file dưới dạng String, hoặc null nếu có lỗi.
         */
        private fun readTextFileFromAssets(context: Context, filePath: String): String? {
            return try {
                // Mở InputStream -> đọc bằng BufferedReader -> lấy toàn bộ text
                // Khối 'use' đảm bảo stream và reader được đóng tự động
                context.assets.open(filePath).bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                Log.e(TAG, "Lỗi khi đọc file asset: $filePath", e)
                null // Trả về null nếu lỗi
            }
        }

        /**
         * Tải và biên dịch một shader từ mã nguồn GLSL.
         * @param type Loại shader (GLES20.GL_VERTEX_SHADER hoặc GLES20.GL_FRAGMENT_SHADER).
         * @param shaderCode Mã nguồn GLSL.
         * @return Handle (ID) của shader đã biên dịch (số nguyên dương), hoặc 0 nếu thất bại.
         */
        private fun loadShader(type: Int, shaderCode: String): Int {
            // Kiểm tra mã nguồn có rỗng không
            if (shaderCode.isBlank()) {
                Log.e(TAG, "Mã nguồn shader rỗng cho loại $type.")
                return 0
            }
            // Tạo đối tượng shader mới
            val shader = GLES20.glCreateShader(type)
            if (shader == 0) { // Kiểm tra nếu tạo shader thất bại
                Log.e(TAG, "Không thể tạo shader loại $type")
                checkGLError("glCreateShader loại $type") // Kiểm tra lỗi OpenGL cụ thể
                return 0
            }
            // Gắn mã nguồn vào đối tượng shader
            GLES20.glShaderSource(shader, shaderCode)
            // Biên dịch shader
            GLES20.glCompileShader(shader)
            // Kiểm tra trạng thái biên dịch
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) { // Nếu biên dịch lỗi
                val shaderType = if (type == GLES20.GL_VERTEX_SHADER) "Vertex" else "Fragment"
                Log.e(TAG, "Không thể biên dịch $shaderType shader:")
                Log.e(
                    TAG,
                    GLES20.glGetShaderInfoLog(shader)
                ) // In log lỗi chi tiết từ trình biên dịch GLSL
                GLES20.glDeleteShader(shader) // Xóa shader bị lỗi
                return 0 // Trả về 0 báo hiệu thất bại
            }
            // Trả về handle của shader đã biên dịch thành công
            return shader
        }

        /**
         * Kiểm tra trạng thái liên kết (link) của một shader program.
         * Xóa program nếu liên kết thất bại.
         * @param program Handle (ID) của shader program cần kiểm tra.
         * @return true nếu liên kết thành công, false nếu thất bại.
         */
        private fun checkProgramLinkStatus(program: Int): Boolean {
            // Bỏ qua nếu program handle không hợp lệ
            if (program == 0) return false
            // Lấy trạng thái liên kết
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) { // Nếu liên kết lỗi
                Log.e(TAG, "Không thể liên kết program: ")
                Log.e(TAG, GLES20.glGetProgramInfoLog(program)) // In log lỗi chi tiết
                GLES20.glDeleteProgram(program) // Xóa program bị lỗi
                return false // Trả về false báo hiệu thất bại
            }
            // Liên kết thành công
            return true
        }

        /**
         * Tạo một FloatBuffer từ một mảng FloatArray của Kotlin.
         * FloatBuffer dùng để truyền dữ liệu đỉnh hiệu quả cho OpenGL.
         * @param data Mảng FloatArray chứa dữ liệu đỉnh.
         * @return FloatBuffer chứa dữ liệu, đã sẵn sàng để đọc (position 0).
         */
        private fun createFloatBuffer(data: FloatArray): FloatBuffer {
            // Cấp phát ByteBuffer với kích thước phù hợp (số float * 4 byte/float)
            // Dùng allocateDirect để cấp phát bộ nhớ gốc (native memory), hiệu quả hơn cho OpenGL
            return ByteBuffer.allocateDirect(data.size * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()) // Đặt thứ tự byte theo hệ thống gốc
                .asFloatBuffer() // Chuyển ByteBuffer thành FloatBuffer
                .put(data) // Sao chép dữ liệu từ mảng vào buffer
                .position(0) as FloatBuffer // Đặt vị trí đọc/ghi về đầu buffer
        }

        /**
         * Kiểm tra lỗi OpenGL và ghi log nếu có.
         * @param op Chuỗi mô tả thao tác vừa thực hiện trước khi kiểm tra lỗi.
         */
        private fun checkGLError(op: String) {
            var error: Int
            // Vòng lặp để lấy hết các lỗi có thể đang chờ trong hàng đợi
            while (GLES20.glGetError().also { error = it } != GLES20.GL_NO_ERROR) {
                // Ghi log lỗi với mã lỗi và mô tả lỗi (nếu có)
                Log.e(TAG, "$op: Lỗi OpenGL ${error} - ${GLUtils.getEGLErrorString(error)}")
                // Tùy chọn: Ném exception trong bản debug để dừng ứng dụng ngay khi có lỗi
                // throw RuntimeException("$op: glError $error")
            }
        }

        /**
         * Tải một texture từ tài nguyên drawable.
         * @param context Context ứng dụng.
         * @param resourceId ID của tài nguyên drawable (ví dụ: R.drawable.my_texture).
         * @return ID (handle) của texture OpenGL đã tạo (số nguyên dương), hoặc 0 nếu thất bại.
         */
        private fun loadTexture(context: Context, resourceId: Int): Int {
            // Tạo một handle (ID) cho texture mới
            val textureHandle = IntArray(1)
            GLES20.glGenTextures(1, textureHandle, 0)
            checkGLError("glGenTextures") // Kiểm tra lỗi sau khi tạo handle

            if (textureHandle[0] != 0) { // Nếu tạo handle thành công
                // Cấu hình BitmapFactory để không scale ảnh theo mật độ màn hình
                val options = BitmapFactory.Options().apply {
                    inScaled = false
                }
                // Giải mã tài nguyên ảnh thành đối tượng Bitmap
                val bitmap = try {
                    BitmapFactory.decodeResource(context.resources, resourceId, options)
                } catch (e: OutOfMemoryError) { // Bắt lỗi nếu thiếu bộ nhớ khi load ảnh lớn
                    Log.e(TAG, "Hết bộ nhớ khi tải texture resource $resourceId", e)
                    null // Trả về null nếu lỗi OOM
                }

                // Kiểm tra nếu bitmap không load được
                if (bitmap == null) {
                    Log.e(TAG, "Không thể giải mã resource $resourceId thành bitmap")
                    GLES20.glDeleteTextures(1, textureHandle, 0) // Xóa handle texture đã tạo
                    return 0 // Trả về 0 báo hiệu thất bại
                }

                // Bind (kích hoạt) texture handle vào target GL_TEXTURE_2D
                // Các lệnh cấu hình texture sau đó sẽ áp dụng cho handle này
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])
                checkGLError("glBindTexture ${textureHandle[0]}")

                // Thiết lập tham số lọc (filtering) cho texture
                // GL_LINEAR: lọc tuyến tính (mượt hơn khi scale), GL_NEAREST: lọc điểm gần nhất (rõ pixel)
                GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR
                ) // Lọc khi thu nhỏ
                GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR
                ) // Lọc khi phóng to
                // Thiết lập tham số bao bọc (wrapping) cho texture
                // GL_CLAMP_TO_EDGE: lặp lại màu pixel ở biên khi tọa độ ra ngoài [0,1]
                // GL_REPEAT: lặp lại texture
                GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE
                ) // Bao bọc theo chiều S (X)
                GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE
                ) // Bao bọc theo chiều T (Y)
                checkGLError("glTexParameteri")

                // Nạp dữ liệu từ Bitmap vào texture OpenGL đang được bind
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                checkGLError("texImage2D")

                // Giải phóng bộ nhớ của đối tượng Bitmap vì dữ liệu đã được copy vào OpenGL
                bitmap.recycle()

                // Unbind texture khỏi target GL_TEXTURE_2D (không bắt buộc nhưng là thực hành tốt)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            } else { // Nếu không tạo được texture handle
                Log.e(TAG, "Không thể tạo texture handle.")
                return 0 // Trả về 0 báo hiệu thất bại
            }

            // Trả về handle của texture đã tạo thành công
            return textureHandle[0]
        }
    }

    // --- Các biến thành viên của Renderer ---

    // Handles (ID) của các shader program và các biến bên trong shader (uniform, attribute)
    // Khởi tạo là 0 hoặc -1 để dễ kiểm tra trạng thái hợp lệ
    // @Volatile để đảm bảo visibility giữa các luồng (dù không hoàn toàn cần thiết ở đây vì GL chạy 1 luồng)
    @Volatile
    private var rippleShaderProgram: Int = 0
    private var ripplePositionHandle: Int = -1
    private var rippleColorHandle: Int = -1
    private var rippleMvpMatrixHandle: Int = -1
    private var rippleTimeHandle: Int = -1 // Handle cho uniform u_Time của ripple

    @Volatile
    private var backgroundShaderProgram: Int = 0
    private var backgroundPositionHandle: Int = -1
    private var backgroundTexCoordHandle: Int = -1
    private var backgroundTextureUniformHandle: Int = -1 // Handle cho uniform sampler u_Texture
    private var backgroundTextureId: Int = 0 // ID của đối tượng texture background

    // --- Buffers chứa dữ liệu hình học ---
    // lateinit vì sẽ được khởi tạo trong onSurfaceCreated
    private lateinit var backgroundVertexBuffer: FloatBuffer // Buffer cho hình chữ nhật background

    // --- Ma trận biến đổi ---
    private val projectionMatrix = FloatArray(16) // Ma trận chiếu (orthographic)
    private val viewMatrix = FloatArray(16)       // Ma trận nhìn (camera)
    private val modelMatrix =
        FloatArray(16)    // Ma trận model (vị trí, xoay, tỷ lệ của đối tượng) - Dùng lại cho ripple
    private val mvpMatrix =
        FloatArray(16)      // Ma trận Model-View-Projection tổng hợp - Dùng lại cho ripple

    // --- Kích thước màn hình/viewport ---
    @Volatile
    private var screenWidth: Int = 0
    @Volatile
    private var screenHeight: Int = 0
    private var aspectRatio: Float = 1.0f // Tỷ lệ khung hình (width / height)

    // --- Dữ liệu hiệu ứng Ripple ---
    // Data class để lưu trữ thông tin của một hiệu ứng ripple đang hoạt động
    data class Ripple(
        val centerX: Float, // Tọa độ X tâm ripple trong không gian GL
        val centerY: Float, // Tọa độ Y tâm ripple trong không gian GL
        val startTime: Long = SystemClock.uptimeMillis(), // Thời điểm bắt đầu hiệu ứng
        // *** Điều chỉnh kích thước Ripple ***
        val maxRadius: Float = 0.08f, // Bán kính tối đa hiệu ứng đạt tới (tính theo đơn vị GL trục Y) (Đã giảm)
        val duration: Float = 2000.0f, // Thời gian tồn tại của hiệu ứng (ms)
        val color: FloatArray = floatArrayOf(
            1.0f,
            1.0f,
            1.0f,
            0.8f
        ), // Màu gốc RGBA (Trắng, hơi trong)
        val vertexBuffer: FloatBuffer = createCircleVertices(), // Buffer chứa đỉnh hình tròn cho ripple này
        val vertexCount: Int = RIPPLE_SEGMENTS + 2 // Số lượng đỉnh để vẽ hình tròn
    ) {
        companion object {
            /** Tạo dữ liệu đỉnh cho hình tròn dùng GL_TRIANGLE_FAN. */
            private fun createCircleVertices(): FloatBuffer {
                val numVertices = RIPPLE_SEGMENTS + 2 // Tâm + các đoạn + điểm đóng vòng
                // X, Y, Z cho mỗi đỉnh
                val circleCoords = FloatArray(numVertices * POSITION_COMPONENT_COUNT_XYZ)
                // Đỉnh tâm (0,0,0)
                circleCoords[0] = 0f; circleCoords[1] = 0f; circleCoords[2] = 0f
                // Các đỉnh trên chu vi (bán kính 1)
                for (i in 1 until numVertices) {
                    // Tính góc cho từng đỉnh
                    val angle = 2.0 * Math.PI * (i - 1).toDouble() / RIPPLE_SEGMENTS.toDouble()
                    val index =
                        i * POSITION_COMPONENT_COUNT_XYZ // Chỉ số bắt đầu của đỉnh i trong mảng
                    // Tính tọa độ X, Y dựa trên góc và bán kính 1
                    circleCoords[index + 0] = cos(angle).toFloat() // x = cos(angle)
                    circleCoords[index + 1] = sin(angle).toFloat() // y = sin(angle)
                    circleCoords[index + 2] = 0f                   // z = 0 (trong mặt phẳng 2D)
                }
                // Đảm bảo đỉnh cuối cùng của FAN trùng với đỉnh đầu tiên trên chu vi để khép kín
                val lastOuterIndex = (numVertices - 1) * POSITION_COMPONENT_COUNT_XYZ
                val firstOuterIndex =
                    1 * POSITION_COMPONENT_COUNT_XYZ // Chỉ số đỉnh đầu tiên trên chu vi
                circleCoords[lastOuterIndex + 0] = circleCoords[firstOuterIndex + 0]
                circleCoords[lastOuterIndex + 1] = circleCoords[firstOuterIndex + 1]
                circleCoords[lastOuterIndex + 2] = circleCoords[firstOuterIndex + 2]

                // Tạo FloatBuffer từ mảng dữ liệu đỉnh
                return createFloatBuffer(circleCoords)
            }
        }

        /**
         * Cập nhật trạng thái (dựa trên thời gian) và vẽ ripple.
         * @param posHandle Handle của attribute vị trí (vPosition).
         * @param colHandle Handle của uniform màu (vColor).
         * @param mvpHandle Handle của uniform ma trận MVP (uMVPMatrix).
         * @param timeHandle Handle của uniform thời gian (u_Time).
         * @param vMatrix Ma trận View.
         * @param pMatrix Ma trận Projection.
         * @param modMatrix Ma trận Model (dùng để tính toán, tránh cấp phát lại).
         * @param mvpCalcMatrix Ma trận MVP (dùng để tính toán, tránh cấp phát lại).
         * @return true nếu ripple vẫn còn hoạt động, false nếu đã kết thúc.
         */
        fun updateAndDraw(
            posHandle: Int,
            colHandle: Int,
            mvpHandle: Int,
            timeHandle: Int, // Thêm timeHandle
            vMatrix: FloatArray,
            pMatrix: FloatArray,
            modMatrix: FloatArray,
            mvpCalcMatrix: FloatArray
        ): Boolean {
            val now = SystemClock.uptimeMillis() // Lấy thời gian hiện tại
            val elapsedTime = now - startTime // Tính thời gian đã trôi qua từ lúc bắt đầu

            // Kiểm tra nếu hiệu ứng đã hết hạn hoặc các handle không hợp lệ
            if (elapsedTime > duration || posHandle < 0 || colHandle < 0 || mvpHandle < 0 || timeHandle < 0) {
                return false // Ripple kết thúc hoặc không thể vẽ
            }

            // Tính toán tiến trình animation (từ 0.0 đến 1.0)
            // coerceIn đảm bảo giá trị luôn nằm trong khoảng [0, 1]
            val progress = (elapsedTime / duration).coerceIn(0f, 1f)

            // --- Chuẩn bị Ma trận ---
            // Ma trận Model: Di chuyển đến tâm ripple và scale (scale chỉ để định vùng, hiệu ứng trong shader)
            Matrix.setIdentityM(modMatrix, 0) // Reset ma trận model
            Matrix.translateM(modMatrix, 0, centerX, centerY, 0f) // Di chuyển đến tâm
            // Scale vùng vẽ hơi lớn hơn maxRadius để hiệu ứng shader không bị cắt đột ngột ở rìa
            Matrix.scaleM(modMatrix, 0, maxRadius * 1.1f, maxRadius * 1.1f, 1.0f)

            // Ma trận MVP: Tính toán P * V * M
            Matrix.multiplyMM(mvpCalcMatrix, 0, vMatrix, 0, modMatrix, 0) // mvpCalc = V * M
            Matrix.multiplyMM(
                mvpCalcMatrix,
                0,
                pMatrix,
                0,
                mvpCalcMatrix,
                0
            ) // mvpCalc = P * (V * M)

            // --- Thiết lập Uniforms và Attributes cho Shader ---
            vertexBuffer.position(0) // Đặt lại vị trí đọc của buffer về đầu
            // Chỉ định dữ liệu đỉnh cho attribute vPosition
            GLES20.glVertexAttribPointer(
                posHandle,
                POSITION_COMPONENT_COUNT_XYZ,
                GLES20.GL_FLOAT,
                false,
                0,
                vertexBuffer
            )
            checkGLError("Ripple glVertexAttribPointer vPosition") // Kiểm tra lỗi
            GLES20.glEnableVertexAttribArray(posHandle) // Kích hoạt attribute array
            checkGLError("Ripple glEnableVertexAttribArray vPosition")

            // Truyền màu gốc vào uniform vColor (fragment shader sẽ tính alpha cuối cùng)
            GLES20.glUniform4fv(colHandle, 1, color, 0)
            checkGLError("Ripple glUniform4fv vColor")

            // Truyền ma trận MVP vào uniform uMVPMatrix
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpCalcMatrix, 0)
            checkGLError("Ripple glUniformMatrix4fv uMVPMatrix")

            // *** Truyền tiến trình animation (0.0 -> 1.0) vào uniform u_Time ***
            GLES20.glUniform1f(timeHandle, progress)
            checkGLError("Ripple glUniform1f u_Time")

            // --- Thực hiện Vẽ ---
            // Vẽ hình tròn bằng chế độ GL_TRIANGLE_FAN
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, vertexCount)
            checkGLError("Ripple glDrawArrays")

            // --- Dọn dẹp ---
            // Vô hiệu hóa attribute array sau khi vẽ xong
            GLES20.glDisableVertexAttribArray(posHandle)
            checkGLError("Ripple glDisableVertexAttribArray vPosition")

            // Ripple vẫn còn hoạt động
            return true
        }
    }

    // Danh sách chứa các hiệu ứng ripple đang hoạt động
    // Dùng synchronizedList hoặc synchronized block để đảm bảo an toàn luồng
    private val activeRipples = mutableListOf<Ripple>()
    private val ripplesLock = Any() // Đối tượng khóa để đồng bộ hóa truy cập `activeRipples`


    // --- Các hàm Callback của GLSurfaceView.Renderer ---

    /**
     * Được gọi một lần khi surface OpenGL được tạo.
     * Dùng để khởi tạo tài nguyên OpenGL (shaders, textures, buffers...).
     */
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, "onSurfaceCreated: Đang thiết lập tài nguyên GL.")
        // Reset trạng thái phòng trường hợp context bị mất và tạo lại
        rippleShaderProgram = 0; backgroundShaderProgram = 0; backgroundTextureId = 0
        ripplePositionHandle = -1; rippleColorHandle = -1; rippleMvpMatrixHandle =
            -1; rippleTimeHandle = -1
        backgroundPositionHandle = -1; backgroundTexCoordHandle =
            -1; backgroundTextureUniformHandle = -1

        // --- Thiết lập Background ---
        try {
            // Đặt màu nền dự phòng (nếu không load được texture)
            GLES20.glClearColor(0.1f, 0.1f, 0.2f, 1.0f) // Màu xanh đen
            checkGLError("glClearColor Fallback")

            // *** QUAN TRỌNG: Thay R.drawable.background bằng ID tài nguyên ảnh của bạn ***
            backgroundTextureId =
                loadTexture(context, R.drawable.background) // Ví dụ: R.drawable.anh_nen_jpg
            if (backgroundTextureId == 0) {
                Log.e(TAG, "Không thể tải texture background. Sẽ dùng màu nền dự phòng.")
            } else {
                Log.i(TAG, "Texture background đã tải thành công: ID=$backgroundTextureId")
            }

            // Tạo shader program cho background
            val bgVertexCode = readTextFileFromAssets(context, BACKGROUND_VERTEX_SHADER_PATH)
            val bgFragmentCode = readTextFileFromAssets(context, BACKGROUND_FRAGMENT_SHADER_PATH)
            if (bgVertexCode != null && bgFragmentCode != null) {
                // Tạo và liên kết shader program
                backgroundShaderProgram = createAndLinkProgram(bgVertexCode, bgFragmentCode)
                if (backgroundShaderProgram != 0) { // Nếu tạo thành công
                    // Lấy handles của các biến attribute và uniform trong background shader
                    backgroundPositionHandle =
                        GLES20.glGetAttribLocation(backgroundShaderProgram, "a_Position")
                    backgroundTexCoordHandle =
                        GLES20.glGetAttribLocation(backgroundShaderProgram, "a_TexCoord")
                    backgroundTextureUniformHandle =
                        GLES20.glGetUniformLocation(backgroundShaderProgram, "u_Texture")
                    Log.i(
                        TAG,
                        "Shader program background đã tạo và lấy handles: $backgroundShaderProgram"
                    )
                    // Kiểm tra xem tất cả handles có hợp lệ không (-1 là không hợp lệ)
                    if (backgroundPositionHandle == -1 || backgroundTexCoordHandle == -1 || backgroundTextureUniformHandle == -1) {
                        Log.e(
                            TAG,
                            "Không thể lấy đủ handles cho background shader. Pos=$backgroundPositionHandle, TexCoord=$backgroundTexCoordHandle, TexUniform=$backgroundTextureUniformHandle"
                        )
                        GLES20.glDeleteProgram(backgroundShaderProgram); backgroundShaderProgram =
                            0 // Xóa program nếu lỗi
                    }
                } else {
                    Log.e(TAG, "Không thể tạo background shader program (liên kết lỗi).")
                }
            } else {
                Log.e(TAG, "Không thể đọc mã nguồn background shader.")
            }

            // Tạo Vertex Buffer cho background quad (hình chữ nhật toàn màn hình)
            // Dữ liệu xen kẽ: X, Y (vị trí), S, T (tọa độ texture)
            // Stride = (2 vị trí + 2 tọa độ tex) * 4 byte/float
            val stride = (POSITION_COMPONENT_COUNT_XY + TEX_COORD_COMPONENT_COUNT) * BYTES_PER_FLOAT
            val quadVertices = floatArrayOf(
                // Tam giác 1
                -1.0f, 1.0f, 0.0f, 0.0f, // Đỉnh Trên Trái
                -1.0f, -1.0f, 0.0f, 1.0f, // Đỉnh Dưới Trái
                1.0f, -1.0f, 1.0f, 1.0f, // Đỉnh Dưới Phải
                // Tam giác 2
                1.0f, -1.0f, 1.0f, 1.0f, // Đỉnh Dưới Phải
                1.0f, 1.0f, 1.0f, 0.0f, // Đỉnh Trên Phải
                -1.0f, 1.0f, 0.0f, 0.0f  // Đỉnh Trên Trái
            )
            backgroundVertexBuffer = createFloatBuffer(quadVertices) // Tạo buffer

        } catch (e: Exception) { // Bắt lỗi nghiêm trọng trong quá trình thiết lập background
            Log.e(TAG, "Lỗi nghiêm trọng khi thiết lập background", e)
            // Có thể đặt cờ lỗi để ngăn việc render nếu cần
        }

        // --- Thiết lập Ripples ---
        try {
            // Tạo shader program cho hiệu ứng ripple
            val rippleVertexCode = readTextFileFromAssets(context, RIPPLE_VERTEX_SHADER_PATH)
            val rippleFragmentCode = readTextFileFromAssets(context, RIPPLE_FRAGMENT_SHADER_PATH)
            if (rippleVertexCode != null && rippleFragmentCode != null) {
                rippleShaderProgram = createAndLinkProgram(rippleVertexCode, rippleFragmentCode)
                if (rippleShaderProgram != 0) { // Nếu tạo thành công
                    // Lấy handles của các biến trong ripple shader
                    ripplePositionHandle =
                        GLES20.glGetAttribLocation(rippleShaderProgram, "vPosition")
                    rippleColorHandle = GLES20.glGetUniformLocation(rippleShaderProgram, "vColor")
                    rippleMvpMatrixHandle =
                        GLES20.glGetUniformLocation(rippleShaderProgram, "uMVPMatrix")
                    rippleTimeHandle = GLES20.glGetUniformLocation(
                        rippleShaderProgram,
                        "u_Time"
                    ) // Lấy handle cho u_Time
                    Log.i(TAG, "Shader program ripple đã tạo và lấy handles: $rippleShaderProgram")
                    // Kiểm tra các handle ripple
                    if (ripplePositionHandle == -1 || rippleColorHandle == -1 || rippleMvpMatrixHandle == -1 || rippleTimeHandle == -1) {
                        Log.e(
                            TAG,
                            "Không thể lấy đủ handles cho ripple shader. Pos=$ripplePositionHandle, Color=$rippleColorHandle, MVP=$rippleMvpMatrixHandle, Time=$rippleTimeHandle"
                        )
                        GLES20.glDeleteProgram(rippleShaderProgram); rippleShaderProgram =
                            0 // Xóa program nếu lỗi
                    }
                } else {
                    Log.e(TAG, "Không thể tạo ripple shader program (liên kết lỗi).")
                }
            } else {
                Log.e(TAG, "Không thể đọc mã nguồn ripple shader.")
            }
        } catch (e: Exception) { // Bắt lỗi nghiêm trọng khi thiết lập ripple
            Log.e(TAG, "Lỗi nghiêm trọng khi thiết lập ripple", e)
        }

        // --- Thiết lập trạng thái OpenGL cuối cùng ---
        GLES20.glEnable(GLES20.GL_BLEND) // Bật chế độ hòa trộn (blending) cho hiệu ứng trong suốt
        checkGLError("glEnable GL_BLEND")
        // Đặt hàm hòa trộn: màu nguồn * alpha nguồn + màu đích * (1 - alpha nguồn)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        checkGLError("glBlendFunc")
        // Tắt kiểm tra chiều sâu (depth testing) vì đây là hiệu ứng 2D phẳng
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        Log.i(TAG, "Hoàn tất thiết lập OpenGL Surface.")
    }

    /**
     * Hàm helper để tạo, biên dịch, liên kết và kiểm tra shader program.
     * @param vertexShaderCode Mã nguồn vertex shader.
     * @param fragmentShaderCode Mã nguồn fragment shader.
     * @return Handle của program đã tạo và link thành công, hoặc 0 nếu thất bại.
     */
    private fun createAndLinkProgram(vertexShaderCode: String, fragmentShaderCode: String): Int {
        // Biên dịch vertex và fragment shader
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        // Kiểm tra nếu biên dịch shader thất bại
        if (vertexShader == 0 || fragmentShader == 0) {
            Log.e(TAG, "Biên dịch shader thất bại, không thể liên kết program.")
            // Dọn dẹp shader đã biên dịch thành công (nếu có)
            if (vertexShader != 0) GLES20.glDeleteShader(vertexShader)
            if (fragmentShader != 0) GLES20.glDeleteShader(fragmentShader)
            return 0 // Trả về 0 báo hiệu thất bại
        }

        // Tạo đối tượng program mới
        val program = GLES20.glCreateProgram()
        if (program == 0) { // Kiểm tra nếu tạo program thất bại
            Log.e(TAG, "Không thể tạo GL program handle.")
            checkGLError("glCreateProgram")
            // Dọn dẹp shaders nếu tạo program lỗi
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return 0
        }

        // Gắn các shader đã biên dịch vào program
        GLES20.glAttachShader(program, vertexShader)
        checkGLError("glAttachShader Vertex vào program $program")
        GLES20.glAttachShader(program, fragmentShader)
        checkGLError("glAttachShader Fragment vào program $program")

        // Liên kết program (kết nối output của vertex shader với input của fragment shader)
        GLES20.glLinkProgram(program)
        checkGLError("glLinkProgram $program")

        // Các đối tượng shader không còn cần thiết sau khi đã liên kết vào program
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        // Kiểm tra trạng thái liên kết (hàm này sẽ xóa program nếu link lỗi)
        if (!checkProgramLinkStatus(program)) {
            Log.e(TAG, "Liên kết program thất bại cho program $program.")
            return 0 // Trả về 0 báo hiệu thất bại
        }

        // Tùy chọn: Xác thực program (hữu ích khi debug, có thể làm chậm)
        // GLES20.glValidateProgram(program);
        // val validateStatus = IntArray(1)
        // GLES20.glGetProgramiv(program, GLES20.GL_VALIDATE_STATUS, validateStatus, 0)
        // if (validateStatus[0] == 0) {
        //     Log.e(TAG, "Lỗi xác thực program $program: ${GLES20.glGetProgramInfoLog(program)}")
        //     GLES20.glDeleteProgram(program) // Xóa nếu không hợp lệ
        //     return 0
        // }

        Log.i(TAG, "Tạo và liên kết program $program thành công")
        return program // Trả về handle của program hợp lệ
    }


    /**
     * Được gọi khi kích thước surface thay đổi (ví dụ: xoay màn hình).
     * Dùng để cập nhật viewport và ma trận chiếu.
     */
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        // Tránh lỗi chia cho 0 hoặc kích thước không hợp lệ
        if (height <= 0 || width <= 0) {
            Log.w(TAG, "onSurfaceChanged nhận kích thước không hợp lệ: $width x $height")
            return
        }
        screenWidth = width
        screenHeight = height
        aspectRatio = width.toFloat() / height.toFloat() // Tính tỷ lệ khung hình

        // Đặt vùng nhìn OpenGL (viewport) khớp với kích thước surface mới
        GLES20.glViewport(0, 0, width, height)
        checkGLError("glViewport")

        // Thiết lập ma trận chiếu trực giao (orthographic) phù hợp cho 2D
        // Ánh xạ tọa độ GL X từ -aspectRatio đến +aspectRatio, Y từ -1 đến +1
        Matrix.orthoM(projectionMatrix, 0, -aspectRatio, aspectRatio, -1f, 1f, -1f, 1f)

        // Thiết lập ma trận nhìn (camera) đơn giản
        // Nhìn từ điểm (0,0,1) về phía gốc tọa độ (0,0,0), hướng lên là (0,1,0)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1.0f, 0.0f)

        Log.i(TAG, "OpenGL Surface Changed: $width x $height, Tỷ lệ: $aspectRatio")
    }

    /**
     * Được gọi liên tục để vẽ mỗi khung hình của wallpaper.
     */
    override fun onDrawFrame(gl: GL10?) {
        // Xóa bộ đệm màu (và chiều sâu nếu dùng) ở đầu mỗi khung hình
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT /* | GLES20.GL_DEPTH_BUFFER_BIT */)
        checkGLError("glClear")

        // --- 1. Vẽ Background ---
        // Kiểm tra xem các tài nguyên background đã sẵn sàng chưa
        if (backgroundShaderProgram > 0 && backgroundTextureId > 0 &&
            backgroundPositionHandle != -1 && backgroundTexCoordHandle != -1 && backgroundTextureUniformHandle != -1
        ) {
            // Sử dụng shader program của background
            GLES20.glUseProgram(backgroundShaderProgram)
            checkGLError("BG: glUseProgram $backgroundShaderProgram")

            // Kích hoạt đơn vị texture 0
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            checkGLError("BG: glActiveTexture")
            // Bind texture background vào đơn vị 0
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTextureId)
            checkGLError("BG: glBindTexture $backgroundTextureId")
            // Nói cho uniform sampler 'u_Texture' biết lấy mẫu từ đơn vị texture 0
            GLES20.glUniform1i(backgroundTextureUniformHandle, 0)
            checkGLError("BG: glUniform1i u_Texture")

            // --- Thiết lập dữ liệu đỉnh cho background quad ---
            // Stride (bước nhảy) giữa dữ liệu của các đỉnh trong buffer
            // = (Số thành phần vị trí + Số thành phần tọa độ tex) * số byte mỗi float
            val stride = (POSITION_COMPONENT_COUNT_XY + TEX_COORD_COMPONENT_COUNT) * BYTES_PER_FLOAT

            // Kích hoạt và chỉ định attribute vị trí (a_Position)
            backgroundVertexBuffer.position(0) // Bắt đầu đọc từ đầu buffer cho vị trí (X, Y)
            GLES20.glVertexAttribPointer(
                backgroundPositionHandle,       // Handle của attribute
                POSITION_COMPONENT_COUNT_XY,    // Số thành phần (X, Y = 2)
                GLES20.GL_FLOAT,                // Kiểu dữ liệu
                false,                          // Có chuẩn hóa không?
                stride,                         // Khoảng cách (bytes) giữa dữ liệu đỉnh liên tiếp
                backgroundVertexBuffer          // Buffer chứa dữ liệu
            )
            checkGLError("BG: glVertexAttribPointer a_Position")
            GLES20.glEnableVertexAttribArray(backgroundPositionHandle) // Kích hoạt attribute này
            checkGLError("BG: glEnableVertexAttribArray a_Position")

            // Kích hoạt và chỉ định attribute tọa độ texture (a_TexCoord)
            // Offset = số float của vị trí (X, Y = 2) để đến dữ liệu tọa độ tex (S, T)
            backgroundVertexBuffer.position(POSITION_COMPONENT_COUNT_XY)
            GLES20.glVertexAttribPointer(
                backgroundTexCoordHandle,       // Handle của attribute
                TEX_COORD_COMPONENT_COUNT,      // Số thành phần (S, T = 2)
                GLES20.GL_FLOAT,                // Kiểu dữ liệu
                false,                          // Có chuẩn hóa không?
                stride,                         // Khoảng cách (bytes) giữa dữ liệu đỉnh liên tiếp
                backgroundVertexBuffer          // Buffer chứa dữ liệu
            )
            checkGLError("BG: glVertexAttribPointer a_TexCoord")
            GLES20.glEnableVertexAttribArray(backgroundTexCoordHandle) // Kích hoạt attribute này
            checkGLError("BG: glEnableVertexAttribArray a_TexCoord")

            // Vẽ background quad (gồm 6 đỉnh định nghĩa 2 tam giác)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
            checkGLError("BG: glDrawArrays")

            // --- Dọn dẹp attribute và texture của background ---
            GLES20.glDisableVertexAttribArray(backgroundPositionHandle) // Vô hiệu hóa attribute vị trí
            GLES20.glDisableVertexAttribArray(backgroundTexCoordHandle) // Vô hiệu hóa attribute tọa độ tex
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0) // Unbind texture khỏi đơn vị 0
        } else {
            // Ghi log nếu không thể vẽ background (ví dụ: lỗi load texture/shader)
            // Log.v(TAG, "Bỏ qua vẽ background - tài nguyên không hợp lệ.")
        }


        // --- 2. Vẽ Ripples (lên trên background) ---
        // Kiểm tra xem các tài nguyên ripple đã sẵn sàng chưa
        if (rippleShaderProgram > 0 && ripplePositionHandle != -1 && rippleColorHandle != -1 &&
            rippleMvpMatrixHandle != -1 && rippleTimeHandle != -1
        ) {
            // Sử dụng shader program của ripple
            GLES20.glUseProgram(rippleShaderProgram)
            checkGLError("Ripple: glUseProgram $rippleShaderProgram")

            // Đảm bảo chế độ hòa trộn (blending) được bật cho hiệu ứng trong suốt
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            // Duyệt qua danh sách các ripple đang hoạt động và vẽ chúng
            synchronized(ripplesLock) { // Đồng bộ hóa truy cập danh sách để tránh lỗi đa luồng
                val iterator = activeRipples.iterator()
                while (iterator.hasNext()) {
                    val ripple = iterator.next()
                    // Gọi hàm vẽ của đối tượng ripple, truyền các handle và ma trận cần thiết
                    if (!ripple.updateAndDraw(
                            ripplePositionHandle,
                            rippleColorHandle,
                            rippleMvpMatrixHandle,
                            rippleTimeHandle,
                            viewMatrix,
                            projectionMatrix,
                            modelMatrix,
                            mvpMatrix
                        )
                    ) {
                        // Nếu hàm trả về false (ripple đã kết thúc), xóa nó khỏi danh sách
                        iterator.remove()
                    }
                }
            }
        } else {
            // Ghi log nếu không thể vẽ ripple
            // Log.v(TAG, "Bỏ qua vẽ ripple - tài nguyên không hợp lệ.")
        }

        // Kiểm tra lỗi OpenGL cuối cùng của khung hình
        checkGLError("Cuối hàm onDrawFrame")
    }

    // --- Xử lý sự kiện chạm ---
    /**
     * Xử lý sự kiện chạm, chuyển đổi tọa độ màn hình sang tọa độ GL và thêm ripple mới.
     * Hàm này được gọi trên luồng GL nhờ queueEvent.
     */
    fun handleTouch(screenX: Float, screenY: Float) {
        // Đảm bảo kích thước màn hình đã hợp lệ trước khi tính toán
        if (screenWidth <= 0 || screenHeight <= 0) {
            Log.w(
                TAG,
                "handleTouch được gọi trước khi kích thước surface được thiết lập ($screenWidth x $screenHeight)."
            )
            return
        }

        // Chuyển đổi tọa độ màn hình (pixel, gốc ở trên-trái) sang
        // Tọa độ Thiết bị Chuẩn hóa OpenGL (NDC) X từ -aspectRatio đến +aspectRatio,
        // Y từ -1 đến +1, gốc ở giữa.
        val glX = (screenX / screenWidth * 2.0f - 1.0f) * aspectRatio
        // Đảo ngược trục Y vì Y màn hình tăng xuống dưới, trong khi Y OpenGL tăng lên trên
        val glY = -(screenY / screenHeight * 2.0f - 1.0f)

        // Thêm ripple mới vào danh sách một cách an toàn (đồng bộ hóa)
        synchronized(ripplesLock) {
            // Tùy chọn: Giới hạn số lượng ripple tối đa để đảm bảo hiệu năng
            val maxRipples = 25
            if (activeRipples.size < maxRipples) {
                activeRipples.add(Ripple(glX, glY)) // Tạo và thêm ripple mới
                // Log.v(TAG, "Đã thêm Ripple tại GL ($glX, $glY). Số lượng: ${activeRipples.size}")
            } else {
                Log.d(TAG, "Đã đạt số lượng ripple tối đa ($maxRipples). Bỏ qua chạm.")
            }
        }
    }

    /** Tùy chọn: Gọi hàm này từ onDestroy của Engine để giải phóng tài nguyên GL tường minh */
    fun releaseResources() {
        Log.i(TAG, "Đang giải phóng tài nguyên OpenGL...")
        // Xóa các shader program
        if (rippleShaderProgram > 0) {
            GLES20.glDeleteProgram(rippleShaderProgram)
            rippleShaderProgram = 0
            Log.d(TAG, "Đã xóa ripple shader program.")
        }
        if (backgroundShaderProgram > 0) {
            GLES20.glDeleteProgram(backgroundShaderProgram)
            backgroundShaderProgram = 0
            Log.d(TAG, "Đã xóa background shader program.")
        }
        // Xóa texture
        if (backgroundTextureId > 0) {
            val textures = intArrayOf(backgroundTextureId)
            GLES20.glDeleteTextures(1, textures, 0) // Xóa texture với handle đã lưu
            backgroundTextureId = 0
            Log.d(TAG, "Đã xóa background texture.")
        }
        // Các buffer (VBOs nếu dùng) cũng có thể được xóa ở đây:
        // GLES20.glDeleteBuffers(...)
        Log.i(TAG, "Hoàn tất giải phóng tài nguyên OpenGL.")
    }
}
