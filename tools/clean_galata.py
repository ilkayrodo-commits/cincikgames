"""Ekran görüntüsünden saat, tarih, durum çubuğu ve kamera simgesini siler."""
import sys
import cv2
import numpy as np

src, out, mask_out = sys.argv[1], sys.argv[2], sys.argv[3]
img = cv2.imread(src)
h, w = img.shape[:2]
hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
lum = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY).astype(np.int16)
sat = hsv[..., 1].astype(np.int16)

mask = np.zeros((h, w), np.uint8)

# Açık renkli, doygunluğu düşük piksel = arayüz yazısı/simgesi.
overlay_like = ((lum > 105) & (sat < 90)).astype(np.uint8)

text_boxes = [
    (60, 35, 330, 95),     # Turk Telekom
    (60, 310, 540, 470),   # 00:04
    (60, 470, 520, 635),   # 6/11 CUM
]
for x0, y0, x1, y1 in text_boxes:
    mask[y0:y1, x0:x1] |= overlay_like[y0:y1, x0:x1]

# Kilit ekranı göstergeleri (daha soluk gri): eşik daha düşük.
faint = ((lum > 75) & (sat < 110)).astype(np.uint8)
mask[640:740, 60:210] |= faint[640:740, 60:210]

# Durum simgeleri ve kamera: kutunun tamamı (içlerinde koyu harfler/halka var).
solid_boxes = [
    (805, 32, 1018, 92),    # VoLTE / sinyal / pil
    (895, 2278, 978, 2350), # kamera simgesi
]
for x0, y0, x1, y1 in solid_boxes:
    region = overlay_like[y0:y1, x0:x1]
    ys, xs = np.nonzero(region)
    if len(xs):
        mask[y0 + ys.min() - 4:y0 + ys.max() + 5, x0 + xs.min() - 4:x0 + xs.max() + 5] = 1

# Kenar yumuşatma halesi için genişlet.
mask = cv2.dilate(mask, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9)))
cv2.imwrite(mask_out, mask * 255)

mask_inv = (1 - mask).astype(np.uint8)  # xphoto: 0 = doldurulacak
result = img.copy()
cv2.xphoto.inpaint(img, mask_inv, result, cv2.xphoto.INPAINT_FSR_FAST)

# Kamera simgesi çapraz saç tellerinin üstünde. Telleri devam ettirmek için,
# tel yönünde simgenin üstünden ve altından alınan iki temiz kopyayı karıştır.
x0, y0, x1, y1 = 860, 2240, 1020, 2390
direction = np.array([0.43, 0.90])
direction /= np.linalg.norm(direction)
reach = 95.0
ys, xs = np.mgrid[y0:y1, x0:x1].astype(np.float32)
def shifted(sign):
    mx = xs + sign * reach * direction[0]
    my = ys + sign * reach * direction[1]
    return cv2.remap(img, mx.astype(np.float32), my.astype(np.float32), cv2.INTER_LINEAR, borderMode=cv2.BORDER_REFLECT).astype(np.float32)
before, after = shifted(-1), shifted(+1)
cy, cx = 2314.0, 936.0
t = ((xs - cx) * direction[0] + (ys - cy) * direction[1]) / 80.0 + 0.5
t = np.clip(t, 0, 1)[..., None]
patch = before * (1 - t) + after * t
local = cv2.GaussianBlur(mask[y0:y1, x0:x1].astype(np.float32), (0, 0), 4)
local = np.clip(local * 1.6, 0, 1)[..., None]
result[y0:y1, x0:x1] = (patch * local + result[y0:y1, x0:x1] * (1 - local)).astype(np.uint8)
cv2.imwrite(out, result, [cv2.IMWRITE_JPEG_QUALITY, 95] if out.endswith(".jpg") else [])
print("masked pixels:", int(mask.sum()))
