// Galata: kullanıcının duvar kağıdı. Saçlar tel yönünde dalgalanır,
// gökyüzünde altın tozları parıldar, kule çevresinde kuşlar uçar.
// u_tex0 = görüntü, u_tex1 = hareket haritası (tools/galata_flowmap.py)

vec2 coverUV(vec2 uv) {
    float sa = u_res.x / u_res.y;
    float ia = u_tex0Size.x / u_tex0Size.y;
    vec2 st = vec2(uv.x, 1.0 - uv.y);
    if (sa < ia) {
        float k = sa / ia;
        st.x = 0.5 + (st.x - 0.5) * k + (u_offset - 0.5) * (1.0 - k);
    } else {
        float k = ia / sa;
        st.y = 0.5 + (st.y - 0.5) * k;
    }
    return st;
}

float birdShape(vec2 p, float flap) {
    float lift = flap * 0.5;
    vec2 elbowL = vec2(-0.42, 0.10 + lift * 0.35);
    vec2 elbowR = vec2(0.42, 0.10 + lift * 0.35);
    vec2 tipL = vec2(-1.0, lift - 0.05);
    vec2 tipR = vec2(1.0, lift - 0.05);
    float d = min(min(sdSeg(p, vec2(0.0), elbowL), sdSeg(p, elbowL, tipL)),
                  min(sdSeg(p, vec2(0.0), elbowR), sdSeg(p, elbowR, tipR)));
    float th = mix(0.10, 0.025, clamp(abs(p.x), 0.0, 1.0));
    float body = length((p - vec2(0.0, -0.03)) * vec2(1.0, 1.7)) - 0.11;
    return min(d - th, body);
}

// Bir sürü: merkez yolu + her kuşa küçük sapma. Dönüş: kuş örtüsü (0..1)
float flock(vec2 P, float t, float seed, float dirSign, float yBase, float period) {
    float aspect = u_res.x / u_res.y;
    float ph = fract(t / period + seed);
    float cx = mix(aspect + 0.35, -0.35, ph);
    if (dirSign < 0.0) cx = mix(-0.35, aspect + 0.35, ph);
    float cy = yBase + 0.05 * sin(t * 0.21 + seed * 6.0);
    float cover = 0.0;
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        vec2 r = hash22(vec2(fi, seed * 17.0)) - 0.5;
        vec2 c = vec2(cx + r.x * 0.30 + abs(fi - 3.0) * 0.035 * dirSign,
                      cy + r.y * 0.12 + 0.012 * sin(t * 1.3 + fi * 2.1));
        float size = 0.018 + 0.010 * hash12(vec2(fi + 3.0, seed));
        vec2 q = (P - c) / size;
        if (abs(q.x) > 1.4 || abs(q.y) > 1.2) continue;
        float flap = sin(t * (7.0 + fi * 0.6) + fi * 1.7);
        // arada süzülme
        flap *= 0.35 + 0.65 * smoothstep(-0.3, 0.6, sin(t * 0.7 + fi));
        float d = birdShape(q, flap);
        cover = max(cover, smoothstep(0.06, -0.02, d));
    }
    return cover;
}

void main() {
    vec2 st = coverUV(v_uv);
    vec3 fm = texture2D(u_tex1, st).rgb;
    float hair = clamp(fm.r * 2.0 - 1.0, 0.0, 1.0);
    float sky = clamp(1.0 - fm.r * 2.0, 0.0, 1.0);
    float c = fm.g * 2.0 - 1.0;
    float s = fm.b * 2.0 - 1.0;
    float t = u_time;

    // Rüzgar alanı (görüntü koordinatlarında, en-boy düzeltmeli)
    vec2 q = st * vec2(1.0, u_tex0Size.y / u_tex0Size.x);
    vec2 v = vec2(sin(q.y * 4.5 - t * 0.85 + 1.4 * sin(q.x * 2.7 + t * 0.31)),
                  cos(q.x * 3.6 + q.y * 2.6 - t * 0.66));
    v += 0.45 * vec2(sin(q.y * 10.0 + q.x * 6.0 - t * 1.6),
                     cos(q.y * 8.5 - q.x * 5.5 - t * 1.25));
    // Tel yönüne izdüşüm: T = 0.5 * (I + [[c, s], [s, -c]])
    vec2 tv = 0.5 * (v + vec2(c * v.x + s * v.y, s * v.x - c * v.y));
    vec2 dispPx = (0.85 * tv + 0.15 * v) * 7.5 * hair;

    // Dokunma: saçlar parmaktan uzağa itilir, sonra geri döner.
    vec2 tdir;
    float tp = touchPulse(gl_FragCoord.xy, u_res.y * 0.09, tdir);
    vec2 tdirImg = vec2(tdir.x, -tdir.y);
    dispPx += tdirImg * tp * 26.0 * hair * (0.6 + 0.4 * sin(u_touch.z * 9.0));

    vec2 uv = st + dispPx / u_tex0Size;
    vec3 col = texture2D(u_tex0, uv).rgb;

    // Saçlarda hafif ışıltı: tel yönünde akan parlaklık
    float sheen = sin(q.x * 3.0 + q.y * 9.0 - t * 1.4 + v.x * 1.5);
    col *= 1.0 + 0.06 * hair * sheen;

    // Gökyüzünde parıldayan altın tozları
    vec2 cell = floor(gl_FragCoord.xy / 9.0);
    float rnd = hash12(cell);
    if (rnd > 0.93) {
        vec2 sp = (hash22(cell + 7.0) * 0.6 + 0.2) * 9.0;
        float d = length(mod(gl_FragCoord.xy, 9.0) - sp);
        float tw = pow(0.5 + 0.5 * sin(t * (1.5 + rnd * 3.0) + rnd * 40.0), 6.0);
        col += vec3(1.0, 0.75, 0.38) * smoothstep(1.8, 0.0, d) * tw * sky * 0.9;
    }

    // Kuşlar
    vec2 P = gl_FragCoord.xy / u_res.y;
    float b = flock(P, t, 0.0, 1.0, 0.70, 34.0);
    b = max(b, flock(P, t + 11.0, 0.53, -1.0, 0.80, 41.0));
    col = mix(col, vec3(0.025, 0.03, 0.045), b * 0.92);

    // Hafif kenar karartma
    vec2 vc = v_uv - 0.5;
    col *= 1.0 - 0.18 * dot(vc, vc);
    gl_FragColor = vec4(col, 1.0);
}
