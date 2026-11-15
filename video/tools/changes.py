# 클립 프레임 사이 화면 변화가 큰 시점을 찾는다(장면 동기화용). 사용: python tools/changes.py <클립이름> [임계값]
import sys, pathlib
from PIL import Image, ImageChops, ImageStat
d = pathlib.Path(__file__).resolve().parent.parent / "src" / "clips" / sys.argv[1]
th = float(sys.argv[2]) if len(sys.argv) > 2 else 2.0
fs = sorted(d.glob("*.jpg")); prev = None; out = []
for i, f in enumerate(fs):
    im = Image.open(f).convert("L").resize((360, 225))
    if prev is not None:
        v = sum(ImageStat.Stat(ImageChops.difference(im, prev)).mean)
        if v > th: out.append(f"{i/30:.2f}s:{v:.1f}")
    prev = im
print(sys.argv[1], len(fs) / 30, " ".join(out))
