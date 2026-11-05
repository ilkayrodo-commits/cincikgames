import sys
from PIL import Image, ImageDraw
out, files = sys.argv[1], sys.argv[2:]
ims = [Image.open(f).convert('RGB') for f in files]
w, h = ims[0].size
sheet = Image.new('RGB', (w * len(ims) + 8 * (len(ims) - 1), h + 24), (40, 40, 40))
d = ImageDraw.Draw(sheet)
for i, im in enumerate(ims):
    sheet.paste(im, (i * (w + 8), 24))
    d.text((i * (w + 8) + 6, 6), files[i].split('/')[-1], fill=(255, 255, 255))
sheet.save(out)
