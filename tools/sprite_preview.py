"""assets/sprites.txt karelerini büyütülmüş PNG olarak çizer (kontrol için)."""
import sys
from PIL import Image, ImageDraw

src, out = sys.argv[1], sys.argv[2]
palette, frames, cur = {}, {}, None
for line in open(src, encoding='utf-8'):
    line = line.rstrip('\n')
    if not line or line.startswith('#'):
        continue
    if line.startswith('['):
        cur = line.strip('[]'); frames[cur] = []
    elif cur is None and '=' in line:
        k, v = line.split('=', 1); palette[k] = v
    elif cur:
        frames[cur].append(line)
S = 12
names = list(frames)
w = max(len(r) for r in frames[names[0]])
h = max(len(f) for f in frames.values())
img = Image.new('RGB', ((w * S + 20) * len(names), h * S + 40), (30, 60, 80))
d = ImageDraw.Draw(img)
for i, n in enumerate(names):
    ox = i * (w * S + 20) + 10
    d.text((ox, 4), n, fill=(255, 255, 255))
    for y, row in enumerate(frames[n]):
        assert len(row) == 16, (n, y, len(row))
        for x, ch in enumerate(row):
            c = palette.get(ch)
            if c and c != 'transparent':
                d.rectangle([ox + x * S, 24 + y * S, ox + x * S + S - 1, 24 + y * S + S - 1], fill=c)
img.save(out)
print(len(names), 'kare,', w, 'x', h)
