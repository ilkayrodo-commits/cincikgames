// Kuzey Işıkları: sivri karlı dağlar, göl yansıması, dalgalanan aurora perdeleri.

float ridged(float x, float seed) {
    float v = 0.0;
    float a = 0.5;
    float f = 1.0;
    for (int i = 0; i < 5; i++) {
        float n = vnoise(vec2(x * f + seed, seed * 1.7));
        n = 1.0 - abs(n * 2.0 - 1.0);
        v += a * n * n;
        f *= 2.1;
        a *= 0.5;
    }
    return v;
}

vec3 aurora(vec2 p, float t) {
    vec3 acc = vec3(0.0);
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float x = p.x * (1.0 + fi * 0.3) + fi * 3.7;
        float yc = 0.54 + fi * 0.08
                   + 0.10 * (fbm(vec2(x * 0.8 + t * 0.04, fi * 4.0 + t * 0.015)) - 0.5) * 2.0
                   + 0.025 * sin(x * 2.1 + t * 0.3 + fi);
        float above = p.y - yc;
        float body = smoothstep(-0.015, 0.0, above) * exp(-max(above, 0.0) * (7.0 + fi * 2.5));
        float rays = vnoise(vec2(x * 28.0 + 2.0 * sin(t * 0.2 + fi), p.y * 2.0 - t * 0.25));
        rays = 0.35 + 0.65 * rays * rays;
        float gaps = smoothstep(0.30, 0.65, fbm(vec2(x * 1.3 - t * 0.03, fi * 2.0)));
        float k = body * rays * gaps;
        vec3 low = mix(vec3(0.15, 1.0, 0.55), vec3(0.2, 0.9, 0.95), fi * 0.45);
        vec3 high = vec3(0.65, 0.25, 0.95);
        acc += mix(low, high, clamp(above * 6.0, 0.0, 1.0)) * k * (1.0 - fi * 0.25);
    }
    return acc;
}

vec3 scene(vec2 p, float t) {
    vec3 col = mix(vec3(0.01, 0.025, 0.07), vec3(0.03, 0.10, 0.15), smoothstep(1.0, 0.38, p.y));
    vec2 g = p * 150.0;
    vec2 id = floor(g);
    float r = hash12(id);
    if (r > 0.96) {
        float d = length(fract(g) - hash22(id) * 0.8 - 0.1);
        float tw = 0.6 + 0.4 * sin(t * (1.0 + r * 4.0) + r * 50.0);
        col += vec3(0.85, 0.9, 1.0) * smoothstep(0.12, 0.0, d) * tw * smoothstep(0.42, 0.65, p.y);
    }
    vec3 au = aurora(p, t);
    col += au;
    // aurora'nın aydınlattığı genel ışık (dağlara vuran)
    vec3 glow = aurora(vec2(p.x, 0.66), t) * 0.5 + aurora(vec2(p.x + 0.2, 0.7), t) * 0.3;

    float x1 = p.x + u_offset * 0.06;
    float x2 = p.x + u_offset * 0.14;
    float h1 = 0.375 + 0.20 * ridged(x1 * 6.5, 3.0);
    float h2 = 0.335 + 0.07 * ridged(x2 * 11.0, 11.0);
    // ufukta aurora'nın yansıyan yeşil ışıltısı
    col += vec3(0.05, 0.25, 0.18) * exp(-abs(p.y - h1) * 12.0) * 0.5;
    if (p.y < h1) {
        float face = smoothstep(h1 - 0.16, h1, p.y);
        vec3 rock = mix(vec3(0.015, 0.025, 0.045), vec3(0.09, 0.13, 0.19), face) + glow * 0.10 * face;
        float snow = smoothstep(h1 - 0.06, h1 - 0.012, p.y) * smoothstep(0.44, 0.50, h1);
        snow *= 0.55 + 0.45 * vnoise(vec2(x1 * 70.0, p.y * 40.0));
        vec3 snowc = vec3(0.42, 0.52, 0.62) + glow * 0.45;
        col = mix(rock, snowc, snow);
        // sırt çizgisi
        col += vec3(0.25, 0.35, 0.4) * smoothstep(0.004, 0.0, h1 - p.y) * 0.5;
    }
    if (p.y < h2) {
        col = vec3(0.012, 0.025, 0.04) + glow * 0.03;
        // çam ağaçları silüeti
        float tx = x2 * 120.0;
        float tid = floor(tx);
        float th = 0.012 + 0.02 * hash12(vec2(tid, 5.0));
        float tf = abs(fract(tx) - 0.5) * 2.0;
        if (p.y > h2 - 0.03 && p.y < h2 + th * (1.0 - tf)) col = vec3(0.005, 0.012, 0.02);
    } else {
        float tx = x2 * 120.0;
        float tid = floor(tx);
        float th = 0.012 + 0.02 * hash12(vec2(tid, 5.0));
        float tf = abs(fract(tx) - 0.5) * 2.0;
        if (p.y < h2 + th * (1.0 - tf) * step(0.4, hash12(vec2(tid, 2.0)))) col = vec3(0.005, 0.012, 0.02);
    }
    return col;
}

void main() {
    vec2 p = vec2(gl_FragCoord.x / u_res.y, v_uv.y);
    float t = u_time;
    float water = 0.30;
    vec3 col;
    if (p.y > water) {
        col = scene(p, t);
    } else {
        float depth = water - p.y;
        float rip = 0.006 * sin(p.y * 160.0 + t * 1.6 + 4.0 * vnoise(vec2(p.x * 12.0, p.y * 40.0 - t * 0.4)))
                    * (0.3 + depth * 3.0);
        vec2 rp = vec2(p.x + rip, water + depth + rip * 0.5);
        col = scene(rp, t) * vec3(0.55, 0.68, 0.78);
        col = mix(col, vec3(0.0, 0.015, 0.03), smoothstep(0.0, 0.30, depth) * 0.55);
        col += vec3(0.6, 0.8, 0.9) * 0.02 * smoothstep(0.004, 0.0, depth);
    }
    vec2 tdir;
    float tp = touchPulse(gl_FragCoord.xy, u_res.y * 0.25, tdir);
    col += vec3(0.2, 0.8, 0.5) * tp * 0.3;
    gl_FragColor = vec4(col, 1.0);
}
