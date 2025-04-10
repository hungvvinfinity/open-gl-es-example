# Demo Live Wallpaper OpenGL ES trên Android (Kotlin) - Phân tích Kỹ thuật

Project này minh họa cách triển khai một Live Wallpaper Android sử dụng OpenGL ES 2.0 cho việc rendering đồ họa tùy chỉnh (ảnh nền texture) và hiệu ứng tương tác (gợn sóng khi chạm). Tài liệu này tập trung vào các khía cạnh kỹ thuật và luồng hoạt động dành cho các nhà phát triển Android.

![Demo Video](demo.mp4)

## Tổng quan Kiến trúc

Live Wallpaper này được xây dựng dựa trên các thành phần chính sau:

1.  **`OpenGLWallpaperService` (Kế thừa `WallpaperService`):** Điểm vào của hệ thống, quản lý vòng đời của Service chạy nền. Nó chịu trách nhiệm tạo ra các instance `Engine`.
2.  **`OpenGLWallpaperService.Engine` (Kế thừa `WallpaperService.Engine`):** Quản lý một instance cụ thể của hình nền đang hiển thị. Nó cung cấp `SurfaceHolder` cho việc vẽ, xử lý các sự kiện vòng đời (`onCreate`, `onDestroy`, `onVisibilityChanged`), và nhận sự kiện chạm (`onTouchEvent`).
3.  **`GLSurfaceView` (Tùy chỉnh bên trong `Engine`):** Đảm nhiệm việc tạo và quản lý EGL context, Surface, và một luồng render riêng biệt (GL Thread). Nó đơn giản hóa việc tích hợp OpenGL ES vào hệ thống View của Android.
4.  **`OpenGLRenderer` (Triển khai `GLSurfaceView.Renderer`):** Nơi chứa logic rendering OpenGL ES chính. Nó giao tiếp trực tiếp với API OpenGL ES thông qua các hàm callback (`onSurfaceCreated`, `onSurfaceChanged`, `onDrawFrame`).
5.  **Shaders GLSL (`assets/shaders/`):** Các file text chứa mã nguồn Vertex và Fragment Shader chạy trên GPU để xử lý đỉnh và tô màu pixel.
6.  **Texture Ảnh nền (`res/drawable/`):** File ảnh được load vào thành một đối tượng Texture OpenGL.
7.  **`MainActivity`:** Một Activity đơn giản chỉ dùng để cung cấp giao diện khởi chạy trình chọn Live Wallpaper của hệ thống.

## Quy trình Rendering OpenGL ES và Shaders

OpenGL ES sử dụng một đường ống rendering để xử lý dữ liệu hình học và tạo ra hình ảnh cuối cùng. Các giai đoạn chính (được đơn giản hóa):

1.  **Vertex Data:** Dữ liệu đỉnh (vị trí, tọa độ texture) được nạp từ Kotlin vào các buffer.
2.  **Vertex Shader:** Chạy cho mỗi đỉnh, tính toán vị trí cuối cùng (`gl_Position`) và chuẩn bị dữ liệu (`varying`) cho giai đoạn sau.
3.  **Primitive Assembly:** Các đỉnh được ráp lại thành các hình cơ bản (tam giác).
4.  **Rasterization:** Chuyển đổi hình cơ bản thành các mảnh (fragments) tương ứng với các pixel trên màn hình. Nội suy giá trị `varying` cho từng fragment.
5.  **Fragment Shader:** Chạy cho mỗi fragment, tính toán màu sắc cuối cùng (`gl_FragColor`) dựa trên dữ liệu nội suy và uniforms.
6.  **Framebuffer Operations:** Các phép toán cuối cùng (blending, depth test - nếu bật) được thực hiện trước khi ghi màu vào bộ đệm khung (framebuffer).

### 1. Shaders GLSL (.glsl)

GLSL (OpenGL Shading Language) là ngôn ngữ C-like dùng để viết các shader program chạy trên GPU.

* **Kiểu dữ liệu:** `float`, `int`, `bool`, `vec2/3/4` (vector), `mat2/3/4` (ma trận), `sampler2D` (để truy cập texture).
* **Qualifiers:**
    * `attribute`: (Chỉ trong Vertex Shader) Dữ liệu đầu vào thay đổi theo từng đỉnh.
    * `uniform`: Dữ liệu đầu vào không đổi cho tất cả đỉnh/fragment trong một lệnh vẽ. Được truyền từ Kotlin.
    * `varying`: Dữ liệu đầu ra từ Vertex Shader, được nội suy và trở thành đầu vào cho Fragment Shader.
* **Biến Built-in:**
    * `gl_Position` (vec4): (Output bắt buộc của Vertex Shader) Vị trí đỉnh trong không gian clip.
    * `gl_FragColor` (vec4): (Output bắt buộc của Fragment Shader) Màu RGBA cuối cùng của fragment.
    * `gl_PointCoord` (vec2): (Chỉ trong Fragment Shader khi vẽ `GL_POINTS`) Tọa độ bên trong điểm đang vẽ.

#### a. Vertex Shaders

* **`background_vertex_shader.glsl`:**
    * **Mục đích:** Nhận vị trí đỉnh (`a_Position`) đã ở trong tọa độ clip (-1 đến 1) và tọa độ texture (`a_TexCoord`, 0 đến 1), sau đó chuyển tiếp chúng.
    * **Hoạt động:**
        * `gl_Position = a_Position;`: Gán thẳng vị trí đầu vào cho đầu ra vì không cần biến đổi thêm.
        * `v_TexCoord = a_TexCoord;`: Truyền tọa độ texture vào biến `varying v_TexCoord` để fragment shader sử dụng.
    * **Tùy chỉnh:** Thường không cần tùy chỉnh nhiều cho background toàn màn hình đơn giản.
* **`vertex_shader.glsl` (Ripple):**
    * **Mục đích:** Nhận vị trí đỉnh gốc của hình tròn (`vPosition`, tương đối so với tâm 0,0), áp dụng phép biến đổi Model-View-Projection (`uMVPMatrix`) để tính vị trí cuối cùng, và truyền vị trí tương đối gốc cho fragment shader.
    * **Hoạt động:**
        * `gl_Position = uMVPMatrix * vPosition;`: Nhân vị trí đỉnh với ma trận MVP để có vị trí trong không gian clip. Ma trận `uMVPMatrix` được tính toán trong Kotlin, bao gồm cả việc di chuyển ripple đến vị trí chạm và scale.
        * `v_RelativePos = vPosition.xy;`: Lấy tọa độ XY gốc (trên vòng tròn đơn vị) và gán vào `varying v_RelativePos`.
    * **Tùy chỉnh:** Có thể thêm các phép biến đổi phức tạp hơn (ví dụ: làm gợn sóng cả hình dạng đỉnh) hoặc truyền thêm dữ liệu (ví dụ: tính toán một hệ số nào đó dựa trên vị trí đỉnh).

#### b. Fragment Shaders

* **`background_fragment_shader.glsl`:**
    * **Mục đích:** Lấy mẫu màu từ texture ảnh nền tại tọa độ được nội suy.
    * **Hoạt động:**
        * `precision mediump float;`: Khai báo độ chính xác.
        * Nhận `varying vec2 v_TexCoord` (tọa độ texture đã nội suy).
        * Nhận `uniform sampler2D u_Texture` (đại diện cho texture ảnh nền).
        * `gl_FragColor = texture2D(u_Texture, v_TexCoord);`: Dùng hàm `texture2D` để lấy màu (vec4 RGBA) từ `u_Texture` tại tọa độ `v_TexCoord` và gán làm màu đầu ra.
    * **Tùy chỉnh:** Có thể áp dụng bộ lọc màu, điều chỉnh độ sáng/tương phản, hoặc kết hợp với các texture khác.
* **`fragment_shader.glsl` (Ripple):**
    * **Mục đích:** Tạo hiệu ứng sóng lan tỏa và mờ dần cho ripple.
    * **Hoạt động:**
        * `precision mediump float;`: Khai báo độ chính xác.
        * Nhận `uniform vec4 vColor` (màu gốc), `uniform float u_Time` (tiến trình 0-1), `varying vec2 v_RelativePos` (vị trí tương đối -1 đến 1).
        * `float dist = length(v_RelativePos);`: Tính khoảng cách từ tâm ripple (0,0) đến fragment hiện tại.
        * `float wave = (sin(dist * freq - u_Time * freq * speed) + 1.0) * 0.5;`: Tính hệ số sóng (0-1) dựa trên hàm `sin`, khoảng cách, tần số (`waveFrequency`), và thời gian/tốc độ (`u_Time`, `waveSpeedFactor`). Tạo ra các vòng sóng di chuyển ra ngoài.
        * `float waveAlpha = mix(1.0 - waveAmplitude, 1.0, wave);`: Nội suy alpha dựa trên `wave` và `waveAmplitude` để tạo hiệu ứng vòng sáng/tối.
        * `float fadeOut = pow(1.0 - u_Time, fadeOutSpeed) * ...;`: Tính hệ số mờ dần tổng thể dựa trên `u_Time` (mờ nhanh về cuối) và một chút theo `dist`.
        * `float edgeSoftness = 1.0 - smoothstep(0.85, 1.0, dist);`: Tính hệ số làm mềm rìa, giảm alpha mượt mà từ 1 về 0 khi `dist` đi từ 0.85 đến 1.0.
        * `float finalAlpha = vColor.a * waveAlpha * fadeOut * edgeSoftness;`: Kết hợp tất cả các hệ số alpha.
        * `gl_FragColor = vec4(vColor.rgb, finalAlpha);`: Xuất màu cuối cùng với RGB gốc và alpha đã tính toán.
    * **Tùy chỉnh:** Thay đổi các hằng số (`waveFrequency`, `waveAmplitude`, `fadeOutSpeed`, `waveSpeedFactor`), thay đổi hàm tính sóng (ví dụ: dùng `cos` hoặc các hàm phức tạp hơn), thay đổi cách tính `fadeOut` hoặc `edgeSoftness`.

---

## Tương tác và Luồng Dữ liệu (Kotlin <-> GLSL)

Sự tương tác chính diễn ra giữa `OpenGLRenderer` (Kotlin) và các Shader Program (GLSL chạy trên GPU).

### 1. Chuẩn bị và Kích hoạt (Chủ yếu trong `onSurfaceCreated`)

* **Load & Compile Shaders:** `readTextFileFromAssets` đọc file `.glsl`. `loadShader` dùng `glShaderSource` và `glCompileShader` để gửi mã nguồn cho driver biên dịch.
* **Link Program:** `createAndLinkProgram` dùng `glCreateProgram`, `glAttachShader`, `glLinkProgram` để tạo một chương trình thực thi hoàn chỉnh trên GPU từ các shader đã biên dịch.
* **Get Handles:** `glGetAttribLocation` và `glGetUniformLocation` lấy ID (handle) của các biến `attribute` và `uniform` trong program đã link, dựa vào tên biến trong mã GLSL. Các handle này cần được lưu lại.

### 2. Truyền Dữ liệu (Chủ yếu trong `onDrawFrame` và `Ripple.updateAndDraw`)

* **Chọn Program:** `glUseProgram(programHandle)` ra lệnh cho GPU sử dụng program nào cho các lệnh vẽ tiếp theo.
* **Uniforms (Kotlin -> GPU):** Dùng các hàm `glUniform*` để cập nhật giá trị cho các biến `uniform` trước khi vẽ.
    * `glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)`: Gửi ma trận 4x4. `count=1`, `transpose=false`.
    * `glUniform4fv(colorHandle, 1, colorArray, 0)`: Gửi vector 4 float (màu RGBA).
    * `glUniform1f(timeHandle, progress)`: Gửi một giá trị float đơn (thời gian).
    * `glUniform1i(textureUniformHandle, 0)`: Gửi một giá trị integer đơn. Thường dùng để chỉ định đơn vị texture (texture unit) mà `sampler2D` sẽ sử dụng (ở đây là unit 0).
* **Attributes (Kotlin -> GPU):** Dữ liệu đỉnh được chuẩn bị trong `FloatBuffer` ở bộ nhớ gốc.
    * `glVertexAttribPointer(handle, size, type, normalized, stride, buffer)`: Cấu hình cách OpenGL đọc dữ liệu từ buffer cho một attribute.
        * `handle`: Handle của attribute (`backgroundPositionHandle`, `ripplePositionHandle`...).
        * `size`: Số thành phần cho mỗi đỉnh (2 cho XY, 3 cho XYZ, 2 cho ST).
        * `type`: Kiểu dữ liệu (`GL_FLOAT`).
        * `normalized`: Dữ liệu có cần chuẩn hóa về [-1, 1] hoặc [0, 1] không (thường là `false` cho float position/texcoord).
        * `stride`: Khoảng cách (bytes) giữa dữ liệu của hai đỉnh liên tiếp trong buffer. Nếu dữ liệu xen kẽ (ví dụ: XYSTXYST...), stride = tổng kích thước (bytes) của một đỉnh. Nếu dữ liệu không xen kẽ hoặc chỉ có 1 attribute, stride = 0.
        * `buffer`: Đối tượng `FloatBuffer` chứa dữ liệu. Cần gọi `buffer.position(...)` trước nếu dữ liệu attribute không bắt đầu từ đầu buffer (ví dụ: khi đọc TexCoord sau Position trong buffer xen kẽ).
    * `glEnableVertexAttribArray(handle)`: Kích hoạt attribute để OpenGL sử dụng nó trong quá trình vẽ. Cần gọi `glDisableVertexAttribArray` sau khi vẽ xong.
* **Textures (Kotlin -> GPU):**
    * `loadTexture`: Tạo texture ID (`glGenTextures`), bind nó (`glBindTexture`), cài đặt tham số lọc/bao bọc (`glTexParameteri`), tải dữ liệu bitmap (`GLUtils.texImage2D`).
    * Khi vẽ (ví dụ: vẽ background):
        * `glActiveTexture(GL_TEXTURE0)`: Chọn một đơn vị texture để làm việc (ví dụ: unit 0).
        * `glBindTexture(GL_TEXTURE_2D, backgroundTextureId)`: Gắn texture ảnh nền vào đơn vị đang hoạt động.
        * `glUniform1i(backgroundTextureUniformHandle, 0)`: Nói cho uniform `sampler2D` (u_Texture) biết lấy mẫu từ đơn vị texture số 0.

### 3. Luồng Tương tác (Chạm -> Vẽ Ripple - Chi tiết)

1.  **Chạm (UI Thread):** `Engine.onTouchEvent` nhận tọa độ pixel.
2.  **Queue (UI -> GL Thread):** `glSurfaceView.queueEvent` gửi lambda sang GL Thread.
3.  **Xử lý Chạm (GL Thread):** `Renderer.handleTouch` chạy:
    * Chuyển đổi pixel -> tọa độ GL (NDC).
    * Tạo `Ripple(glX, glY, startTime=now)`.
    * Thêm vào `activeRipples` (dùng `synchronized`).
4.  **Vẽ Khung Hình (GL Thread - `onDrawFrame`):**
    * Vẽ background (dùng shader background, bind texture background...).
    * `glUseProgram(rippleShaderProgram)`.
    * Bật `GL_BLEND`.
    * `synchronized(ripplesLock)`: Duyệt `activeRipples`.
    * Với mỗi `ripple`:
        * Gọi `ripple.updateAndDraw(...)`.
        * Trong `updateAndDraw`:
            * `elapsedTime = now - ripple.startTime`.
            * `progress = (elapsedTime / duration).coerceIn(0f, 1f)`.
            * Tính `modelMatrix` (Translate đến `ripple.centerX/Y`, Scale theo `ripple.maxRadius`).
            * Tính `mvpMatrix = projectionMatrix * viewMatrix * modelMatrix`.
            * `glUniform1f(rippleTimeHandle, progress)` -> Gửi `progress` vào `uniform float u_Time`.
            * `glUniformMatrix4fv(rippleMvpMatrixHandle, ..., mvpMatrix, ...)` -> Gửi MVP vào `uniform mat4 uMVPMatrix`.
            * `glUniform4fv(rippleColorHandle, ..., ripple.color, ...)` -> Gửi màu gốc vào `uniform vec4 vColor`.
            * `ripple.vertexBuffer.position(0)`.
            * `glVertexAttribPointer(ripplePositionHandle, 3, ..., ripple.vertexBuffer)`.
            * `glEnableVertexAttribArray(ripplePositionHandle)`.
            * `glDrawArrays(GL_TRIANGLE_FAN, 0, ripple.vertexCount)` -> **Ra lệnh cho GPU thực thi.**
            * `glDisableVertexAttribArray(ripplePositionHandle)`.
            * Trả về `true` nếu `elapsedTime <= duration`.
        * Nếu `updateAndDraw` trả về `false`, xóa `ripple` khỏi danh sách.
5.  **Thực thi GPU (Cho lệnh `glDrawArrays` của ripple):**
    * **Vertex Shader:** Chạy `ripple.vertexCount` lần. Mỗi lần nhận 1 đỉnh từ `ripple.vertexBuffer` (thông qua `vPosition`), nhận `uMVPMatrix` từ `glUniformMatrix4fv`. Tính `gl_Position` và `v_RelativePos`.
    * **Fragment Shader:** Chạy cho mỗi pixel được phủ bởi các tam giác tạo thành ripple. Mỗi lần nhận `vColor` và `u_Time` (giống nhau cho mọi pixel trong lệnh vẽ này), nhận `v_RelativePos` (đã được nội suy). Thực hiện các phép tính `dist`, `wave`, `waveAlpha`, `fadeOut`, `edgeSoftness`, `finalAlpha`. Xuất màu cuối cùng ra `gl_FragColor`.
6.  **Hiển thị:** Framebuffer được cập nhật và hiển thị.
