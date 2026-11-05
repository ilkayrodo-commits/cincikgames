// Sakura: alacakaranlıkta sisli tepeler ve rüzgarda süzülen kiraz yaprakları.

float petal(vec2 q) {
    // q: yaprak yerel koordinatı (~[-1,1]); ucu çentikli damla
    q.y += 0.15;
    float r = length(q * vec2(1.0, 0.75));
    float shape = r - (0.55 + 0.25 * q.y);
    float notch = length(q - vec2(0.0, 1.18)) - 0.20;
    return max(shape, -notch);
}

vec4 petalLayer(vec2 p, float t, float scale, float speed, float seed) {
    vec2 g = p * scale;
    g += vec2(t * speed * 0.6, t * speed);
    vec2 id = floor(g);
    vec2 f = fract(g) - 0.5;
    float r = hash12(id + seed);
    if (r > 0.32) return vec4(0.0);
    vec2 o = (hash22(id + seed * 3.0) - 0.5) * 0.5;
    o.x += 0.15 * sin(t * 1.2 + r * 30.0);
    float a = t * (0.8 + r * 1.5) + r * 20.0;
    float flip = cos(t * (1.0 + r) + r * 9.0);   // 3B dönüş hissi
    mat2 rot = mat2(cos(a), -sin(a), sin(a), cos(a));
    vec2 q = rot * (f - o) / 0.17;
    q.x /= max(abs(flip), 0.25);
    float d = petal(q);
    float m = smoothstep(0.08, -0.02, d);
    vec3 c = mix(vec3(1.0, 0.72, 0.82), vec3(1.0, 0.88, 0.92), 0.5 + 0.5 * flip);
    c *= 0.85 + 0.15 * smoothstep(0.5, -0.5, q.y);
    return vec4(c, m);
}

void main() {
    float aspect = u_res.x / u_res.y;
    vec2 p = vec2(gl_FragCoord.x / u_res.y, v_uv.y);
    float t = u_time;
    // gökyüzü
    vec3 col = mix(vec3(1.0, 0.78, 0.72), vec3(0.45, 0.42, 0.72), smoothstep(0.3, 1.0, p.y));
    // güneş
    vec2 sc = vec2(aspect * 0.62, 0.58);
    float sd = length(p - sc);
    col = mix(col, vec3(1.0, 0.95, 0.85), smoothstep(0.075, 0.07, sd));
    col += vec3(1.0, 0.7, 0.6) * exp(-sd * 6.0) * 0.25;
    // katmanlı tepeler + sis
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float par = (u_offset - 0.5) * (0.03 + fi * 0.05);
        float x = p.x + par + fi * 1.3;
        float h = 0.50 - fi * 0.085 + 0.06 * (fbm(vec2(x * (1.2 + fi * 0.5), fi * 3.0)) - 0.5) * 2.0;
        vec3 hc = mix(vec3(0.82, 0.60, 0.72), vec3(0.28, 0.16, 0.30), fi / 3.0);
        if (p.y < h) col = hc;
        float mist = exp(-abs(p.y - h + 0.02) * 30.0) * 0.25;
        col = mix(col, vec3(1.0, 0.85, 0.88), mist * (1.0 - fi * 0.2));
    }
    // ağaç dalı (sol üstten)
    vec2 bp = p - vec2(-0.02, 0.86);
    float branch = 1e3;
    branch = min(branch, sdSeg(bp, vec2(0.0), vec2(0.30, -0.05)) - 0.012);
    branch = min(branch, sdSeg(bp, vec2(0.30, -0.05), vec2(0.42, -0.14)) - 0.006);
    branch = min(branch, sdSeg(bp, vec2(0.18, -0.03), vec2(0.27, 0.06)) - 0.005);
    branch = min(branch, sdSeg(bp, vec2(0.30, -0.05), vec2(0.38, 0.02)) - 0.004);
    float sway = 0.004 * sin(t * 0.8);
    col = mix(col, vec3(0.22, 0.10, 0.14), smoothstep(0.003, 0.0, branch));
    // dal üstündeki çiçek kümeleri
    for (int i = 0; i < 14; i++) {
        float fi = float(i);
        vec2 r = hash22(vec2(fi, 4.0));
        vec2 c = vec2(-0.02, 0.86) + vec2(0.05 + r.x * 0.38, -0.12 + r.y * 0.17) + vec2(sway, 0.0);
        vec2 fd = p - c;
        float fa = atan(fd.y, fd.x) + fi;
        float rr = 0.014 + 0.010 * hash12(vec2(fi, 9.0));
        float cd = length(fd) - rr * (0.78 + 0.22 * abs(cos(fa * 2.5)));
        // çiçek ortası
        col = mix(col, vec3(0.85, 0.35, 0.45), smoothstep(rr * 0.25, rr * 0.1, length(fd)) * 0.0);
        vec3 fc = mix(vec3(1.0, 0.70, 0.80), vec3(1.0, 0.86, 0.90), r.y);
        col = mix(col, fc, smoothstep(0.003, -0.002, cd));
        col = mix(col, vec3(0.9, 0.4, 0.5), smoothstep(rr * 0.28, rr * 0.12, length(fd)));
    }
    // yapraklar: uzak (bulanık/küçük) -> yakın (büyük)
    vec4 l;
    l = petalLayer(p, t, 16.0, 0.35, 1.0); col = mix(col, l.rgb, l.a * 0.55);
    l = petalLayer(p, t, 10.0, 0.45, 2.0); col = mix(col, l.rgb, l.a * 0.8);
    l = petalLayer(p, t, 7.0, 0.6, 3.0);   col = mix(col, l.rgb, l.a);
    // dokunma: rüzgar parlaması
    vec2 tdir;
    float tp = touchPulse(gl_FragCoord.xy, u_res.y * 0.2, tdir);
    col += vec3(1.0, 0.8, 0.9) * tp * 0.25;
    gl_FragColor = vec4(col, 1.0);
}
