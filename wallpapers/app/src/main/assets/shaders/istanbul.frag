// İstanbul Gecesi: hilal, yıldızlar, cami ve minare silüetleri,
// boğazda yansımalar ve geçen bir vapur.

float dome(float x, float cx, float r, float base) {
    float dx = x - cx;
    return abs(dx) < r ? base + sqrt(r * r - dx * dx) * 0.85 : 0.0;
}
float minaret(float x, float cx, float w, float top) {
    float dx = abs(x - cx);
    if (dx > w) return 0.0;
    return top + (w - dx) * 7.0;   // sivri külah
}

float skyline(float x) {
    // arka plan binaları
    float cell = floor(x * 40.0);
    float h = 0.30 + 0.025 * hash12(vec2(cell, 1.0)) + 0.015 * vnoise(vec2(x * 6.0, 2.0));
    // camiler (en/boy oranı ~0.45 ekranda yatayda 0..0.46 aralığı)
    // büyük cami
    h = max(h, dome(x, 0.22, 0.060, 0.315));
    h = max(h, dome(x, 0.155, 0.030, 0.31));
    h = max(h, dome(x, 0.285, 0.030, 0.31));
    h = max(h, dome(x, 0.22, 0.012, 0.365));
    h = max(h, minaret(x, 0.115, 0.0045, 0.44));
    h = max(h, minaret(x, 0.325, 0.0045, 0.44));
    h = max(h, minaret(x, 0.135, 0.0035, 0.41));
    h = max(h, minaret(x, 0.305, 0.0035, 0.41));
    // küçük cami (sağda)
    h = max(h, dome(x, 0.40, 0.032, 0.305));
    h = max(h, minaret(x, 0.445, 0.0035, 0.395));
    // Galata'yı andıran kule (solda)
    if (abs(x - 0.035) < 0.012) h = max(h, 0.37 + (0.012 - abs(x - 0.035)) * 4.0);
    return h;
}

vec3 sky(vec2 p, float t, float aspect) {
    vec3 col = mix(vec3(0.10, 0.07, 0.20), vec3(0.01, 0.02, 0.07), smoothstep(0.28, 1.0, p.y));
    col += vec3(0.35, 0.15, 0.25) * exp(-(p.y - 0.30) * 7.0) * 0.5;
    vec2 g = p * 140.0;
    vec2 id = floor(g);
    float r = hash12(id);
    if (r > 0.965) {
        float d = length(fract(g) - hash22(id) * 0.8 - 0.1);
        col += vec3(1.0, 0.95, 0.85) * smoothstep(0.1, 0.0, d) * (0.5 + 0.5 * sin(t * (1.0 + 3.0 * r) + r * 40.0)) * smoothstep(0.45, 0.7, p.y);
    }
    // hilal
    vec2 mc = vec2(aspect * 0.72, 0.80);
    float m1 = length(p - mc) - 0.055;
    float m2 = length(p - mc - vec2(0.022, 0.008)) - 0.047;
    float moon = max(m1, -m2);
    col += vec3(1.0, 0.95, 0.8) * exp(-max(length(p - mc) - 0.05, 0.0) * 14.0) * 0.15;
    col = mix(col, vec3(1.0, 0.96, 0.85), smoothstep(0.002, -0.001, moon));
    return col;
}

void main() {
    float aspect = u_res.x / u_res.y;
    vec2 p = vec2(gl_FragCoord.x / u_res.y, v_uv.y);
    float t = u_time;
    float water = 0.30;
    float x = p.x / aspect * 0.46 + (u_offset - 0.5) * 0.03;
    vec3 col;
    if (p.y > water) {
        col = sky(p, t, aspect);
        float h = skyline(x);
        if (p.y < h) {
            col = vec3(0.03, 0.025, 0.06);
            // pencere ışıkları
            vec2 w = vec2(x * 300.0, p.y * 240.0);
            vec2 wid = floor(w);
            float on = step(0.82, hash12(wid)) * step(p.y, 0.325);
            float flick = 0.7 + 0.3 * sin(t * 0.5 + hash12(wid + 3.0) * 30.0);
            vec2 wf = fract(w);
            col += vec3(1.0, 0.75, 0.35) * on * flick * step(0.25, wf.x) * step(wf.x, 0.75) * step(0.3, wf.y) * step(wf.y, 0.7);
            // cami aydınlatması
            // camiler gece aydınlatılmış: kubbe ve minarelerde sıcak ışık
            float lit = smoothstep(0.312, 0.335, p.y);
            col = mix(col, mix(vec3(0.30, 0.18, 0.09), vec3(0.55, 0.38, 0.20), smoothstep(0.31, 0.44, p.y)), lit * 0.75);
        } else {
            // minare uçlarında ışık
            col += vec3(1.0, 0.8, 0.5) * exp(-length(vec2((x - 0.115) * 2.2, p.y - 0.46)) * 300.0) * 0.8;
            col += vec3(1.0, 0.8, 0.5) * exp(-length(vec2((x - 0.325) * 2.2, p.y - 0.46)) * 300.0) * 0.8;
        }
    } else {
        float depth = water - p.y;
        float wave = vnoise(vec2(p.x * 25.0, p.y * 160.0 / (depth * 3.0 + 0.3) - t * 0.8));
        float rip = (wave - 0.5) * 0.02 * (0.2 + depth * 2.0);
        vec2 rp = vec2(p.x + rip, water + depth * 1.2);
        float rx = rp.x / aspect * 0.46 + (u_offset - 0.5) * 0.03;
        col = sky(rp, t, aspect) * 0.55;
        if (rp.y < skyline(rx)) col = vec3(0.02, 0.02, 0.05);
        // ışık sütunları (yansıyan pencereler)
        float streak = step(0.85, hash12(vec2(floor(rx * 300.0), 7.0))) * smoothstep(0.12, 0.0, depth);
        col += vec3(1.0, 0.7, 0.35) * streak * (0.3 + 0.7 * wave) * 0.45;
        // ay yansıması
        vec2 mc = vec2(aspect * 0.72, 0.0);
        float mx = abs(p.x + rip * 2.0 - mc.x);
        col += vec3(1.0, 0.95, 0.8) * smoothstep(0.03, 0.0, mx) * smoothstep(0.65, 0.35, wave) * 0.35 * smoothstep(0.3, 0.0, depth);
        col = mix(col, vec3(0.01, 0.015, 0.04), smoothstep(0.0, 0.3, depth) * 0.6);
        // vapur
        float ft = fract(t / 50.0);
        float fx = mix(-0.15, aspect + 0.15, ft);
        vec2 fp = p - vec2(fx, 0.205);
        float hull = max(abs(fp.x) - 0.06, abs(fp.y) - 0.008);
        hull = max(hull, -(fp.y + fp.x * 0.15 - 0.006) * 0.0 - 1.0);
        float cabin = max(abs(fp.x + 0.005) - 0.04, abs(fp.y - 0.013) - 0.006);
        float stack = max(abs(fp.x - 0.01) - 0.004, abs(fp.y - 0.025) - 0.008);
        float ferry = min(min(hull, cabin), stack);
        col = mix(col, vec3(0.02, 0.02, 0.03), smoothstep(0.001, 0.0, ferry));
        vec2 wl = vec2(fract((fp.x + 0.04) * 120.0) - 0.5, fp.y - 0.013);
        float win = step(abs(fp.x + 0.005), 0.036) * smoothstep(0.003, 0.0, abs(wl.y)) * step(abs(wl.x), 0.25);
        col += vec3(1.0, 0.85, 0.5) * win;
        // vapur ışığının yansıması
        col += vec3(1.0, 0.8, 0.45) * smoothstep(0.05, 0.0, abs(fp.x)) * smoothstep(0.0, -0.06, fp.y) * smoothstep(-0.10, -0.02, fp.y) * 0.25 * wave;
    }
    // dokunma: havai fişek parıltısı
    vec2 tdir;
    float tp = touchPulse(gl_FragCoord.xy, u_res.y * 0.05, tdir);
    col += vec3(1.0, 0.7, 0.4) * tp * 1.2;
    gl_FragColor = vec4(col, 1.0);
}
