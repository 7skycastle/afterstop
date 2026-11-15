# 멈춘 다음 · 시연 영상 편집 소스

레퍼런스 `E:\Claude\영상제작\EV_Report_가치영상`의 형식을 따랐습니다. 남색 배경, 노란 강조, 왼쪽 위 섹션 번호와 진행선, 왼쪽 앱 화면의 손그림 원과 곡선 화살표, 오른쪽의 소제목·큰 제목·원형 불릿, 큰 노란 숫자, 아래 자막 상자, 화면 아래 안내줄, 가운데 정렬 마무리 화면을 그대로 사용했습니다.

## 결과물 (`output/`)

| 파일 | 내용 |
|---|---|
| `멈춘다음_시연영상.mp4` | 1920×1080 · 30fps · H.264 · AAC 48kHz · 2분 11초 |
| `멈춘다음_시연영상_자막.srt` | 내레이션과 앱 음성 자막 |
| `멈춘다음_시연영상_음성.m4a` | 내레이션 + 앱 음성 + 배경음 |

## 장면

| 장면 | 시작 | 화면 |
|---|---|---|
| 1 소개 | 0:00 | 태블릿 홈 + 휴대폰 세로 홈 |
| 2 사고 후보 감지 | 0:17 | 고속도로 시연: 주행 신호 → 감지 → 공통 안내 |
| 3 장면 확보·분석 | 0:32 | 프레임 확보 → 예시 응답 검사 → 고속도로 안내 + 앱 음성 |
| 4 상황별 안내 | 0:50 | 터널 연기 의심 → 이동 어려움 알림 → 겹친 위험 안내 + 앱 음성 |
| 5 판단 보류 | 1:08 | 야간 판단 보류 → 과속방지턱 미전환 |
| 6 실제 입력 | 1:21 | 가상 카메라 → 영상 파일 → 서버 이벤트 → 모델 응답(스텁) |
| 7 구조·기록 | 1:38 | 제품 구성(현대차 어댑터 연결 안 됨) → 사건 기록 |
| 8 마무리 | 1:54 | 요약 + 끝 화면 |

## 소재와 표현 범위

- 앱 화면: Android 에뮬레이터(Pixel Tablet AVD)에서 실제로 조작하면서 `adb emu screenrecord`로 녹화했습니다. 화면을 다시 디자인하지 않았습니다.
- 시연 장면의 도로 영상·차량 신호·분석 응답은 예시입니다. 실제 입력 장면의 분석 응답은 시험용 고정 응답 서버(`stub-vision`)가 만든 것이며, 실제 비전 모델 추론이 아닙니다. 각 장면 아래 안내줄과 끝 화면에 표시했습니다.
- 내레이션: Fish Audio 웹 텍스트 음성 변환으로 만들었습니다(하윤 Hayoon, Fish Audio S2.1 Pro). 장면별 대본은 `tools/narration.json`에 있습니다.
- 앱 음성: 앱에 포함된 하윤 녹음(`assets/audio/highway.mp3`, `combinedObserved.mp3`) 원본입니다. 차량 스피커처럼 들리도록 대역만 약간 제한했습니다.
- 배경음: `tools/music.py`로 직접 합성했습니다.

## 다시 만들기

Python 3 + Playwright(Chromium), ffmpeg가 필요합니다.

```bash
bash tools/extract_frames.sh recordings
```
```bash
python tools/build_timeline.py
```
```bash
python tools/music.py
```
```bash
ffmpeg -y -i audio/narration.wav -i audio/music.wav -filter_complex "[0:a]loudnorm=I=-16:TP=-1.5:LRA=11,aresample=48000,pan=stereo|c0=c0|c1=c0[v];[1:a]volume=-29dB[m];[v][m]amix=inputs=2:normalize=0:duration=first[a]" -map "[a]" -c:a aac -b:a 192k output/멈춘다음_시연영상_음성.m4a
```
```bash
python tools/render.py 6
```

정지 화면 확인: `python tools/render.py stills 20 45 90` (`output/stills/`에 저장)

- 장면 동기화용 화면 변화 시점: `python tools/changes.py <클립 이름>`
- `src/clips/`는 녹화본(`recordings/*.webm`)에서 풀어낸 프레임입니다(약 570MB). 지워도 첫 명령으로 다시 만들 수 있습니다.
