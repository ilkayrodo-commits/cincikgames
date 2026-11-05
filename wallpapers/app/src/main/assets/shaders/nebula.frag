// Derin Uzay: renkli bulutsu, katmanlı yıldızlar, kayan yıldızlar.

float starLayer(vec2 p, float density, float t) {
    vec2 id = floor(p);
    vec2 f = fract(p) - 0.5;
    float r = hash12(id);
    if (r > density) return 0.0;
    vec2 o = hash22(id + 4.0) - 0.5;
    float d = length(f - o * 0.7);
    float tw = 0.55 + 0.45 * sin(t * (0.8 + r * 3.0) + r * 60.0);
    return smoothstep(0.08, 0.0, d) * tw + smoothstep(0.35, 0.0, d) * 0.08;
}

void main() {
    vec2 p = (gl_FragCoord.xy - 0.5 * u_res) / u_res.y;
    float t = u_time;
    vec2 par = vec2((u_offset - 0.5) * 0.12, 0.0);
    vec2 q = p * 1.6 + par;
    // alan bükmeli fbm
    vec2 w1 = vec2(fbm(q + vec2(0.0, t * 0.010)), fbm(q + vec2(5.2, 1.3 - t * 0.008)));
    vec2 w2 = vec2(fbm(q + 3.5 * w1 + vec2(1.7, 9.2) + t * 0.006), fbm(q + 3.5 * w1 + vec2(8.3, 2.8)));
    float n = fbm(q + 3.0 * w2);
    vec3 col = vec3(0.004, 0.006, 0.02);
    vec3 c1 = vec3(0.35, 0.05, 0.55);
    vec3 c2 = vec3(0.05, 0.35, 0.75);
    vec3 c3 = vec3(1.0, 0.45, 0.25);
    vec3 neb = mix(c1, c2, clamp(w1.x * 1.6 - 0.3, 0.0, 1.0));
    neb = mix(neb, c3, smoothstep(0.55, 0.85, w2.y) * 0.8);
    float dens = smoothstep(0.35, 0.95, n);
    col += neb * dens * 1.25;
    col += vec3(1.0, 0.85, 0.75) * pow(dens, 4.0) * 0.6;
    // toz bantları
    col *= 0.55 + 0.45 * smoothstep(0.2, 0.6, fbm(q * 2.5 + 11.0));
    // yıldızlar (parallax)
    col += vec3(0.8, 0.85, 1.0) * starLayer((p + par * 0.3) * 90.0, 0.10, t) * 0.8;
    col += vec3(1.0, 0.95, 0.85) * starLayer((p + par * 0.6) * 45.0, 0.06, t * 1.3);
    col += vec3(0.9, 0.95, 1.0) * starLayer((p + par) * 22.0, 0.035, t * 0.7) * 1.4;
    // kayan yıldız (her ~9 sn)
    float cyc = floor(t / 9.0);
    float ft = fract(t / 9.0);
    vec2 r = hash22(vec2(cyc, 3.0));
    vec2 start = vec2(r.x * 0.8 - 0.2, 0.25 + r.y * 0.3);
    vec2 dir = normalize(vec2(-0.8, -0.45));
    vec2 head = start + dir * ft * 1.8;
    float along = dot(p - head, -dir);
    float side = abs(dot(p - head, vec2(-dir.y, dir.x)));
    float trail = smoothstep(0.0015, 0.0, side) * smoothstep(0.25, 0.0, along) * step(0.0, along);
    col += vec3(1.0, 0.95, 0.9) * trail * smoothstep(0.0, 0.05, ft) * smoothstep(0.6, 0.4, ft) * 1.5;
    // dokunma: yıldız patlaması
    vec2 tdir;
    float tp = touchPulse(gl_FragCoord.xy, u_res.y * 0.06, tdir);
    col += vec3(0.7, 0.8, 1.0) * tp * 1.2;
    col = 1.0 - exp(-col * 1.3);
    gl_FragColor = vec4(col, 1.0);
}
