package com.example.demolivewallpaper // Package name đã đổi

import android.content.Context
import android.graphics.BitmapFactory // Import để load ảnh
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils // Import để dùng getEGLErrorString và texImage2D
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
import kotlin.math.sqrt // Import hàm sqrt

class OpenGLRenderer(private val context: Context) : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "OpenGLRenderer"
        private const val BYTES_PER_FLOAT = 4
        // Constants for vertex data layout
        private const val POSITION_COMPONENT_COUNT_XY = 2 // X, Y for background
        private const val POSITION_COMPONENT_COUNT_XYZ = 3 // X, Y, Z for ripple
        private const val TEX_COORD_COMPONENT_COUNT = 2 // S, T for background texture
        private const val RIPPLE_SEGMENTS = 30 // Độ chi tiết của vòng tròn ripple

        // --- Shader File Paths ---
        private const val RIPPLE_VERTEX_SHADER_PATH = "shaders/vertex_shader.glsl"
        private const val RIPPLE_FRAGMENT_SHADER_PATH = "shaders/fragment_shader.glsl"
        private const val BACKGROUND_VERTEX_SHADER_PATH = "shaders/background_vertex_shader.glsl"
        private const val BACKGROUND_FRAGMENT_SHADER_PATH = "shaders/background_fragment_shader.glsl"

        // --- Utility Functions ---

        /**
         * Reads a text file from the assets folder.
         * @param context App context to access assets.
         * @param filePath Relative path within the assets folder.
         * @return File content as String, or null on error.
         */
        private fun readTextFileFromAssets(context: Context, filePath: String): String? {
            return try {
                context.assets.open(filePath).bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                Log.e(TAG, "Error reading asset file: $filePath", e)
                null
            }
        }

        /**
         * Loads and compiles a shader.
         * @param type GLES20.GL_VERTEX_SHADER or GLES20.GL_FRAGMENT_SHADER.
         * @param shaderCode The GLSL source code.
         * @return Shader handle (positive int) or 0 on failure.
         */
        private fun loadShader(type: Int, shaderCode: String): Int {
            if (shaderCode.isBlank()) {
                Log.e(TAG, "Shader code is empty for type $type.")
                return 0
            }
            val shader = GLES20.glCreateShader(type)
            if (shader == 0) {
                Log.e(TAG, "Cannot create shader type $type")
                checkGLError("glCreateShader type $type") // Check error after creation attempt
                return 0
            }
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            // Check compile status
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                val shaderType = if (type == GLES20.GL_VERTEX_SHADER) "Vertex" else "Fragment"
                Log.e(TAG, "Cannot compile $shaderType shader:")
                Log.e(TAG, GLES20.glGetShaderInfoLog(shader))
                GLES20.glDeleteShader(shader) // Clean up failed shader
                return 0
            }
            return shader
        }

        /**
         * Checks the link status of a shader program.
         * Deletes the program if linking failed.
         * @param program The shader program handle.
         * @return true if linked successfully, false otherwise.
         */
        private fun checkProgramLinkStatus(program: Int): Boolean {
            if (program == 0) return false
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                Log.e(TAG, "Cannot link program: ")
                Log.e(TAG, GLES20.glGetProgramInfoLog(program))
                GLES20.glDeleteProgram(program) // Clean up failed program
                return false
            }
            return true
        }

        /**
         * Creates a native FloatBuffer from a Kotlin FloatArray.
         * @param data The float array.
         * @return The FloatBuffer.
         */
        private fun createFloatBuffer(data: FloatArray): FloatBuffer {
            return ByteBuffer.allocateDirect(data.size * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(data)
                .position(0) as FloatBuffer
        }

        /**
         * Checks for OpenGL errors.
         * @param op A string identifying the operation before the check.
         */
        private fun checkGLError(op: String) {
            // Loop as there might be multiple errors queued
            var error: Int
            while (GLES20.glGetError().also { error = it } != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "$op: glError ${error} - ${GLUtils.getEGLErrorString(error)}")
                // Optional: throw RuntimeException("$op: glError $error") in debug builds
            }
        }

        /**
         * Loads a texture from a drawable resource.
         * @param context App context.
         * @param resourceId The drawable resource ID (e.g., R.drawable.my_texture).
         * @return The OpenGL texture ID (positive int) or 0 on failure.
         */
        private fun loadTexture(context: Context, resourceId: Int): Int {
            val textureHandle = IntArray(1)
            GLES20.glGenTextures(1, textureHandle, 0)
            checkGLError("glGenTextures")

            if (textureHandle[0] != 0) {
                val options = BitmapFactory.Options().apply {
                    inScaled = false // Prevent pre-scaling
                }
                val bitmap = try {
                    BitmapFactory.decodeResource(context.resources, resourceId, options)
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "Out of memory loading texture resource $resourceId", e)
                    null
                }

                if (bitmap == null) {
                    Log.e(TAG, "Cannot decode resource $resourceId into bitmap")
                    GLES20.glDeleteTextures(1, textureHandle, 0)
                    return 0
                }

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])
                checkGLError("glBindTexture ${textureHandle[0]}")

                // Set filtering: GL_LINEAR for smoother scaling, GL_NEAREST for pixelated
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                // Set wrapping: GL_CLAMP_TO_EDGE prevents edge artifacts from filtering
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                checkGLError("glTexParameteri")

                // Load the bitmap data into the texture
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                checkGLError("texImage2D")

                bitmap.recycle() // Important: Recycle the bitmap after loading to GL

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0) // Unbind texture
            } else {
                Log.e(TAG, "Cannot generate texture handle.")
                return 0
            }

            return textureHandle[0]
        }
    }

    // --- Shader Programs and Handles ---
    // Ripple Shader
    @Volatile private var rippleShaderProgram: Int = 0
    private var ripplePositionHandle: Int = -1
    private var rippleColorHandle: Int = -1
    private var rippleMvpMatrixHandle: Int = -1
    private var rippleTimeHandle: Int = -1 // Handle for u_Time uniform

    // Background Shader
    @Volatile private var backgroundShaderProgram: Int = 0
    private var backgroundPositionHandle: Int = -1
    private var backgroundTexCoordHandle: Int = -1
    private var backgroundTextureUniformHandle: Int = -1 // Handle for u_Texture sampler
    private var backgroundTextureId: Int = 0 // Texture object ID

    // --- Geometry Buffers ---
    private lateinit var backgroundVertexBuffer: FloatBuffer // Buffer for background quad vertices + tex coords

    // --- Matrices ---
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)    // Reused for ripples
    private val mvpMatrix = FloatArray(16)      // Reused for ripples

    // --- Viewport ---
    @Volatile private var screenWidth: Int = 0
    @Volatile private var screenHeight: Int = 0
    private var aspectRatio: Float = 1.0f

    // --- Ripple Data ---
    data class Ripple(
        val centerX: Float, // GL X coordinate
        val centerY: Float, // GL Y coordinate
        val startTime: Long = SystemClock.uptimeMillis(), // Start time for animation
        val maxRadius: Float = 0.1f, // Maximum visual radius in GL Y units
        val duration: Float = 1200.0f, // Duration of the effect in milliseconds
        val color: FloatArray = floatArrayOf(1.0f, 1.0f, 1.0f, 0.8f), // Base color (RGBA) - White, slightly transparent
        val vertexBuffer: FloatBuffer = createCircleVertices(), // Vertex buffer for this ripple's circle
        val vertexCount: Int = RIPPLE_SEGMENTS + 2 // Number of vertices for the circle
    ) {
        companion object {
            /** Creates the vertex data for a circle using a triangle fan. */
            private fun createCircleVertices(): FloatBuffer {
                val numVertices = RIPPLE_SEGMENTS + 2 // Center + segments + closing segment
                // X, Y, Z for each vertex
                val circleCoords = FloatArray(numVertices * POSITION_COMPONENT_COUNT_XYZ)
                // Center vertex
                circleCoords[0] = 0f; circleCoords[1] = 0f; circleCoords[2] = 0f
                // Outer vertices
                for (i in 1 until numVertices) {
                    val angle = 2.0 * Math.PI * (i - 1).toDouble() / RIPPLE_SEGMENTS.toDouble()
                    val index = i * POSITION_COMPONENT_COUNT_XYZ
                    circleCoords[index + 0] = cos(angle).toFloat() // x
                    circleCoords[index + 1] = sin(angle).toFloat() // y
                    circleCoords[index + 2] = 0f                   // z
                }
                // Ensure the last vertex connects back to the first outer vertex for TRIANGLE_FAN
                val lastOuterIndex = (numVertices - 1) * POSITION_COMPONENT_COUNT_XYZ
                val firstOuterIndex = 1 * POSITION_COMPONENT_COUNT_XYZ
                circleCoords[lastOuterIndex + 0] = circleCoords[firstOuterIndex + 0]
                circleCoords[lastOuterIndex + 1] = circleCoords[firstOuterIndex + 1]
                circleCoords[lastOuterIndex + 2] = circleCoords[firstOuterIndex + 2]

                return createFloatBuffer(circleCoords)
            }
        }

        /**
         * Updates the ripple's state and draws it.
         * @return true if the ripple is still active, false if it has finished.
         */
        fun updateAndDraw(
            posHandle: Int, colHandle: Int, mvpHandle: Int, timeHandle: Int, // Include time handle
            vMatrix: FloatArray, pMatrix: FloatArray, modMatrix: FloatArray, mvpCalcMatrix: FloatArray
        ): Boolean {
            val now = SystemClock.uptimeMillis()
            val elapsedTime = now - startTime

            // Check if effect duration is over or if handles are invalid
            if (elapsedTime > duration || posHandle < 0 || colHandle < 0 || mvpHandle < 0 || timeHandle < 0) {
                return false // Ripple finished or cannot be drawn
            }

            // Calculate animation progress (0.0 to 1.0)
            val progress = (elapsedTime / duration).coerceIn(0f, 1f)

            // --- Prepare Matrices ---
            // Model matrix: Translate to ripple center, scale (radius is handled by shader effect now)
            Matrix.setIdentityM(modMatrix, 0)
            Matrix.translateM(modMatrix, 0, centerX, centerY, 0f)
            // Scale slightly larger than maxRadius to ensure shader effect covers the area
            Matrix.scaleM(modMatrix, 0, maxRadius * 1.1f, maxRadius * 1.1f, 1.0f)

            // MVP matrix: Project * View * Model
            Matrix.multiplyMM(mvpCalcMatrix, 0, vMatrix, 0, modMatrix, 0) // mvp = V * M
            Matrix.multiplyMM(mvpCalcMatrix, 0, pMatrix, 0, mvpCalcMatrix, 0) // mvp = P * (V * M)

            // --- Set Shader Uniforms and Attributes ---
            vertexBuffer.position(0) // Reset buffer position
            GLES20.glVertexAttribPointer(posHandle, POSITION_COMPONENT_COUNT_XYZ, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            checkGLError("Ripple glVertexAttribPointer vPosition")
            GLES20.glEnableVertexAttribArray(posHandle)
            checkGLError("Ripple glEnableVertexAttribArray vPosition")

            // Pass base color (fragment shader will modify alpha)
            GLES20.glUniform4fv(colHandle, 1, color, 0)
            checkGLError("Ripple glUniform4fv vColor")

            // Pass MVP matrix
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpCalcMatrix, 0)
            checkGLError("Ripple glUniformMatrix4fv uMVPMatrix")

            // Pass time progress (0.0 to 1.0)
            GLES20.glUniform1f(timeHandle, progress)
            checkGLError("Ripple glUniform1f u_Time")

            // --- Draw ---
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, vertexCount)
            checkGLError("Ripple glDrawArrays")

            // --- Clean up ---
            GLES20.glDisableVertexAttribArray(posHandle)
            checkGLError("Ripple glDisableVertexAttribArray vPosition")

            return true // Ripple is still active
        }
    }
    private val activeRipples = mutableListOf<Ripple>()
    private val ripplesLock = Any() // Lock for synchronizing access to activeRipples


    // --- Renderer Lifecycle Methods ---

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, "onSurfaceCreated: Setting up GL resources.")
        // Reset state in case of context loss
        rippleShaderProgram = 0
        backgroundShaderProgram = 0
        backgroundTextureId = 0
        // Reset handles
        ripplePositionHandle = -1; rippleColorHandle = -1; rippleMvpMatrixHandle = -1; rippleTimeHandle = -1
        backgroundPositionHandle = -1; backgroundTexCoordHandle = -1; backgroundTextureUniformHandle = -1

        // --- Setup Background ---
        try {
            // Fallback clear color (in case texture loading fails)
            GLES20.glClearColor(0.1f, 0.1f, 0.2f, 1.0f) // Dark blue fallback
            checkGLError("glClearColor Fallback")

            // *** IMPORTANT: Replace R.drawable.background with your actual drawable resource ID ***
            backgroundTextureId = loadTexture(context, R.drawable.background) // Or R.drawable.your_jpg_file
            if (backgroundTextureId == 0) {
                Log.e(TAG, "Failed to load background texture. Will use fallback clear color.")
            } else {
                Log.i(TAG, "Background texture loaded successfully: ID=$backgroundTextureId")
            }

            // Create shader program for background
            val bgVertexCode = readTextFileFromAssets(context, BACKGROUND_VERTEX_SHADER_PATH)
            val bgFragmentCode = readTextFileFromAssets(context, BACKGROUND_FRAGMENT_SHADER_PATH)
            if (bgVertexCode != null && bgFragmentCode != null) {
                backgroundShaderProgram = createAndLinkProgram(bgVertexCode, bgFragmentCode)
                if (backgroundShaderProgram != 0) {
                    // Get handles for background shader variables
                    backgroundPositionHandle = GLES20.glGetAttribLocation(backgroundShaderProgram, "a_Position")
                    backgroundTexCoordHandle = GLES20.glGetAttribLocation(backgroundShaderProgram, "a_TexCoord")
                    backgroundTextureUniformHandle = GLES20.glGetUniformLocation(backgroundShaderProgram, "u_Texture")
                    Log.i(TAG, "Background shader program created and handles obtained: $backgroundShaderProgram")
                    // Basic handle validation
                    if (backgroundPositionHandle == -1 || backgroundTexCoordHandle == -1 || backgroundTextureUniformHandle == -1) {
                        Log.e(TAG, "Failed to get all background shader handles. Pos=$backgroundPositionHandle, TexCoord=$backgroundTexCoordHandle, TexUniform=$backgroundTextureUniformHandle")
                        GLES20.glDeleteProgram(backgroundShaderProgram)
                        backgroundShaderProgram = 0
                    }
                } else { Log.e(TAG, "Failed to create background shader program.") }
            } else { Log.e(TAG, "Failed to read background shader source.") }

            // Create Vertex Buffer for background quad (XY positions + ST texture coordinates)
            // Stride = (2 pos + 2 tex) * bytes_per_float
            val stride = (POSITION_COMPONENT_COUNT_XY + TEX_COORD_COMPONENT_COUNT) * BYTES_PER_FLOAT
            val quadVertices = floatArrayOf(
                // X,    Y,    S,    T
                -1.0f,  1.0f, 0.0f, 0.0f, // Top Left
                -1.0f, -1.0f, 0.0f, 1.0f, // Bottom Left
                1.0f, -1.0f, 1.0f, 1.0f, // Bottom Right

                1.0f, -1.0f, 1.0f, 1.0f, // Bottom Right
                1.0f,  1.0f, 1.0f, 0.0f, // Top Right
                -1.0f,  1.0f, 0.0f, 0.0f  // Top Left
            )
            backgroundVertexBuffer = createFloatBuffer(quadVertices)

        } catch (e: Exception) {
            Log.e(TAG, "Critical error during background setup", e)
            // Consider preventing rendering if background setup fails critically
        }

        // --- Setup Ripples ---
        try {
            val rippleVertexCode = readTextFileFromAssets(context, RIPPLE_VERTEX_SHADER_PATH)
            val rippleFragmentCode = readTextFileFromAssets(context, RIPPLE_FRAGMENT_SHADER_PATH)
            if (rippleVertexCode != null && rippleFragmentCode != null) {
                rippleShaderProgram = createAndLinkProgram(rippleVertexCode, rippleFragmentCode)
                if (rippleShaderProgram != 0) {
                    // Get handles for ripple shader variables
                    ripplePositionHandle = GLES20.glGetAttribLocation(rippleShaderProgram, "vPosition")
                    rippleColorHandle = GLES20.glGetUniformLocation(rippleShaderProgram, "vColor")
                    rippleMvpMatrixHandle = GLES20.glGetUniformLocation(rippleShaderProgram, "uMVPMatrix")
                    rippleTimeHandle = GLES20.glGetUniformLocation(rippleShaderProgram, "u_Time")
                    Log.i(TAG, "Ripple shader program created and handles obtained: $rippleShaderProgram")
                    // Basic handle validation
                    if (ripplePositionHandle == -1 || rippleColorHandle == -1 || rippleMvpMatrixHandle == -1 || rippleTimeHandle == -1) {
                        Log.e(TAG, "Failed to get all ripple shader handles. Pos=$ripplePositionHandle, Color=$rippleColorHandle, MVP=$rippleMvpMatrixHandle, Time=$rippleTimeHandle")
                        GLES20.glDeleteProgram(rippleShaderProgram)
                        rippleShaderProgram = 0
                    }
                } else { Log.e(TAG, "Failed to create ripple shader program.") }
            } else { Log.e(TAG, "Failed to read ripple shader source.") }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during ripple setup", e)
            // Consider preventing rendering if ripple setup fails critically
        }

        // --- Final GL State ---
        GLES20.glEnable(GLES20.GL_BLEND) // Enable blending for ripples
        checkGLError("glEnable GL_BLEND")
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA) // Standard alpha blending
        checkGLError("glBlendFunc")
        // Depth testing is usually not needed for 2D wallpaper like this
        // GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        Log.i(TAG, "OpenGL Surface Created Finished.")
    }

    /** Helper to create, compile, link, and validate a shader program. */
    private fun createAndLinkProgram(vertexShaderCode: String, fragmentShaderCode: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        if (vertexShader == 0 || fragmentShader == 0) {
            if (vertexShader != 0) GLES20.glDeleteShader(vertexShader)
            if (fragmentShader != 0) GLES20.glDeleteShader(fragmentShader)
            return 0 // Return 0 if shaders failed to compile
        }

        val program = GLES20.glCreateProgram()
        if (program == 0) {
            Log.e(TAG, "Cannot create GL program.")
            checkGLError("glCreateProgram")
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return 0
        }
        GLES20.glAttachShader(program, vertexShader)
        checkGLError("glAttachShader Vertex to program $program")
        GLES20.glAttachShader(program, fragmentShader)
        checkGLError("glAttachShader Fragment to program $program")
        GLES20.glLinkProgram(program)
        checkGLError("glLinkProgram $program")

        // Shaders can be deleted after linking
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        if (!checkProgramLinkStatus(program)) { // Checks link status and deletes program if failed
            Log.e(TAG, "Program linking failed for program $program.")
            return 0 // Return 0 if linking failed
        }

        // Optional: Validate program (useful for debugging, can be slow)
        // GLES20.glValidateProgram(program);
        // val validateStatus = IntArray(1)
        // GLES20.glGetProgramiv(program, GLES20.GL_VALIDATE_STATUS, validateStatus, 0)
        // if (validateStatus[0] == 0) {
        //     Log.e(TAG, "Error validating program $program: ${GLES20.glGetProgramInfoLog(program)}")
        //     GLES20.glDeleteProgram(program)
        //     return 0
        // }

        Log.i(TAG, "Successfully created and linked program $program")
        return program
    }


    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        if (height <= 0 || width <= 0) {
            Log.w(TAG, "onSurfaceChanged received invalid dimensions: $width x $height")
            return
        }
        screenWidth = width
        screenHeight = height
        aspectRatio = width.toFloat() / height.toFloat()

        GLES20.glViewport(0, 0, width, height)
        checkGLError("glViewport")

        // Orthographic projection suitable for 2D wallpaper
        Matrix.orthoM(projectionMatrix, 0, -aspectRatio, aspectRatio, -1f, 1f, -1f, 1f)
        // Simple view matrix looking straight ahead
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1.0f, 0.0f)

        Log.i(TAG, "OpenGL Surface Changed: $width x $height, AspectRatio: $aspectRatio")
    }

    override fun onDrawFrame(gl: GL10?) {
        // Clear the buffers
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT /* | GLES20.GL_DEPTH_BUFFER_BIT */)
        checkGLError("glClear")

        // --- 1. Draw Background ---
        // Check if background resources are valid before attempting to draw
        if (backgroundShaderProgram > 0 && backgroundTextureId > 0 &&
            backgroundPositionHandle != -1 && backgroundTexCoordHandle != -1 && backgroundTextureUniformHandle != -1)
        {
            GLES20.glUseProgram(backgroundShaderProgram)
            checkGLError("BG: glUseProgram $backgroundShaderProgram")

            // Set the active texture unit to texture unit 0
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            checkGLError("BG: glActiveTexture")
            // Bind the background texture to this unit
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTextureId)
            checkGLError("BG: glBindTexture $backgroundTextureId")
            // Tell the texture uniform sampler to use this texture unit (unit 0)
            GLES20.glUniform1i(backgroundTextureUniformHandle, 0)
            checkGLError("BG: glUniform1i u_Texture")

            // --- Set up vertex attributes for background quad ---
            // Calculate stride: (2 position floats + 2 texture coord floats) * bytes per float
            val stride = (POSITION_COMPONENT_COUNT_XY + TEX_COORD_COMPONENT_COUNT) * BYTES_PER_FLOAT

            // Position Attribute (X, Y)
            backgroundVertexBuffer.position(0) // Start at the beginning of the buffer for position
            GLES20.glVertexAttribPointer(
                backgroundPositionHandle,
                POSITION_COMPONENT_COUNT_XY, // Size (X, Y)
                GLES20.GL_FLOAT,
                false, // Normalized
                stride, // Stride between vertices
                backgroundVertexBuffer
            )
            checkGLError("BG: glVertexAttribPointer a_Position")
            GLES20.glEnableVertexAttribArray(backgroundPositionHandle)
            checkGLError("BG: glEnableVertexAttribArray a_Position")

            // Texture Coordinate Attribute (S, T)
            // Offset by 2 floats (X, Y) to get to the S, T coordinates
            backgroundVertexBuffer.position(POSITION_COMPONENT_COUNT_XY)
            GLES20.glVertexAttribPointer(
                backgroundTexCoordHandle,
                TEX_COORD_COMPONENT_COUNT, // Size (S, T)
                GLES20.GL_FLOAT,
                false, // Normalized
                stride, // Stride between vertices
                backgroundVertexBuffer
            )
            checkGLError("BG: glVertexAttribPointer a_TexCoord")
            GLES20.glEnableVertexAttribArray(backgroundTexCoordHandle)
            checkGLError("BG: glEnableVertexAttribArray a_TexCoord")

            // Draw the two triangles that make up the quad
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6) // 6 vertices define two triangles
            checkGLError("BG: glDrawArrays")

            // --- Clean up background attributes ---
            GLES20.glDisableVertexAttribArray(backgroundPositionHandle)
            GLES20.glDisableVertexAttribArray(backgroundTexCoordHandle)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0) // Unbind texture
        } else {
            // Log.v(TAG, "Skipping background draw - invalid resources.")
        }


        // --- 2. Draw Ripples (on top of the background) ---
        // Check if ripple resources are valid
        if (rippleShaderProgram > 0 && ripplePositionHandle != -1 && rippleColorHandle != -1 &&
            rippleMvpMatrixHandle != -1 && rippleTimeHandle != -1)
        {
            GLES20.glUseProgram(rippleShaderProgram)
            checkGLError("Ripple: glUseProgram $rippleShaderProgram")

            // Ensure blending is enabled for transparent ripples
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            // Iterate and draw active ripples safely
            synchronized(ripplesLock) {
                val iterator = activeRipples.iterator()
                while (iterator.hasNext()) {
                    val ripple = iterator.next()
                    // Pass all required handles and matrices to the ripple's draw function
                    if (!ripple.updateAndDraw(
                            ripplePositionHandle, rippleColorHandle, rippleMvpMatrixHandle, rippleTimeHandle,
                            viewMatrix, projectionMatrix, modelMatrix, mvpMatrix
                        )
                    ) {
                        iterator.remove() // Remove ripple if it finished
                    }
                }
            }
        } else {
            // Log.v(TAG, "Skipping ripple draw - invalid resources.")
        }

        // Final error check for the frame
        checkGLError("End of onDrawFrame")
    }

    // --- Touch Handling ---
    /** Handles touch events, converting screen coordinates to GL coordinates and adding a ripple. */
    fun handleTouch(screenX: Float, screenY: Float) {
        // Ensure surface dimensions are valid
        if (screenWidth <= 0 || screenHeight <= 0) {
            Log.w(TAG, "handleTouch called before surface dimensions are set.")
            return
        }

        // Convert screen coordinates (pixels, top-left origin) to
        // OpenGL normalized device coordinates (-aspect..aspect for X, -1..1 for Y, center origin)
        val glX = (screenX / screenWidth * 2.0f - 1.0f) * aspectRatio
        // Invert Y because screen Y increases downwards, GL Y increases upwards
        val glY = -(screenY / screenHeight * 2.0f - 1.0f)

        // Add a new ripple safely
        synchronized(ripplesLock) {
            // Optional: Limit the maximum number of concurrent ripples for performance
            if (activeRipples.size < 25) {
                activeRipples.add(Ripple(glX, glY))
                // Log.v(TAG, "Ripple added at GL ($glX, $glY). Count: ${activeRipples.size}")
            } else {
                Log.d(TAG, "Maximum ripple count reached. Touch ignored.")
            }
        }
    }

    /** Optional: Call this from Engine's onDestroy to explicitly release GL resources */
    fun releaseResources() {
        Log.i(TAG, "Releasing OpenGL resources...")
        // Delete shader programs
        if (rippleShaderProgram > 0) {
            GLES20.glDeleteProgram(rippleShaderProgram)
            rippleShaderProgram = 0
        }
        if (backgroundShaderProgram > 0) {
            GLES20.glDeleteProgram(backgroundShaderProgram)
            backgroundShaderProgram = 0
        }
        // Delete textures
        if (backgroundTextureId > 0) {
            val textures = intArrayOf(backgroundTextureId)
            GLES20.glDeleteTextures(1, textures, 0)
            backgroundTextureId = 0
        }
        // Delete buffers if needed (though they are managed by GC eventually)
        Log.i(TAG, "OpenGL resources released.")
    }
}
