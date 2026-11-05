"""Galata v2 haritaları.

Çıktılar (yarım çözünürlük PNG):
  flow.png  R = 0.5*(1 + saç - gökyüzü)   (gökyüzü 0, kule 0.5, saç 1)
            G,B = yönlü akış vektörü * 0.5 + 0.5  (tel boyunca, tutarlı işaretli)
  aux.png   R = kule maskesi, G = pencere maskesi, B = saç bandı (kule önünde)

Tel yönünün işareti (ileri/geri) yapı tensöründen gelmez; komşular arasında en
iyi hizalanandan başlayarak yayılan bir ağaç (Prim) ile tutarlı hale getirilir,
sonra her saç kütlesi genel olarak aşağı akacak şekilde çevrilir. İşaretin
uyuşmadığı dikiş yerlerinde akış sönümlenir.
"""
import heapq
import sys

import cv2
import numpy as np
from scipy import ndimage

src, out_flow, out_aux, debug = sys.argv[1:5]
img = cv2.imread(src).astype(np.float32) / 255.0
h, w = img.shape[:2]
b, g, r = img[..., 0], img[..., 1], img[..., 2]
lum = 0.299 * r + 0.587 * g + 0.114 * b

tower_poly = np.array([
    (620, 410), (644, 410), (660, 524), (790, 880), (820, 940), (856, 1080),
    (856, 1800), (370, 1800), (366, 1080), (400, 940), (456, 890), (610, 524),
], np.int32)
tower_hard = np.zeros((h, w), np.uint8)
cv2.fillPoly(tower_hard, [tower_poly], 1)

sky = np.clip(((b - r) - 0.05) / 0.08, 0, 1) * np.clip((lum - 0.07) / 0.05, 0, 1)
sky = cv2.GaussianBlur(sky, (0, 0), 3)

# Kulenin içindeki koyu bölgeler: büyük olanlar saç bandı, küçükler pencere.
dark_in = ((cv2.GaussianBlur(lum, (0, 0), 2) < 0.075) & (tower_hard > 0)).astype(np.uint8)
dark_in = cv2.morphologyEx(dark_in, cv2.MORPH_OPEN, np.ones((5, 5), np.uint8))
lab, n = ndimage.label(dark_in)
areas = ndimage.sum(dark_in, lab, index=np.arange(1, n + 1))
big = np.zeros(n + 1, bool)
big[1:] = areas > 9000
small = np.zeros(n + 1, bool)
for i, sl in enumerate(ndimage.find_objects(lab), start=1):
    if sl is None or not (300 < areas[i - 1] <= 9000):
        continue
    bh = sl[0].stop - sl[0].start
    bw = sl[1].stop - sl[1].start
    fill = areas[i - 1] / float(bh * bw)
    # Pencere: dik ya da kareye yakın, dolu bir kemer (korkuluk/kenar çizgisi değil).
    small[i] = bh >= 20 and bw >= 14 and bw / bh < 1.6 and bh / bw < 4 and fill > 0.45
band = big[lab].astype(np.float32)
windows = small[lab].astype(np.float32)
band = cv2.GaussianBlur(cv2.dilate(band, np.ones((5, 5), np.uint8)), (0, 0), 4)
windows = cv2.GaussianBlur(windows, (0, 0), 2)

tower_soft = cv2.GaussianBlur(tower_hard.astype(np.float32), (0, 0), 10)
hair = (1 - sky) * (1 - tower_soft) + tower_soft * band
hair = np.clip(cv2.GaussianBlur(hair, (0, 0), 3), 0, 1)

# --- yapı tensörü -> tel yönü (işaretsiz)
gray = cv2.GaussianBlur(lum, (0, 0), 1.2)
gx = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3)
gy = cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
sig = 10
jxx = cv2.GaussianBlur(gx * gx, (0, 0), sig)
jyy = cv2.GaussianBlur(gy * gy, (0, 0), sig)
jxy = cv2.GaussianBlur(gx * gy, (0, 0), sig)
c2 = -(jxx - jyy)
s2 = -(2 * jxy)
mag = np.sqrt(c2 * c2 + s2 * s2) + 1e-9
coh = mag / (jxx + jyy + 1e-9)

# --- ızgarada işaret yayılımı
S = 4
gh, gw = h // S, w // S
c2g = cv2.resize(c2 / mag * coh, (gw, gh), interpolation=cv2.INTER_AREA)
s2g = cv2.resize(s2 / mag * coh, (gw, gh), interpolation=cv2.INTER_AREA)
cohg = np.hypot(c2g, s2g)
theta = 0.5 * np.arctan2(s2g, c2g)
tx, ty = np.cos(theta), np.sin(theta)
hairg = cv2.resize(hair, (gw, gh), interpolation=cv2.INTER_AREA)

sign = np.zeros((gh, gw), np.int8)
visited = np.zeros((gh, gw), bool)
order = np.argsort(-(cohg * hairg).ravel())
nbrs = ((1, 0), (-1, 0), (0, 1), (0, -1))
for seed in order:
    sy, sx = divmod(int(seed), gw)
    if visited[sy, sx]:
        continue
    visited[sy, sx] = True
    sign[sy, sx] = 1
    heap = []
    def push(y, x):
        for dy, dx in nbrs:
            ny, nx = y + dy, x + dx
            if 0 <= ny < gh and 0 <= nx < gw and not visited[ny, nx]:
                d = tx[y, x] * tx[ny, nx] + ty[y, x] * ty[ny, nx]
                wgt = 1.0 - abs(d) * min(cohg[y, x], cohg[ny, nx])
                heapq.heappush(heap, (wgt, ny, nx, y, x))
    push(sy, sx)
    while heap:
        _, ny, nx, py, px = heapq.heappop(heap)
        if visited[ny, nx]:
            continue
        visited[ny, nx] = True
        d = tx[py, px] * tx[ny, nx] + ty[py, px] * ty[ny, nx]
        sign[ny, nx] = sign[py, px] * (1 if d >= 0 else -1)
        push(ny, nx)

fx, fy = tx * sign, ty * sign

# Her saç kütlesi genel olarak aşağı aksın.
hair_lab, hn = ndimage.label(hairg > 0.5)
for i in range(1, hn + 1):
    m = hair_lab == i
    if fy[m].mean() < 0:
        fx[m] *= -1
        fy[m] *= -1

# Dikiş: komşu ile ters yön.
dots = np.ones((gh, gw), np.float32)
for dy, dx in nbrs:
    sh_x = np.roll(fx, (dy, dx), (0, 1))
    sh_y = np.roll(fy, (dy, dx), (0, 1))
    dots = np.minimum(dots, fx * sh_x + fy * sh_y)
seam = (dots < -0.2).astype(np.float32)
seam = cv2.GaussianBlur(cv2.dilate(seam, np.ones((3, 3), np.uint8)), (0, 0), 2.5)
strength = np.clip(cohg * 2.2, 0, 1) * np.clip(1 - seam * 2.5, 0, 1)

hw, hh = w // 2, h // 2
up = lambda a: cv2.resize(a.astype(np.float32), (hw, hh), interpolation=cv2.INTER_LINEAR)
FX = cv2.GaussianBlur(up(fx * strength), (0, 0), 1.5)
FY = cv2.GaussianBlur(up(fy * strength), (0, 0), 1.5)

half = lambda a: cv2.resize(a, (hw, hh), interpolation=cv2.INTER_AREA)
flow = np.dstack([FY * 0.5 + 0.5, FX * 0.5 + 0.5, half(0.5 * (1 + hair - sky))])
aux = np.dstack([half(band), half(windows), half(tower_soft)])
cv2.imwrite(out_flow, np.clip(flow * 255 + 0.5, 0, 255).astype(np.uint8))
cv2.imwrite(out_aux, np.clip(aux * 255 + 0.5, 0, 255).astype(np.uint8))

# Hata ayıklama: yön okları + dikişler + pencereler
vis = (cv2.resize(img, (hw, hh)) * 0.45 * 255).astype(np.uint8)
vis[..., 1] = np.maximum(vis[..., 1], (half(windows) * 255).astype(np.uint8))
vis[..., 2] = np.maximum(vis[..., 2], (up(seam) * 255).astype(np.uint8))
vis[..., 0] = np.maximum(vis[..., 0], (half(band) * 200).astype(np.uint8))
for y in range(12, hh, 24):
    for x in range(12, hw, 24):
        vx, vy = FX[y, x], FY[y, x]
        if np.hypot(vx, vy) < 0.15:
            continue
        p1 = (int(x + vx * 10), int(y + vy * 10))
        cv2.arrowedLine(vis, (x, y), p1, (255, 255, 255), 1, cv2.LINE_AA, tipLength=0.4)
cv2.imwrite(debug, vis)
print("hair components:", hn, "windows px:", int(windows.sum()), "band px:", int(band.sum()))
