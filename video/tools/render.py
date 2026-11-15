# 전체 프레임을 병렬 렌더링해 MP4로 만든다. 사용: python tools/render.py [작업 수]
# 정지 화면 확인: python tools/render.py stills 3.5 20 40 ...  (output/stills/에 PNG 저장)
import sys, json, re, subprocess, pathlib, asyncio, math
from multiprocessing import Process
from playwright.async_api import async_playwright
ROOT = pathlib.Path(__file__).resolve().parent.parent
FPS = 30
TOTAL = json.loads(re.search(r"=\s*(\{.*\});", (ROOT / "src" / "timeline.js").read_text(encoding="utf-8"), re.S).group(1))["total"]
NF = round(TOTAL * FPS)
TMP = ROOT / "audio" / "segments"
OUT = ROOT / "output" / "멈춘다음_시연영상.mp4"
AUDIO = ROOT / "output" / "멈춘다음_시연영상_음성.m4a"

async def open_page(p):
    br = await p.chromium.launch(args=["--allow-file-access-from-files"])
    pg = await br.new_page(viewport={"width": 1920, "height": 1080})
    await pg.goto((ROOT / "src" / "index.html").as_uri() + "?render=1")
    await pg.evaluate("window.READY")
    return br, pg

async def work(a, b, out):
    ff = subprocess.Popen(["ffmpeg", "-v", "error", "-y", "-f", "image2pipe", "-framerate", str(FPS), "-c:v", "mjpeg", "-i", "-",
        "-vf", "scale=in_range=full:out_range=limited,format=yuv420p", "-color_range", "tv", "-colorspace", "bt709", "-color_primaries", "bt709",
        "-color_trc", "bt709", "-c:v", "libx264", "-preset", "medium", "-crf", "16", "-r", str(FPS), str(out)], stdin=subprocess.PIPE)
    async with async_playwright() as p:
        br, pg = await open_page(p)
        for f in range(a, b):
            await pg.evaluate(f"R({f / FPS})")
            ff.stdin.write(await pg.screenshot(type="jpeg", quality=94))
        await br.close()
    ff.stdin.close(); ff.wait()

async def stills(ts):
    d = ROOT / "output" / "stills"; d.mkdir(parents=True, exist_ok=True)
    async with async_playwright() as p:
        br, pg = await open_page(p)
        for t in ts:
            await pg.evaluate(f"R({t})")
            await pg.screenshot(path=str(d / f"t{float(t):06.2f}.png"))
        await br.close()

def run(a, b, out): asyncio.run(work(a, b, out))

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "stills":
        asyncio.run(stills(sys.argv[2:])); sys.exit()
    W = int(sys.argv[1]) if len(sys.argv) > 1 else 6
    TMP.mkdir(exist_ok=True)
    step = math.ceil(NF / W); segs = []; ps = []
    for i in range(W):
        a, b = i * step, min(NF, (i + 1) * step)
        o = TMP / f"seg{i:02d}.mp4"; segs.append(o)
        ps.append(Process(target=run, args=(a, b, o))); ps[-1].start()
    for p in ps: p.join()
    (TMP / "list.txt").write_text("".join(f"file '{s.as_posix()}'\n" for s in segs), encoding="utf-8")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", str(TMP / "list.txt"), "-i", str(AUDIO),
        "-map", "0:v", "-map", "1:a", "-c:v", "copy", "-c:a", "copy", "-shortest", "-movflags", "+faststart", str(OUT)], check=True)
    print("done", OUT, NF, "frames")
