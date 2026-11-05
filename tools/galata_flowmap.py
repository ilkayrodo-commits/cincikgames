"""Temizlenmiş Galata görüntüsünden hareket haritası üretir.

Çıktı (RGB, yarım çözünürlük):
  R = 0.5 * (1 + saç - gökyüzü)  -> gökyüzü 0, kule 0.5, saç 1
  G = cos(2θ) * tutarlılık * 0.5 + 0.5   (θ = tel yönü)
  B = sin(2θ) * tutarlılık * 0.5 + 0.5
Yön çift açı olarak saklanır; böylece işaret belirsizliği yoktur ve
filtrelenince bozulmaz. Shader'da T = 0.5*(I + [[c,s],[s,-c]]) ile
rüzgar vektörü tel yönüne izdüşürülür.
"""
import sys
import cv2
import numpy as np

src, out, debug = sys.argv[1], sys.argv[2], sys.argv[3]
img = cv2.imread(src).astype(np.float32) / 255.0
h, w = img.shape[:2]
b, g, r = img[..., 0], img[..., 1], img[..., 2]
lum = 0.299 * r + 0.587 * g + 0.114 * b

# Kule çokgeni (1080x2460 koordinatları).
tower = np.array([
    (620, 410), (644, 410), (660, 524), (790, 880), (820, 940), (856, 1080),
    (856, 1800), (370, 1800), (366, 1080), (400, 940), (456, 890), (610, 524),
], np.int32)
tower_mask = np.zeros((h, w), np.float32)
cv2.fillPoly(tower_mask, [tower], 1.0)
tower_mask = cv2.GaussianBlur(tower_mask, (0, 0), 14)

# Gökyüzü: mavi baskın, orta parlaklık.
sky = np.clip(((b - r) - 0.05) / 0.08, 0, 1) * np.clip((lum - 0.07) / 0.05, 0, 1)
sky = cv2.GaussianBlur(sky, (0, 0), 3)

# Saç: gökyüzü ve kule olmayan her yer. Kule tamamen sabit kalır
# (kuleye sarılan bantlar orada "tutturulmuş" gibi durur).
hair = (1 - sky) * (1 - tower_mask)
hair = cv2.GaussianBlur(np.clip(hair, 0, 1), (0, 0), 5)

# Tel yönü: yapı tensörü.
gray = cv2.GaussianBlur(lum, (0, 0), 1.2)
gx = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3)
gy = cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
sig = 9
jxx = cv2.GaussianBlur(gx * gx, (0, 0), sig)
jyy = cv2.GaussianBlur(gy * gy, (0, 0), sig)
jxy = cv2.GaussianBlur(gx * gy, (0, 0), sig)
# Gradyanın çift açısı; tel yönü ona diktir -> çift açıda işaret değişir.
c2 = -(jxx - jyy)
s2 = -(2 * jxy)
mag = np.sqrt(c2 * c2 + s2 * s2) + 1e-6
coherence = mag / (jxx + jyy + 1e-6)
c2, s2 = c2 / mag * coherence, s2 / mag * coherence
c2 = cv2.GaussianBlur(c2, (0, 0), 4)
s2 = cv2.GaussianBlur(s2, (0, 0), 4)

outimg = np.dstack([
    np.clip(s2 * 0.5 + 0.5, 0, 1),   # B
    np.clip(c2 * 0.5 + 0.5, 0, 1),   # G
    np.clip(0.5 * (1 + hair - sky), 0, 1),  # R
])
small = cv2.resize(outimg, (w // 2, h // 2), interpolation=cv2.INTER_AREA)
cv2.imwrite(out, (small * 255 + 0.5).astype(np.uint8))

# Hata ayıklama görseli: saç ağırlığı + yön çizgileri.
vis = (img * 0.35 * 255).astype(np.uint8)
vis[..., 2] = np.maximum(vis[..., 2], (hair * 255).astype(np.uint8))
vis[..., 1] = np.maximum(vis[..., 1], (sky * 200).astype(np.uint8))
for y in range(20, h, 40):
    for x in range(20, w, 40):
        c, s = c2[y, x], s2[y, x]
        coh = np.hypot(c, s)
        th = 0.5 * np.arctan2(s, c)
        L = 16 * coh
        p0 = (int(x - L * np.cos(th)), int(y - L * np.sin(th)))
        p1 = (int(x + L * np.cos(th)), int(y + L * np.sin(th)))
        cv2.line(vis, p0, p1, (255, 255, 255), 1, cv2.LINE_AA)
cv2.polylines(vis, [tower], True, (0, 255, 0), 2)
cv2.imwrite(debug, cv2.resize(vis, (w // 2, h // 2)))
