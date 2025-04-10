// File: assets/shaders/background_fragment_shader.glsl
precision mediump float; // BẮT BUỘC phải có dòng này ở đầu

varying vec2 v_TexCoord;         // Tọa độ texture nhận từ vertex shader
uniform sampler2D u_Texture;     // Texture ảnh nền

void main() {
    // Lấy màu từ texture tại tọa độ tương ứng và gán cho pixel cuối cùng
    gl_FragColor = texture2D(u_Texture, v_TexCoord);
}
