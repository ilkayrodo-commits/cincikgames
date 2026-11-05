// Altın Mermer: yavaşça akan siyah mermer üzerinde parlayan altın damarlar.

void main() {
    vec2 p = (gl_FragCoord.xy - 0.5 * u_res) / u_res.y;
    float t = u_time * 0.05;
    vec2 q = p * 1.8 + vec2((u_offset - 0.5) * 0.3, 0.0);
    // dokunma: girdap
    vec2 tdir;
    float tp = touchPulse(gl_FragCoord.xy, u_res.y * 0.18, tdir);
    float ang = tp * 1.2;
    vec2 tc = (u_touch.xy - 0.5 * u_res) / u_res.y * 1.8;
    vec2 rel = q - tc;
    rel = mat2(cos(ang), -sin(ang), sin(ang), cos(ang)) * rel;
    q = tc + rel;

    vec2 a = vec2(fbm(q + vec2(t, 0.0)), fbm(q + vec2(5.2, 1.3) - t));
    vec2 b = vec2(fbm(q + 4.0 * a + vec2(1.7, 9.2) + t * 0.6), fbm(q + 4.0 * a + vec2(8.3, 2.8) - t * 0.4));
    float f = fbm(q + 4.0 * b);
    // damarlar: değer alanındaki ince bantlar
    float v = abs(sin(f * 26.0 + b.x * 6.0));
    float vein = pow(1.0 - v, 10.0) * smoothstep(0.25, 0.6, a.x);
    float vein2 = pow(1.0 - abs(sin(f * 9.0 + a.y * 3.0)), 28.0);
    vec3 base = mix(vec3(0.02, 0.02, 0.025), vec3(0.16, 0.17, 0.18), smoothstep(0.35, 0.85, f));
    base += vec3(0.05) * pow(1.0 - abs(sin(f * 12.0 + b.y * 5.0)), 6.0);
    base = mix(base, vec3(0.02, 0.05, 0.05), smoothstep(0.5, 0.9, b.y) * 0.6);
    // metalik altın: açıya bağlı parlaklık
    float shimmer = 0.6 + 0.4 * sin(f * 30.0 - u_time * 0.8 + p.y * 4.0);
    vec3 gold = mix(vec3(0.55, 0.35, 0.08), vec3(1.0, 0.85, 0.45), shimmer);
    vec3 col = base;
    col = mix(col, gold, clamp(vein * 1.2 + vein2, 0.0, 1.0));
    col += gold * pow(vein, 3.0) * 0.4;
    // ışıltı noktaları
    vec2 g = gl_FragCoord.xy / 6.0;
    vec2 id = floor(g);
    float r = hash12(id);
    if (r > 0.985) {
        float d = length(fract(g) - 0.5);
        col += vec3(1.0, 0.9, 0.6) * smoothstep(0.3, 0.0, d) * (vein + vein2) *
               pow(0.5 + 0.5 * sin(u_time * 3.0 + r * 80.0), 4.0) * 2.0;
    }
    vec2 vc = v_uv - 0.5;
    col *= 1.0 - 0.35 * dot(vc, vc);
    gl_FragColor = vec4(col, 1.0);
}
