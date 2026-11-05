// Galata v4: saçlar kendi üzerinde, bütün halinde dalgalanır. Kule sabittir.
//
// Tek ve pürüzsüz bir hareket alanı kullanılır (yukarı ilerleyen geniş dalgalar +
// akışkan kıvrılma). Alan her yerde süreklidir ve kuleye yaklaştıkça ~80 px'lik
// yumuşak bir rampayla sıfıra iner; bu yüzden saçta kopma/yırtılma oluşmaz.
//
// u_tex0 = temizlenmiş görüntü
// u_tex1 = R: 0.5*(1+saç-gökyüzü)
// u_tex2 = R: paralaks katmanı  G: hareket ağırlığı  B: kule önündeki saç bantları
// (tools/galata_maps.py ile üretilir)

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

// Akışkan kıvrılma: gürültü potansiyelinin rotasyoneli (sıkışmasız, yumuşak).
vec2 curl(vec2 p) {
    float e = 0.02;
    float n1 = vnoise(p + vec2(0.0, e));
    float n2 = vnoise(p - vec2(0.0, e));
    float n3 = vnoise(p + vec2(e, 0.0));
    float n4 = vnoise(p - vec2(e, 0.0));
    return vec2(n1 - n2, -(n3 - n4)) / (2.0 * e);
}

void main() {
    float t = u_time;
    vec2 size = u_tex0Size;

    // --- kamera: sabit kadraj + yumuşak eğim paralaksı
    vec2 st = coverUV(v_uv);
    float zoom = 1.04;   // eğim paralaksı için kenar payı (kule sabit kalsın diye sabit)
    st = 0.5 + (st - 0.5) / zoom;
    float layer = texture2D(u_tex2, st).r;
    vec2 tilt = clamp(u_tilt, -1.0, 1.0);
    st -= vec2(tilt.x, -tilt.y) * (6.0 + 10.0 * layer) / size;

    float m = texture2D(u_tex2, st).g;          // hareket ağırlığı
    float sky = skyOf(texture2D(u_tex1, st).r);
    vec2 px = st * size;

    // --- tek parça hareket alanı (piksel)
    // 1) yukarı doğru ilerleyen geniş dalgalar (saç kendi üzerinde salınır)
    float k1 = 6.2831 / 900.0;
    float k2 = 6.2831 / 520.0;
    float a1 = px.y * k1 + px.x * k1 * 0.35 + t * 0.55;
    float a2 = px.y * k2 - px.x * k2 * 0.50 + t * 0.85 + 1.7;
    vec2 d = vec2(sin(a1), 0.35 * cos(a1)) * 20.0
           + vec2(sin(a2), 0.40 * cos(a2 * 0.9)) * 9.0;
    // 2) yavaşça değişen akışkan kıvrılma
    vec2 cp = px / 650.0 + vec2(0.0, -t * 0.05);
    d += curl(cp + vec2(t * 0.03, 0.0)) * 5.5;
    d *= m;

    // --- dokunma: saçlar parmaktan yumuşakça itilir ve geri gelir
    vec2 tdir;
    float tp = touchPulse(gl_FragCoord.xy, u_res.y * 0.14, tdir);
    d += vec2(tdir.x, -tdir.y) * tp * 30.0 * m * cos(u_touch.z * 6.0);

    vec3 col = texture2D(u_tex0, st + d / size).rgb;

    // --- saçların üzerinden yukarı süzülen sıcak ışık (yumuşak, kesintisiz)
    float hl = smoothstep(0.06, 0.32, col.r - col.b);
    float sweep = pow(0.5 + 0.5 * sin(px.y * k1 * 1.2 + px.x * 0.002 + t * 0.9), 5.0);
    col += col * hl * m * sweep * 0.9;

    // --- gökyüzünde parıldayan altın tozları
    vec2 cell = floor(gl_FragCoord.xy / 9.0);
    float rnd = hash12(cell);
    if (rnd > 0.93) {
        vec2 sp = (hash22(cell + 7.0) * 0.6 + 0.2) * 9.0;
        float dd = length(mod(gl_FragCoord.xy, 9.0) - sp);
        float tw = pow(0.5 + 0.5 * sin(t * (1.5 + rnd * 3.0) + rnd * 40.0), 6.0);
        col += vec3(1.0, 0.75, 0.38) * smoothstep(1.8, 0.0, dd) * tw * sky * 0.9;
    }

    // --- martılar
    vec2 P = gl_FragCoord.xy / u_res.y;
    float b = flock(P, t, 0.0, 1.0, 0.72, 30.0, 1.0);
    b = max(b, flock(P, t + 11.0, 0.53, -1.0, 0.82, 37.0, 0.8));
    b = max(b, flock(P, t + 23.0, 0.29, 1.0, 0.58, 45.0, 1.25));
    col = mix(col, vec3(0.025, 0.03, 0.045), b * 0.92);

    // --- renk ve kenar karartma
    vec2 vc = v_uv - 0.5;
    col *= 1.0 - 0.22 * dot(vc, vc);
    col = mix(col, col * vec3(1.04, 1.0, 0.96), 0.5);
    gl_FragColor = vec4(col, 1.0);
}
