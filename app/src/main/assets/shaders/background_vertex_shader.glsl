// File: assets/shaders/background_vertex_shader.glsl
// Nhiệm vụ: Vertex shader đơn giản để vẽ một hình chữ nhật texture phủ hết màn hình.
//           Chuyển tiếp vị trí đỉnh và tọa độ texture cho fragment shader.

// Input: Thuộc tính (attribute) của mỗi đỉnh được truyền từ ứng dụng Kotlin.
attribute vec4 a_Position;     // Vị trí đỉnh (đã ở trong tọa độ clip space, -1 đến 1).
attribute vec2 a_TexCoord;     // Tọa độ texture của đỉnh (tọa độ S, T, từ 0 đến 1).

// Output: Biến 'varying' để truyền dữ liệu đã nội suy cho fragment shader.
varying vec2 v_TexCoord;       // Truyền tọa độ texture đã nội suy.

// Hàm chính của vertex shader, chạy cho mỗi đỉnh.
void main() {
    // Vị trí đỉnh (a_Position) đã ở trong không gian clip (-1 đến 1)
    // nên không cần nhân với ma trận MVP, chỉ cần gán trực tiếp cho gl_Position.
    gl_Position = a_Position;

    // Truyền tọa độ texture (a_TexCoord) của đỉnh này vào biến varying v_TexCoord.
    // GPU sẽ tự động nội suy giá trị này cho từng pixel giữa các đỉnh.
    v_TexCoord = a_TexCoord;
}
