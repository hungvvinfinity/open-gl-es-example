// File: assets/shaders/fragment_shader.glsl (Dùng cho hiệu ứng Ripple)
// Nhiệm vụ: Tính toán màu sắc cuối cùng cho mỗi pixel (fragment) của hiệu ứng ripple,
//           tạo hiệu ứng sóng và làm mờ dựa trên thời gian và vị trí.

// Đặt độ chính xác mặc định cho các phép tính dấu phẩy động.
precision mediump float;

// Input: Biến 'uniform' từ ứng dụng Kotlin.
uniform vec4 vColor;         // Màu gốc của ripple (ví dụ: màu trắng RGBA).
uniform float u_Time;        // Tiến trình animation của ripple (từ 0.0 đến 1.0).

// Input: Biến 'varying' được nội suy từ vertex shader.
varying vec2 v_RelativePos;  // Vị trí tương đối của fragment so với tâm ripple (khoảng -1 đến +1).

// Hằng số toán học và điều khiển hiệu ứng.
const float PI = 3.14159265359;      // Số Pi
const float waveFrequency = 15.0; // Tần số sóng (số lượng vòng sóng). Càng cao càng nhiều vòng.
const float waveAmplitude = 0.8;  // Biên độ sóng alpha (0 = không có sóng, 1 = alpha biến đổi từ 0 đến 1).
const float fadeOutSpeed = 2.5;   // Tốc độ mờ dần tổng thể (càng cao càng nhanh).
// *** GIẢM GIÁ TRỊ NÀY ĐỂ SÓNG CHẬM LẠI ***
const float waveSpeedFactor = 0.8; // <-- Giảm tốc độ di chuyển sóng (ví dụ: từ 1.5 xuống 0.8)

// Hàm chính của fragment shader, chạy cho mỗi pixel.
void main() {
    // Tính khoảng cách từ tâm ripple (0,0) đến fragment hiện tại.
    // length(v_RelativePos) tính độ dài vector v_RelativePos.
    // Vì v_RelativePos đến từ vòng tròn đơn vị, dist sẽ từ 0.0 đến khoảng 1.0 ở rìa.
    float dist = length(v_RelativePos);

    // Tính toán hiệu ứng sóng dựa trên hàm sin, khoảng cách và thời gian.
    // Công thức: sin( khoảng_cách * tần_số - thời_gian * tần_số * tốc_độ_sóng )
    // Kết quả của sin là [-1, 1]. Chuyển đổi về khoảng [0, 1].
    float wave = (sin(dist * waveFrequency - u_Time * waveFrequency * waveSpeedFactor) + 1.0) * 0.5; // Giá trị trong khoảng [0.0, 1.0]

    // Điều chỉnh alpha dựa trên sóng (tạo vòng sáng tối).
    // mix(a, b, t) nội suy giữa a và b dựa trên t.
    // Nội suy giữa (1.0 - amplitude) và 1.0 dựa trên giá trị 'wave'.
    // Khi 'wave' = 0, alpha là (1-amp). Khi 'wave' = 1, alpha là 1.0.
    float waveAlpha = mix(1.0 - waveAmplitude, 1.0, wave); // Alpha trong khoảng [(1-amp), 1.0]

    // Tính toán hệ số mờ dần tổng thể dựa trên thời gian (u_Time).
    // pow(1.0 - u_Time, speed) làm hiệu ứng mờ nhanh hơn về cuối.
    // Có thể thêm yếu tố khoảng cách để rìa mờ nhanh hơn một chút.
    float fadeOut = pow(1.0 - u_Time, fadeOutSpeed) * (1.0 - dist * 0.2);
    // Đảm bảo fadeOut luôn nằm trong khoảng [0.0, 1.0].
    fadeOut = clamp(fadeOut, 0.0, 1.0);

    // Kết hợp alpha gốc của màu (vColor.a), alpha điều chỉnh bởi sóng (waveAlpha),
    // và hệ số mờ dần tổng thể (fadeOut).
    float finalAlpha = vColor.a * waveAlpha * fadeOut;

    // *** LÀM MỀM RÌA NGOÀI ***
    // Sử dụng smoothstep để tạo hiệu ứng alpha giảm dần về 0 ở gần rìa ngoài (dist gần 1.0)
    // Ví dụ: bắt đầu mờ từ khoảng cách 0.85 và mờ hoàn toàn ở khoảng cách 1.0
    float edgeSoftness = 1.0 - smoothstep(0.85, 1.0, dist);
    // Nhân alpha cuối cùng với hệ số làm mềm cạnh
    finalAlpha *= edgeSoftness;

    // Gán màu cuối cùng cho fragment: giữ nguyên RGB gốc, sử dụng alpha đã tính toán.
    gl_FragColor = vec4(vColor.rgb, finalAlpha);
}
