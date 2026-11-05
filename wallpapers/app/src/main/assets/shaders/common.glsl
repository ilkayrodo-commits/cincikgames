#ifdef GL_OES_standard_derivatives
#extension GL_OES_standard_derivatives : enable
#endif
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform vec2 u_res;        // çizim yüzeyi, piksel
uniform float u_time;      // saniye
uniform vec3 u_touch;      // xy: son dokunma (piksel, y yukarı), z: dokunmadan beri geçen saniye
uniform float u_offset;    // ana ekran sayfa kaydırma 0..1
uniform sampler2D u_tex0;
uniform sampler2D u_tex1;
uniform vec2 u_tex0Size;
varying vec2 v_uv;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}
float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}
float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 m = mat2(1.6, 1.2, -1.2, 1.6);
    for (int i = 0; i < 5; i++) {
        v += a * vnoise(p);
        p = m * p;
        a *= 0.5;
    }
    return v;
}
float sdSeg(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}
// Dokunma dalgası: 0..1 arası güç, dokunmadan sonra sönümlenir.
float touchPulse(vec2 frag, float radius, out vec2 dir) {
    vec2 d = frag - u_touch.xy;
    float dist = length(d);
    dir = dist > 0.0 ? d / dist : vec2(0.0);
    return exp(-dist / radius) * exp(-u_touch.z * 1.8);
}
