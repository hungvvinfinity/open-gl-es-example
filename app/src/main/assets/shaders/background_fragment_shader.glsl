// File: assets/shaders/background_fragment_shader.glsl
// Nhiệm vụ: Lấy mẫu màu từ texture ảnh nền và xuất ra màu cuối cùng cho pixel.

// Đặt độ chính xác mặc định cho các phép tính dấu phẩy động (float).
// 'mediump' là mức trung bình, cân bằng giữa hiệu năng và độ chính xác.
precision mediump float;

// Input: Biến 'varying' được nội suy từ vertex shader.
// 'varying' nghĩa là giá trị này sẽ khác nhau cho mỗi pixel (fragment)
// và được tính toán dựa trên giá trị tại các đỉnh gần nhất.
varying vec2 v_TexCoord; // Tọa độ texture (S, T) đã được nội suy cho fragment này.

// Input: Biến 'uniform' được truyền vào từ ứng dụng Kotlin.
// 'uniform' nghĩa là giá trị này giống nhau cho tất cả các pixel được xử lý bởi shader này trong một lệnh vẽ.
// 'sampler2D' là kiểu dữ liệu đặc biệt để truy cập texture 2D.
uniform sampler2D u_Texture; // Đơn vị texture chứa ảnh nền.

// Hàm chính của fragment shader, chạy cho mỗi pixel được vẽ.
void main() {
    // Lấy mẫu màu (sample) từ texture 'u_Texture' tại tọa độ texture 'v_TexCoord'.
    // Hàm texture2D trả về một vec4 (RGBA) đại diện cho màu tại điểm đó trên ảnh.
    // Gán màu lấy được này làm màu cuối cùng cho fragment (pixel) hiện tại.
    gl_FragColor = texture2D(u_Texture, v_TexCoord);
}
