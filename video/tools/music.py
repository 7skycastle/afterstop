# 저작권 문제가 없도록 배경음을 직접 합성한다(사인파 패드 + 부드러운 벨). 레퍼런스 영상 제작 도구와 같은 방식.
import numpy as np, wave, json, re, pathlib
ROOT = pathlib.Path(__file__).resolve().parent.parent
total = json.loads(re.search(r"=\s*(\{.*\});", (ROOT/"src"/"timeline.js").read_text(encoding="utf-8"), re.S).group(1))["total"]
SR = 48000; n = int(total*SR)+SR; t = np.arange(n)/SR
f = lambda m: 440*2**((m-69)/12)
chords = [[48,55,60,64,71],[45,52,57,60,67],[41,48,53,57,64],[43,50,55,59,62]]  # Cmaj7 Am7 Fmaj7 G
L = 8.0; out = np.zeros(n)
for i in range(int(total/L)+2):
    st = i*L; ch = chords[i % 4]
    seg = (t >= st-1.5) & (t < st+L+1.5); tt = t[seg]-st
    env = np.clip((tt+1.5)/2.5,0,1)*np.clip((L+1.5-tt)/2.5,0,1)
    s = sum(np.sin(2*np.pi*f(m)*tt + k) + .5*np.sin(2*np.pi*f(m)*1.003*tt) for k,m in enumerate(ch))
    out[seg] += env*s/len(ch)*.5
    for j, m in enumerate([ch[2]+12, ch[3]+12, ch[4]+12, ch[3]+12]):   # 벨 아르페지오
        bt = st + j*2.0; sg = (t >= bt) & (t < bt+3); x = t[sg]-bt
        out[sg] += .10*np.sin(2*np.pi*f(m)*x)*np.exp(-x*1.8)*np.clip(x/.02,0,1)
k = 200; out = np.convolve(out, np.ones(k)/k, mode="same")
out *= np.clip(t/3,0,1)*np.clip((total-t)/5,0,1)
out = out/np.abs(out).max()*.9
st = np.stack([out, np.roll(out, 240)], 1)
with wave.open(str(ROOT/"audio"/"music.wav"), "wb") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes((st*32767).astype("<i2").tobytes())
print("music", total)
