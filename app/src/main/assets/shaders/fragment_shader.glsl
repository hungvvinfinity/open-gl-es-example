// File: assets/shaders/fragment_shader.glsl (For Ripples)
precision mediump float;

uniform vec4 vColor;         // Base color (e.g., white) passed from Kotlin
uniform float u_Time;        // Ripple progress (0.0 to 1.0) passed from Kotlin

varying vec2 v_RelativePos;  // Relative position from vertex shader (-1 to 1 approx)

const float PI = 3.14159265359;
const float waveFrequency = 15.0; // Số lượng sóng
const float waveAmplitude = 0.8;  // Độ mạnh của hiệu ứng alpha sóng
const float fadeOutSpeed = 2.5;   // Tốc độ mờ dần tổng thể

void main() {
    // Tính khoảng cách từ tâm (0,0) của ripple đến fragment hiện tại
    // v_RelativePos đã có bán kính khoảng 1.0 ở rìa
    float dist = length(v_RelativePos); // dist sẽ từ 0.0 đến ~1.0

    // Tạo hiệu ứng sóng dựa trên khoảng cách và thời gian
    // sin(dist * freq - time * speed)
    // Giá trị sin từ -1 đến 1. Chuyển thành 0 đến 1 và áp dụng amplitude.
    float wave = (sin(dist * waveFrequency - u_Time * waveFrequency * 1.5) + 1.0) * 0.5; // 0.0 to 1.0
    float waveAlpha = mix(1.0 - waveAmplitude, 1.0, wave); // Alpha từ (1-amp) đến 1.0

    // Tính độ mờ dần tổng thể dựa trên thời gian (u_Time)
    // và một chút dựa trên khoảng cách (mờ ở rìa nhanh hơn)
    float fadeOut = pow(1.0 - u_Time, fadeOutSpeed) * (1.0 - dist * 0.2);
    fadeOut = clamp(fadeOut, 0.0, 1.0);

    // Kết hợp alpha của sóng và alpha mờ dần tổng thể
    float finalAlpha = vColor.a * waveAlpha * fadeOut;

    // Đặt màu cuối cùng
    gl_FragColor = vec4(vColor.rgb, finalAlpha);

    // Optional: Làm mềm rìa ngoài cùng của ripple
    // float edgeSoftness = 1.0 - smoothstep(0.95, 1.0, dist);
    // gl_FragColor.a *= edgeSoftness;

}