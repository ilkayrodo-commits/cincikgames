// Galata v2: kuleyi saran saçlar gerçekten akar ve dalgalanır.
//
// u_tex0 = temizlenmiş görüntü
// u_tex1 = akış haritası  R: 0.5*(1+saç-gökyüzü)  G,B: yönlü tel akışı (x, y)
// u_tex2 = yardımcı harita R: kule  G: pencereler  B: kule önündeki saç bantları
// (tools/galata_maps.py ile üretilir)

const float PI = 3.14159265;

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

float hairOf(float m) { return clamp(m * 2.0 - 1.0, 0.0, 1.0); }
float skyOf(float m) { return clamp(1.0 - m * 2.0, 0.0, 1.0); }

// ---------------------------------------------------------------- martılar
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

float flock(vec2 P, float t, float seed, float dirSign, float yBase, float period, float scale) {
    float aspect = u_res.x / u_res.y;
    float ph = fract(t / period + seed);
    float cx = mix(aspect + 0.4, -0.4, ph);
    if (dirSign < 0.0) cx = mix(-0.4, aspect + 0.4, ph);
    float cy = yBase + 0.06 * sin(t * 0.21 + seed * 6.0) + 0.03 * sin(ph * 6.2831);
    float cover = 0.0;
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        vec2 r = hash22(vec2(fi, seed * 17.0)) - 0.5;
        vec2 c = vec2(cx + r.x * 0.32 + abs(fi - 3.0) * 0.04 * dirSign,
                      cy + r.y * 0.13 + 0.014 * sin(t * 1.3 + fi * 2.1));
        float size = (0.017 + 0.011 * hash12(vec2(fi + 3.0, seed))) * scale;
        vec2 q = (P - c) / size;
        if (abs(q.x) > 1.4 || abs(q.y) > 1.2) continue;
        if (dirSign < 0.0) q.x = -q.x;
        float flap = sin(t * (7.0 + fi * 0.6) + fi * 1.7);
        flap *= 0.35 + 0.65 * smoothstep(-0.3, 0.6, sin(t * 0.7 + fi));
        cover = max(cover, smoothstep(0.06, -0.02, birdShape(q, flap)));
    }
    return cover;
}

// ------------------------------------- kulenin etrafında dönen serbest teller
// side 0: ön yarı (kulenin önünden geçer), side 1: arka yarı (yalnızca gökyüzünde görünür)
vec4 orbitStrands(vec2 px, float t, int side) {
    float cx = 612.0;
    float R = mix(150.0, 300.0, smoothstep(650.0, 1120.0, px.y)) + 18.0;
    float u = (px.x - cx) / R;
    if (abs(u) > 1.0) return vec4(0.0);
    float th = asin(u);
    float ths = side == 0 ? th : PI - th;
    float span = smoothstep(600.0, 760.0, px.y) * smoothstep(1760.0, 1480.0, px.y)
               * smoothstep(1.0, 0.86, abs(u));   // yan kenarda yumuşakça arkaya dön
    if (span <= 0.0) return vec4(0.0);
    vec4 acc = vec4(0.0);
    for (int k = 0; k < 2; k++) {
        float fk = float(k);
        float pitch = 560.0 + fk * 180.0;
        // Sarmal aşağı kayar: tutam kulenin etrafında dönüyormuş gibi görünür.
        float y0 = 640.0 + fk * 330.0 + t * (30.0 + fk * 10.0);
        float wave = 22.0 * sin(ths * 2.0 + t * 0.9 + fk * 3.0) + 9.0 * sin(ths * 5.0 - t * 1.4);
        float dy = px.y - (y0 + pitch * ths / (2.0 * PI) + wave);
        dy -= pitch * floor(dy / pitch + 0.5);
        float H = 46.0 + 16.0 * fk + 10.0 * sin(ths * 1.5 + t * 0.5);
        if (abs(dy) > H) continue;
        float v = dy / H;
        // tutamın gövdesi: kenarlara doğru incelen koyu yarı saydam kütle
        float body = smoothstep(1.0, 0.45, abs(v));
        float fib = 0.0;
        float hi = 0.0;
        for (int j = 0; j < 10; j++) {
            float fj = float(j);
            float off = (fj - 4.5) * 0.19 + 0.08 * sin(ths * (3.0 + fj * 0.4) + t * 1.2 + fj * 2.3);
            float w = 0.030 + 0.025 * hash12(vec2(fj, fk));
            float line = smoothstep(w, w * 0.15, abs(v - off));
            fib = max(fib, line);
            // her telde akan küçük parıltılar
            float dash = smoothstep(0.55, 1.0, sin(ths * (14.0 + fj) - t * (2.5 + fj * 0.2) + fj * 7.0));
            hi = max(hi, line * dash * hash12(vec2(fj + 4.0, fk + 2.0)));
        }
        float depth = cos(ths);
        float lit = pow(clamp(0.5 + 0.5 * sin(ths + 0.9), 0.0, 1.0), 2.0);
        vec3 dark = vec3(0.018, 0.018, 0.026);
        vec3 strand = mix(dark, vec3(0.55, 0.24, 0.10), lit * 0.5);
        vec3 c = mix(dark, strand, fib) + vec3(1.0, 0.52, 0.20) * hi * (0.35 + 0.65 * lit) * (0.5 + 0.5 * max(depth, 0.0));
        float a = clamp(body * 0.93 + fib * 0.4, 0.0, 1.0) * span * (side == 0 ? 0.97 : 0.9);
        acc.rgb = mix(acc.rgb, c, a);
        acc.a = max(acc.a, a);
    }
    return acc;
}

// ------------------------------------------- gökyüzünde savrulan serbest teller
vec4 wisps(vec2 P, float t) {
    float aspect = u_res.x / u_res.y;
    vec4 acc = vec4(0.0);
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float period = 13.0 + fi * 2.7;
        float ph = fract(t / period + hash12(vec2(fi, 2.0)));
        vec2 start = vec2(hash12(vec2(fi, 3.0)) * aspect, 0.45 + 0.5 * hash12(vec2(fi, 4.0)));
        vec2 c = start + vec2(-0.25 + 0.5 * hash12(vec2(fi, 5.0)), -0.12) * ph;
        float ang = -0.6 + 1.2 * hash12(vec2(fi, 6.0)) + 0.35 * sin(t * 0.4 + fi);
        vec2 q = P - c;
        q = vec2(cos(ang) * q.x + sin(ang) * q.y, -sin(ang) * q.x + cos(ang) * q.y);
        float L = 0.13 + 0.10 * hash12(vec2(fi, 7.0));
        if (abs(q.x) > L || abs(q.y) > 0.06) continue;
        float env = smoothstep(L, L * 0.55, abs(q.x));
        float fade = smoothstep(0.0, 0.2, ph) * smoothstep(1.0, 0.75, ph);
        for (int f = 0; f < 3; f++) {
            float ff = float(f);
            float y = 0.022 * sin(q.x * 28.0 - t * 2.4 + fi * 3.0 + ff * 0.8) * (0.4 + q.x / L * 0.6)
                      + ff * 0.0045 * (1.0 + q.x / L);
            float d = abs(q.y - y);
            float a = smoothstep(0.0011, 0.0, d) * env * fade * 0.9;
            vec3 col = mix(vec3(0.02, 0.02, 0.03), vec3(0.85, 0.40, 0.16), 0.25 + 0.25 * sin(q.x * 40.0 + t));
            acc.rgb = mix(acc.rgb, col, a);
            acc.a = max(acc.a, a);
        }
    }
    return acc;
}

void main() {
    float t = u_time;
    vec2 size = u_tex0Size;

    // --- kamera: hafif nefes alan yakınlaşma + eğim paralaksı için pay
    vec2 st = coverUV(v_uv);
    float zoom = 1.05 + 0.012 * sin(t * 0.16);
    st = 0.5 + (st - 0.5) / zoom;
    vec3 f0 = texture2D(u_tex1, st).rgb;
    float layer = hairOf(f0.r) + (1.0 - hairOf(f0.r) - skyOf(f0.r)) * 0.45;
    vec2 tilt = clamp(u_tilt, -1.0, 1.0);
    st -= vec2(tilt.x, -tilt.y) * (5.0 + 20.0 * layer) / size;

    vec3 f = texture2D(u_tex1, st).rgb;
    vec3 aux = texture2D(u_tex2, st).rgb;
    float hair = hairOf(f.r);
    float sky = skyOf(f.r);
    float tower = aux.r;
    float band = aux.b;
    vec2 fl = f.gb * 2.0 - 1.0;
    vec2 nrm = vec2(-fl.y, fl.x);

    // --- büyük dalgalanma: saç kütleleri tellere dik yönde savrulur
    vec2 q = st * vec2(1.0, size.y / size.x);
    float ph = q.y * 3.2 + q.x * 1.4 - t * 1.05 + 1.6 * vnoise(q * 1.5 + t * 0.07);
    float swayAmp = (15.0 * sin(ph) + 6.0 * sin(ph * 2.3 + 1.0 - t * 0.6)) * mix(1.0, 0.3, band);
    vec2 iso = vec2(sin(ph * 0.9 + 0.5), cos(ph * 0.7)) * 0.35;
    vec2 dispPx = (nrm + iso) * swayAmp * hair;

    // --- dokunma: saçlar parmaktan itilir ve yaylanarak geri gelir
    vec2 tdir;
    float tp = touchPulse(gl_FragCoord.xy, u_res.y * 0.11, tdir);
    dispPx += vec2(tdir.x, -tdir.y) * tp * 42.0 * hair * cos(u_touch.z * 7.0);

    // --- akış: teller kendi yönünde akar (iki fazlı akış haritası)
    vec2 base = st + dispPx / size;
    float L = mix(58.0, 40.0, band);
    float n0 = vnoise(q * 2.2) * 1.3;
    float speed = 0.22;
    float p0 = fract(t * speed + n0);
    float p1 = fract(t * speed + n0 + 0.5);
    vec2 adv = fl * L * hair / size;
    vec2 uv0 = base - adv * (p0 - 0.5);
    vec2 uv1 = base - adv * (p1 - 0.5);
    vec3 cb = texture2D(u_tex0, base).rgb;
    vec3 c0 = texture2D(u_tex0, uv0).rgb;
    vec3 c1 = texture2D(u_tex0, uv1).rgb;
    // Akış saçın dışına (kuleye/gökyüzüne) taşmasın.
    c0 = mix(cb, c0, smoothstep(0.25, 0.75, hairOf(texture2D(u_tex1, uv0).r)));
    c1 = mix(cb, c1, smoothstep(0.25, 0.75, hairOf(texture2D(u_tex1, uv1).r)));
    float w0 = 1.0 - abs(2.0 * p0 - 1.0);
    vec3 col = c0 * w0 + c1 * (1.0 - w0);

    // --- saçların üzerinden geçen sıcak ışık
    float hl = smoothstep(0.06, 0.32, col.r - col.b);
    float sweep = pow(0.5 + 0.5 * sin((q.x * 0.7 + q.y * 0.9) * 5.0 - t * 0.7), 5.0);
    float shimmer = 0.5 + 0.5 * sin(dot(q, fl) * 60.0 - t * 4.0);
    col += col * hl * hair * (sweep * 1.1 + shimmer * 0.15);

    // --- pencerelerde titreyen mum ışığı (hafif hale ile)
    vec2 o = 14.0 / size;
    float bloom = (texture2D(u_tex2, st + vec2(o.x, o.y)).g + texture2D(u_tex2, st + vec2(-o.x, o.y)).g +
                   texture2D(u_tex2, st + vec2(o.x, -o.y)).g + texture2D(u_tex2, st + vec2(-o.x, -o.y)).g) * 0.25;
    float wid = vnoise(q * 3.0) * 20.0;
    float candle = 0.75 + 0.12 * sin(t * 9.0 + wid) + 0.08 * sin(t * 23.0 + wid * 1.7) + 0.05 * sin(t * 2.3 + wid);
    float winOn = smoothstep(0.35, 0.6, vnoise(q * vec2(9.0, 6.0) + vec2(0.0, floor(t / 7.0) * 3.1)));
    vec3 warm = vec3(1.0, 0.58, 0.22);
    float inner = aux.g * aux.g * (1.0 - band);
    bloom *= 1.0 - band;
    col = mix(col, col * 0.4 + warm * 0.55 * candle, inner * 0.8 * (1.0 - hair));
    col += warm * bloom * 0.25 * candle * (0.6 + 0.4 * winOn) * (1.0 - hair);

    // --- kulenin arkasından dolanan teller (yalnızca gökyüzünde)
    vec2 px = st * size;
    vec4 back = orbitStrands(px, t, 1);
    col = mix(col, back.rgb, back.a * sky);

    // --- gökyüzünde parıldayan altın tozları
    vec2 cell = floor(gl_FragCoord.xy / 9.0);
    float rnd = hash12(cell);
    if (rnd > 0.93) {
        vec2 sp = (hash22(cell + 7.0) * 0.6 + 0.2) * 9.0;
        float d = length(mod(gl_FragCoord.xy, 9.0) - sp);
        float tw = pow(0.5 + 0.5 * sin(t * (1.5 + rnd * 3.0) + rnd * 40.0), 6.0);
        col += vec3(1.0, 0.75, 0.38) * smoothstep(1.8, 0.0, d) * tw * sky * 0.9;
    }

    // --- savrulan teller ve martılar
    vec2 P = gl_FragCoord.xy / u_res.y;
    vec4 wp = wisps(P, t);
    col = mix(col, wp.rgb, wp.a * sky);
    float b = flock(P, t, 0.0, 1.0, 0.72, 30.0, 1.0);
    b = max(b, flock(P, t + 11.0, 0.53, -1.0, 0.82, 37.0, 0.8));
    b = max(b, flock(P, t + 23.0, 0.29, 1.0, 0.58, 45.0, 1.25));
    col = mix(col, vec3(0.025, 0.03, 0.045), b * 0.92);

    // --- kulenin önünden geçen dönen teller
    vec4 front = orbitStrands(px, t, 0);
    col = mix(col, front.rgb, front.a);

    // --- renk ve kenar karartma
    vec2 vc = v_uv - 0.5;
    col *= 1.0 - 0.22 * dot(vc, vc);
    col = mix(col, col * vec3(1.04, 1.0, 0.96), 0.5);
    gl_FragColor = vec4(col, 1.0);
}
