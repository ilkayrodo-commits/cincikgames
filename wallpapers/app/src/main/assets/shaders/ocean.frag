// Okyanus Altı: yüzeyden süzülen ışık huzmeleri, kostik desenler,
// yükselen kabarcıklar ve sallanan yosunlar.

float caustic(vec2 p, float t) {
    float c = 0.0;
    vec2 q = p;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        q = p * (1.0 + fi * 0.7) + vec2(t * (0.15 + fi * 0.05), -t * 0.1);
        vec2 w = vec2(vnoise(q * 2.0 + t * 0.3), vnoise(q * 2.0 + 5.0 - t * 0.25));
        float n = vnoise(q * 3.0 + w * 2.0);
        c += pow(1.0 - abs(n * 2.0 - 1.0), 6.0) / (1.0 + fi);
    }
    return c;
}

void main() {
    float aspect = u_res.x / u_res.y;
    vec2 p = vec2(gl_FragCoord.x / u_res.y, v_uv.y);
    float t = u_time;
    vec3 deep = vec3(0.0, 0.03, 0.08);
    vec3 shallow = vec3(0.02, 0.35, 0.45);
    vec3 col = mix(deep, shallow, pow(p.y, 1.6));
    // ışık huzmeleri (yukarıdan, hafif açılı)
    vec2 src = vec2(aspect * 0.6 + (u_offset - 0.5) * 0.2, 1.25);
    vec2 d = p - src;
    float ang = atan(d.x, -d.y);
    float rays = 0.5 + 0.5 * sin(ang * 26.0 + t * 0.4) * sin(ang * 13.0 - t * 0.3 + 1.0);
    rays *= vnoise(vec2(ang * 9.0, t * 0.2));
    col += vec3(0.4, 0.8, 0.85) * rays * smoothstep(0.0, 1.0, p.y) * 0.35;
    // kostik ağı (üstte belirgin)
    col += vec3(0.5, 0.95, 1.0) * caustic(p * 2.2, t) * 0.18 * smoothstep(0.2, 1.0, p.y);
    // deniz tabanı
    float floorH = 0.12 + 0.04 * vnoise(vec2(p.x * 4.0, 1.0)) + 0.02 * sin(p.x * 9.0);
    if (p.y < floorH) {
        col = mix(vec3(0.05, 0.12, 0.13), vec3(0.18, 0.28, 0.24), smoothstep(0.0, floorH, p.y));
        col += vec3(0.4, 0.8, 0.8) * caustic(p * 5.0, t * 1.3) * 0.12;
    }
    // yosunlar
    for (int i = 0; i < 9; i++) {
        float fi = float(i);
        float bx = aspect * (fi + 0.3 + 0.4 * hash12(vec2(fi, 1.0))) / 9.0;
        float hgt = 0.25 + 0.30 * hash12(vec2(fi, 2.0));
        float yy = clamp(p.y / hgt, 0.0, 1.0);
        float sway = sin(t * 0.8 + fi * 1.3 + p.y * 6.0) * 0.035 * yy * yy + 0.01 * sin(p.y * 25.0 + t * 2.0 + fi) * yy;
        float w = 0.010 * (1.0 - yy * 0.6);
        float dx = abs(p.x - bx - sway);
        if (p.y < hgt && dx < w) {
            col = mix(col, vec3(0.02, 0.16, 0.08) + vec3(0.05, 0.25, 0.1) * yy, 0.9);
        }
    }
    // kabarcıklar
    for (int i = 0; i < 14; i++) {
        float fi = float(i);
        float sp = 0.06 + 0.08 * hash12(vec2(fi, 5.0));
        float by = fract(t * sp + hash12(vec2(fi, 6.0))) * 1.25 - 0.1;
        float bx = aspect * hash12(vec2(fi, 7.0)) + 0.015 * sin(t * 2.0 + fi * 3.0);
        float r = 0.004 + 0.008 * hash12(vec2(fi, 8.0));
        float bd = length(p - vec2(bx, by));
        float ring = smoothstep(r, r * 0.7, bd) - smoothstep(r * 0.7, r * 0.3, bd) * 0.7;
        col += vec3(0.7, 0.95, 1.0) * ring * 0.6;
        col += vec3(1.0) * smoothstep(r * 0.35, 0.0, length(p - vec2(bx - r * 0.35, by + r * 0.35))) * 0.5;
    }
    // süzülen parçacıklar
    vec2 g = (p + vec2(t * 0.005, t * 0.01)) * 60.0;
    vec2 id = floor(g);
    if (hash12(id) > 0.97) {
        float pd = length(fract(g) - 0.5);
        col += vec3(0.6, 0.9, 0.9) * smoothstep(0.08, 0.0, pd) * 0.4;
    }
    // dokunma: kabarcık patlaması gibi halka
    vec2 tdir;
    float dist = length(gl_FragCoord.xy - u_touch.xy) / u_res.y;
    float ringR = u_touch.z * 0.25;
    col += vec3(0.6, 0.95, 1.0) * smoothstep(0.012, 0.0, abs(dist - ringR)) * exp(-u_touch.z * 2.0) * 0.8;
    gl_FragColor = vec4(col, 1.0);
}
