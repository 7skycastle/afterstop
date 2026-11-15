# 에뮬레이터 녹화(webm, 2560x1600 30fps)를 1440x900 JPEG 프레임으로 푼다. 사용: bash tools/extract_frames.sh <녹화폴더>
V="$(cd "$(dirname "$0")/.." && pwd)"; R="$1"
for f in "$R"/r_*.webm "$R"/t_highway.webm; do n=$(basename "$f" .webm); mkdir -p "$V/src/clips/$n"
  ffmpeg -v error -y -i "$f" -vf "fps=30,scale=1440:900:flags=lanczos" -q:v 3 "$V/src/clips/$n/%05d.jpg" && echo "$n $(ls "$V/src/clips/$n" | wc -l)"; done
