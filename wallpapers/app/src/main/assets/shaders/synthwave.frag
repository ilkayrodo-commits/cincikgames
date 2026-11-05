// Retro Gün Batımı: çizgili güneş, neon ızgara, mor dağlar.

void main() {
    float aspect = u_res.x / u_res.y;
    vec2 p = vec2((gl_FragCoord.x - 0.5 * u_res.x) / u_res.y, v_uv.y);
    float t = u_time;
    float horizon = 0.42;
    vec3 col;
    if (p.y > horizon) {
        float h = (p.y - horizon) / (1.0 - horizon);
        col = mix(vec3(1.0, 0.35, 0.45), vec3(0.18, 0.02, 0.30), pow(h, 0.7));
        col = mix(col, vec3(0.03, 0.0, 0.10), smoothstep(0.55, 1.0, h));
        // yıldızlar
        vec2 g = gl_FragCoord.xy / u_res.y * 120.0;
        vec2 id = floor(g);
        float r = hash12(id);
        if (r > 0.97) {
            float d = length(fract(g) - 0.5);
            col += vec3(1.0, 0.8, 1.0) * smoothstep(0.1, 0.0, d) * smoothstep(0.3, 0.8, h)
                   * (0.5 + 0.5 * sin(t * 2.0 + r * 30.0));
        }
        // güneş
        vec2 sc = vec2(0.0, horizon + 0.17);
        float sd = length(p - sc);
        float sunR = 0.20;
        vec3 sunCol = mix(vec3(1.0, 0.85, 0.25), vec3(1.0, 0.15, 0.55), smoothstep(sc.y + sunR, sc.y - sunR, p.y));
        float stripes = 1.0;
        float sy = (sc.y + 0.02 - p.y) / sunR;
        if (sy > 0.0) {
            float band = fract(sy * 6.0 - t * 0.35);
            stripes = step(sy * 0.45, band);
        }
        float sun = smoothstep(sunR + 0.003, sunR - 0.003, sd) * stripes;
        col += vec3(1.0, 0.3, 0.5) * exp(-max(sd - sunR, 0.0) * 9.0) * 0.45;
        col = mix(col, sunCol, sun);
        // dağlar
        float x = p.x + (u_offset - 0.5) * 0.15;
        float m = horizon + 0.11 * abs(sin(x * 3.1 + 1.0)) * (0.6 + 0.4 * sin(x * 7.3))
                  + 0.06 * (vnoise(vec2(x * 9.0, 1.0)) - 0.5);
        m = max(m, horizon + 0.005);
        if (p.y < m) {
            col = mix(vec3(0.10, 0.0, 0.18), vec3(0.25, 0.02, 0.35), (p.y - horizon) / 0.15);
            col += vec3(1.0, 0.2, 0.8) * smoothstep(0.006, 0.0, m - p.y) * 0.9;
        }
    } else {
        // perspektif zemin ızgarası
        float d = horizon - p.y;
        float z = 0.18 / d;
        float gx = p.x * z * 2.2;
        float gz = z + t * 1.2;
        vec2 g = vec2(gx, gz);
        vec2 fw = max(fwidth(g), vec2(1e-4));
        vec2 dl = abs(fract(g - 0.5) - 0.5) / fw;   // çizgiye piksel uzaklığı
        float dmin = min(dl.x, dl.y);
        float fade = exp(-z * 0.08);
        float far = smoothstep(0.35, 0.0, max(fw.x, fw.y));  // uzakta moiré'yi sönümle
        float line = (1.0 - smoothstep(0.6, 1.6, dmin)) * far + exp(-dmin * 0.25) * 0.35 * far;
        col = mix(vec3(0.05, 0.0, 0.10), vec3(0.20, 0.0, 0.25), fade);
        col += vec3(1.0, 0.15, 0.85) * line * fade * 1.3;
        col += vec3(1.0, 0.3, 0.6) * exp(-d * 25.0) * 0.5;
        // dokunma: ızgarada dalga
        vec2 tdir;
        float tp = touchPulse(gl_FragCoord.xy, u_res.y * 0.2, tdir);
        col += vec3(0.2, 0.9, 1.0) * tp * line * 2.0;
    }
    // tarama çizgileri
    col *= 0.92 + 0.08 * sin(gl_FragCoord.y * 1.6);
    gl_FragColor = vec4(col, 1.0);
}
