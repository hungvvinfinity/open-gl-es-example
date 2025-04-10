// File: assets/shaders/vertex_shader.glsl (Dùng cho hiệu ứng Ripple)
// Nhiệm vụ: Tính toán vị trí cuối cùng của đỉnh trong không gian clip
//           và truyền vị trí tương đối của đỉnh cho fragment shader.

// Input: Biến 'uniform' - giá trị không đổi cho tất cả các đỉnh trong một lệnh vẽ.
uniform mat4 uMVPMatrix;    // Ma trận Model-View-Projection tổng hợp từ Kotlin.

// Input: Biến 'attribute' - giá trị riêng cho từng đỉnh.
attribute vec4 vPosition;   // Thông tin vị trí của đỉnh (X,Y,Z,W),
// được cung cấp tương đối so với tâm ripple (0,0).

// Output: Biến 'varying' - giá trị được nội suy và truyền tới fragment shader.
varying vec2 v_RelativePos; // Truyền vị trí XY gốc (trước khi biến đổi) cho fragment shader.

// Hàm chính của vertex shader, chạy cho mỗi đỉnh được vẽ.
void main() {
    // Tính toán vị trí cuối cùng của đỉnh trong không gian clip (-1 đến 1).
    // Nhân vị trí gốc của đỉnh với ma trận MVP.
    // Ma trận MVP đã bao gồm phép di chuyển (translate) và tỷ lệ (scale) của ripple.
    gl_Position = uMVPMatrix * vPosition;

    // Lấy tọa độ XY gốc từ vPosition (là tọa độ trên vòng tròn đơn vị).
    // Truyền giá trị này vào biến varying v_RelativePos.
    // Fragment shader sẽ dùng giá trị này để tính khoảng cách từ tâm ripple.
    v_RelativePos = vPosition.xy;
}
