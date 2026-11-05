// Lav Lambası: yumuşak, birbirine karışan sıcak renkli damlalar.

void main() {
    float aspect = u_res.x / u_res.y;
    vec2 p = vec2(gl_FragCoord.x / u_res.y, v_uv.y);
    float t = u_time * 0.6;
    float field = 0.0;
    vec3 tint = vec3(0.0);
    for (int i = 0; i < 9; i++) {
        float fi = float(i);
        vec2 r = hash22(vec2(fi, 2.3));
        float speed = 0.05 + 0.05 * r.y;
        // aşağı-yukarı yavaş yolculuk
        float y = 0.5 + 0.55 * sin(t * speed * 6.2831 * 0.25 + fi * 1.9);
        float x = aspect * (0.2 + 0.6 * r.x) + 0.08 * sin(t * 0.3 + fi * 2.7);
        float rad = 0.055 + 0.050 * hash12(vec2(fi, 7.7));
        rad *= 1.0 + 0.15 * sin(t * 0.8 + fi);
        vec2 d = p - vec2(x, y);
        d.y *= 0.9;
        float f = rad * rad / (dot(d, d) + 1e-4);
        f = f * sqrt(f);
        field += f;
        vec3 c = mix(vec3(1.0, 0.25, 0.35), vec3(1.0, 0.62, 0.15), hash12(vec2(fi, 3.3)));
        tint += c * f;
    }
    // dokunma: yeni bir damla iter
    vec2 tdir;
    float tp = touchPulse(gl_FragCoord.xy, u_res.y * 0.15, tdir);
    field += tp * 1.5;
    tint += vec3(1.0, 0.4, 0.6) * tp * 1.5;
    tint /= max(field, 1e-3);

    vec3 bgTop = vec3(0.18, 0.04, 0.22);
    vec3 bgBot = vec3(0.45, 0.06, 0.18);
    vec3 col = mix(bgBot, bgTop, p.y);
    // lambanın içindeki sıvı ışıltısı
    col += vec3(0.4, 0.1, 0.2) * (1.0 - p.y) * 0.25;
    float edge = smoothstep(0.96, 1.04, field);
    float core = smoothstep(1.6, 5.0, field);
    vec3 blob = mix(tint, vec3(1.0, 0.85, 0.55), core * 0.35);
    blob *= 0.85 + 0.15 * smoothstep(1.0, 2.5, field);
    col = mix(col, blob, edge);
    // kenar parlaması
    col += tint * smoothstep(0.55, 0.98, field) * (1.0 - edge) * 0.18;
    // cam yansıması
    float gx = gl_FragCoord.x / u_res.x;
    col += vec3(1.0) * smoothstep(0.08, 0.0, abs(gx - 0.12)) * 0.05;
    col += vec3(1.0) * smoothstep(0.02, 0.0, abs(gx - 0.86)) * 0.04;
    gl_FragColor = vec4(col, 1.0);
}
