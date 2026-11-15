# Fish Audio 내레이션(장면별 1파일)을 문장 단위로 나눠 타임라인·SRT·음성 믹스를 만든다.
# 앱 안내 음성(하윤 녹음 11개 중 해당 파일)은 내레이션이 끝난 뒤 별도 구간으로 넣는다.
import json, re, subprocess, pathlib
ROOT = pathlib.Path(__file__).resolve().parent.parent
APP_AUDIO = ROOT.parent / "assets" / "audio"
data = json.loads((ROOT / "tools" / "narration.json").read_text(encoding="utf-8"))
MIN = {1: 17, 2: 15, 3: 15, 4: 15, 5: 13, 6: 17, 7: 16, 8: 17}
LEAD = {1: 2.4}; TAIL = {8: 6.5}

def dur(p):
    return float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(p)],
                                capture_output=True, text=True).stdout)

def bounds(p):
    """앞뒤 무음을 뺀 실제 발화 구간"""
    r = subprocess.run(["ffmpeg", "-v", "info", "-i", str(p), "-af", "silencedetect=n=-42dB:d=0.08", "-f", "null", "-"],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    total = dur(p)
    st = [float(x) for x in re.findall(r"silence_start: ([\d.]+)", r.stderr)]
    en = [float(x) for x in re.findall(r"silence_end: ([\d.]+)", r.stderr)]
    head = en[0] if st and st[0] < 0.02 and en else 0.0
    tail = st[-1] if st and (len(en) < len(st) or abs(en[-1] - total) < 0.03) and st[-1] > head else total
    return head, tail, total

t = 0.0; scenes = []; cues = []; srt = []
for sc in data:
    n = sc["scene"]; p = ROOT / "audio" / "tts" / sc["file"]
    head, tail, total = bounds(p)
    speech = tail - head
    app = sc.get("app")
    app_d = dur(APP_AUDIO / app["file"]) if app else 0
    lead = LEAD.get(n, 1.0); tl = TAIL.get(n, 1.4)
    need = lead + speech + (0.9 + app_d if app else 0) + tl
    d = max(MIN[n], need); lead += (d - need) * 0.3
    d = round(d * 30) / 30
    split = sc["split"] - head
    lines = [{"start": round(lead, 3), "end": round(lead + split - 0.25, 3), "sub": sc["lines"][0]},
             {"start": round(lead + split + 0.1, 3), "end": round(lead + speech, 3), "sub": sc["lines"][1]}]
    cues.append({"file": str(p), "at": t + lead - head})
    s = {"n": n, "start": round(t, 3), "dur": d, "lines": lines}
    if app:
        a0 = lead + speech + 0.9
        s["app"] = {"start": round(a0, 3), "end": round(a0 + app_d, 3), "sub": app["text"], "file": app["file"]}
        cues.append({"file": str(APP_AUDIO / app["file"]), "at": t + a0, "app": True})
    scenes.append(s); t += d
total = round(t, 3)
(ROOT / "src" / "timeline.js").write_text("window.TL = " + json.dumps({"total": total, "scenes": scenes}, ensure_ascii=False, indent=1) + ";\n", encoding="utf-8")

def ts(x):
    ms = int(round(x * 1000)); h, ms = divmod(ms, 3600000); m, ms = divmod(ms, 60000); s_, ms = divmod(ms, 1000)
    return f"{h:02d}:{m:02d}:{s_:02d},{ms:03d}"
k = 1
for s in scenes:
    items = [(l["start"], l["end"], l["sub"]) for l in s["lines"]]
    if "app" in s: items.append((s["app"]["start"], s["app"]["end"], "[앱 안내 음성] " + s["app"]["sub"]))
    for a, b, sub in items:
        srt.append(f"{k}\n{ts(s['start'] + a)} --> {ts(s['start'] + b + 0.4)}\n{sub}\n"); k += 1
(ROOT / "output").mkdir(exist_ok=True)
(ROOT / "output" / "멈춘다음_시연영상_자막.srt").write_text("\n".join(srt), encoding="utf-8-sig")

# 음성 믹스: 각 파일을 정확한 위치에 배치 (앱 음성은 차량 스피커처럼 약간 대역 제한)
inputs, filt, labels = [], [], []
for i, c in enumerate(cues):
    inputs += ["-i", c["file"]]
    ms = int(round(c["at"] * 1000))
    eq = ",highpass=f=180,lowpass=f=6500,volume=0.92" if c.get("app") else ""
    filt.append(f"[{i}:a]aresample=48000,aformat=channel_layouts=mono{eq},adelay={ms}|{ms}[a{i}]"); labels.append(f"[a{i}]")
filt.append("".join(labels) + f"amix=inputs={len(labels)}:normalize=0,apad=whole_dur={total}[out]")
subprocess.run(["ffmpeg", "-v", "error", "-y", *inputs, "-filter_complex", ";".join(filt), "-map", "[out]", "-t", str(total),
                "-ar", "48000", "-ac", "1", "-c:a", "pcm_s16le", str(ROOT / "audio" / "narration.wav")], check=True)
print("total", total, "scenes", [(s["n"], s["dur"]) for s in scenes])
