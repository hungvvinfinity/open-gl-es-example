// File: assets/shaders/vertex_shader.glsl (For Ripples)
uniform mat4 uMVPMatrix;
attribute vec4 vPosition;

varying vec2 v_RelativePos; // <-- KIỂM TRA DÒNG NÀY

void main() {
    gl_Position = uMVPMatrix * vPosition;
    v_RelativePos = vPosition.xy; // <-- Gán giá trị cho varying
}
