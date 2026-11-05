// Yağmurlu Cam: buğulu camda kayan damlalar, arkada bulanık şehir ışıkları.

vec3 bokeh(vec2 p, float t, float blur) {
    vec3 col = mix(vec3(0.02, 0.03, 0.07), vec3(0.10, 0.06, 0.12), smoothstep(-0.5, 0.5, p.y));
    for (int i = 0; i < 26; i++) {
        float fi = float(i);
        vec2 r = hash22(vec2(fi, 1.7));
        vec2 c = vec2(r.x * 1.2 - 0.6, r.y * 0.9 - 0.55);
        c.x += 0.02 * sin(t * 0.15 + fi);
        float rad = (0.05 + 0.09 * hash12(vec2(fi, 9.1))) * (1.0 + blur * 0.4);
        float d = length(p - c);
        float hue = hash12(vec2(fi, 4.4));
        vec3 lc = hue < 0.35 ? vec3(1.0, 0.65, 0.25) : (hue < 0.6 ? vec3(1.0, 0.25, 0.3) : (hue < 0.8 ? vec3(0.3, 0.6, 1.0) : vec3(0.9, 0.9, 0.7)));
        float flick = 0.75 + 0.25 * sin(t * (0.5 + hue) + fi * 3.0);
        float edge = mix(0.75, 0.2, blur);
        col += lc * smoothstep(rad, rad * edge, d) * 0.45 * flick / (1.0 + blur * 0.5);
        col += lc * exp(-d * 7.0) * 0.05;
    }
    return col;
}

// x: damla maskesi, y: arkasında bıraktığı temiz iz
vec2 dropLayer(vec2 uv, float t, float cols, float seed) {
    float cx = floor(uv.x * cols);
    float rnd = hash12(vec2(cx, seed));
    float speed = 0.05 + 0.09 * rnd;
    float span = 1.7;
    float yHead = 1.35 - fract(t * speed / span + rnd) * span;
    float xc = (cx + 0.5 + 0.5 * (hash12(vec2(cx, seed + 1.0)) - 0.5)) / cols
               + 0.006 * sin(uv.y * 23.0 + rnd * 10.0) / cols * 9.0;
    float r = (0.014 + 0.012 * rnd) * 9.0 / cols;
    vec2 d = (uv - vec2(xc, yHead)) * vec2(1.0, 0.8);
    float drop = smoothstep(r, r * 0.55, length(d));
    float above = uv.y - yHead;
    float trailW = r * 0.5 * exp(-max(above, 0.0) * 2.0);
    float trail = smoothstep(trailW, trailW * 0.3, abs(uv.x - xc)) * step(0.0, above) * exp(-above * 1.6);
    // izde kalan küçük damlacıklar
    float row = floor(uv.y * 45.0);
    float seg = fract(uv.y * 45.0);
    float dd = length(vec2((uv.x - xc) * 45.0, seg - 0.5));
    float droplets = smoothstep(0.3, 0.12, dd) * step(0.45, hash12(vec2(row, cx + seed))) * step(0.02, above)
                     * exp(-above * 1.2);
    return vec2(max(drop, droplets), trail);
}

void main() {
    vec2 p = (gl_FragCoord.xy - 0.5 * u_res) / u_res.y;
    vec2 uv = gl_FragCoord.xy / u_res.y;
    float t = u_time;

    // Küçük sabit damlacıklar (yavaşça belirip kaybolur)
    vec2 sg = uv * 55.0;
    vec2 sid = floor(sg);
    vec2 sf = fract(sg) - 0.5;
    vec2 so = (hash22(sid) - 0.5) * 0.5;
    float life = fract(t * 0.04 + hash12(sid + 2.0));
    float sd = length(sf - so);
    float small = smoothstep(0.24, 0.12, sd) * step(0.6, hash12(sid + 9.0)) * smoothstep(1.0, 0.75, life) * smoothstep(0.0, 0.1, life);

    vec2 l1 = dropLayer(uv, t, 9.0, 1.0);
    vec2 l2 = dropLayer(uv + vec2(0.031, 0.0), t * 1.15, 14.0, 7.0);
    float drops = clamp(l1.x + l2.x * 0.8 + small, 0.0, 1.0);
    float trails = clamp(l1.y + l2.y, 0.0, 1.0);

    vec2 grad = vec2(dFdx(drops), dFdy(drops));
    vec2 off = grad * 0.35 + (sf - so) * small * 0.06;

    float clear = max(drops, trails * 0.85);
    vec3 sharp = bokeh(p - off, t, 0.0);
    vec3 foggy = bokeh(p, t, 1.0) * 0.8 + vec3(0.04, 0.045, 0.06);
    vec3 col = mix(foggy, sharp * 1.15, clear);
    // damla parlaklığı
    col += vec3(0.9, 0.95, 1.0) * smoothstep(0.0, 0.25, -grad.y) * drops * 0.25;
    // dokunma: cam silinir gibi berraklaşır
    vec2 tdir;
    float tp = touchPulse(gl_FragCoord.xy, u_res.y * 0.12, tdir);
    col = mix(col, sharp * 1.1, clamp(tp * 1.5, 0.0, 1.0));
    gl_FragColor = vec4(col, 1.0);
}
