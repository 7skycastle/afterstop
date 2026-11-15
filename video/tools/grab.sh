# grab.sh sNN : 다운로드 폴더의 가장 최근 Fish Audio 결과(.tmp/.mp3)를 audio/tts/sNN.mp3 로 옮긴다
V="E:/Claude/HD-APP/멈춘다음_Claude_개발인계패키지/afterstop-claude/video"
F=$(ls -t ~/Downloads/*.tmp ~/Downloads/*.mp3 2>/dev/null | head -1)
[ -z "$F" ] && { echo "NO FILE"; exit 1; }
mv "$F" "$V/audio/tts/$1.mp3" && ffprobe -v error -show_entries format=duration -of csv=p=0 "$V/audio/tts/$1.mp3" | sed "s/^/$1 /"
