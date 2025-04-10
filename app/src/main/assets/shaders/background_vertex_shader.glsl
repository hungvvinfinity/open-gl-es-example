// File: assets/shaders/background_vertex_shader.glsl
// Shader đơn giản để vẽ texture toàn màn hình

attribute vec4 a_Position;     // Vertex position (in screen coordinates, e.g., -1 to 1)
attribute vec2 a_TexCoord;     // Texture coordinate (0 to 1)

varying vec2 v_TexCoord;       // Pass texture coordinate to fragment shader

void main() {
    // Vị trí không cần biến đổi vì đã là tọa độ màn hình
    gl_Position = a_Position;
    // Truyền tọa độ texture
    v_TexCoord = a_TexCoord;
}