# 멈춘 다음 (AfterStop)

**사고 직후, 할 일을 하나씩.** 사고로 차가 멈춘 뒤 운전자에게 '지금 할 일'을 하윤 목소리와 큰 화면으로 한 가지씩 알려 주고, 사고 장면을 저장해 AI로 상황(터널·연기·어두운 화면)을 확인하며, 안전 안내가 끝나면 한 번 물어보고 미리 정해 둔 보험사에 연결해 주는 차량용 앱입니다.

- 출품: 2026 현대자동차그룹 차량용 앱 개발 공모전 (Pleos Connect · Pleos App Market)
- 미리 보기 사이트(설치 없이 웹 체험판·화면·영상): https://afterstop-preview.vercel.app

## APK

| 파일 | 설명 |
|---|---|
| [`apk/AfterStop_멈춘다음_release.apk`](apk/) | release 빌드(서명됨). 패키지 `com.afterstop.app`, 최소 Android 8.0(API 26) |

release 빌드는 평문 HTTP를 막으므로 AI 서버 연결은 HTTPS 서버에서만 됩니다. 체험 모드·보험 연결·음성 안내는 서버 없이 동작합니다. PC의 AI 서버(HTTP)로 시험하려면 아래 debug 빌드를 쓰세요.

## 실행 방법

1. APK 설치: `adb install -r apk/AfterStop_멈춘다음_release.apk` (또는 기기에서 파일을 열어 설치)
2. 앱 첫 화면 **체험 시작** → 고속도로/터널/야간/과속방지턱 중 하나 선택. 서버·권한 없이 감지 → 장면 저장 → 예시 AI 결과 → 하윤 음성 안내 → 보험사 연결 질문까지 보여 줍니다. 체험 화면은 늘 "체험 중 · 실제 사고가 아니에요"로 표시합니다.
3. **설정 → 내 보험 정보**에 보험사 이름과 긴급출동 번호를 넣으면, **연결** 탭의 **충격 테스트** 뒤 "○○에 연결할까요?"를 묻고 '연결해 줘'(말 또는 버튼)에만 전화 화면을 엽니다. 이동 어려움·연기 신호가 있으면 묻지 않고 119·구조 요청을 먼저 안내합니다.
4. 실제 입력: **연결** 탭에서 휴대폰 카메라 또는 블랙박스 영상 파일 → 사고 장면 저장 → (동의 후) AI로 상황 확인.

자세한 실행·서버 연결: [docs/RUN_ANDROID_KO.md](docs/RUN_ANDROID_KO.md), AI 서버: [backend/README_KO.md](backend/README_KO.md)

## 빌드

```bat
cd android
set JAVA_HOME=C:\path\to\jdk17
gradlew.bat testDebugUnitTest assembleDebug
```

- Kotlin 2.2 · Jetpack Compose(Material3) · CameraX · OkHttp · AGP 8.13 · Gradle 8.14 · compileSdk/targetSdk 36 · minSdk 26 · JDK 17
- 작업 경로에 한글이 있으면 단위 테스트가 실패할 수 있습니다(영문 경로 또는 `subst` 가상 드라이브 사용).
- release 서명: 저장소 밖 키 파일을 `AFTERSTOP_KEYSTORE`, `AFTERSTOP_KEYSTORE_PASSWORD` 환경 변수(또는 같은 이름의 gradle 속성 `afterstopKeystore` 등)로 넘긴 뒤 `gradlew.bat assembleRelease`. 키는 저장소에 넣지 않습니다.

## 테스트 환경

| 항목 | 내용 |
|---|---|
| 확인한 환경 | Android Emulator · Pixel Tablet AVD · Android 15(API 35) · 화면 2560×1600, 320dpi(가로) |
| 휴대폰 세로 화면 | 같은 앱이 좁은 화면에서 1열·아래 메뉴로 바뀜(에뮬레이터 확인) |
| 단위 테스트 | 45개 통과 (감지 규칙·AI 응답 검사·안내 선택·네트워크·보험 대답 해석) |
| Pleos Connect 에뮬레이터 | **아직 사용하지 않음.** Pleos Playground SDK·에뮬레이터를 받는 대로 같은 앱을 차량 화면 규격에서 확인하고, 차량 신호·빌트인 캠·SOS 연결부를 실제 구현으로 바꿀 예정 |
| 필요한 설정 | 체험: 없음 · 보험 연결: 설정에 보험사 정보 입력(마이크·전화 권한은 선택) · AI 확인: `backend/` 서버 + Ollama 비전 모델(선택) |

## 구조

- `android/` — 앱 소스. 교체 가능한 연결부(VideoSource, TelemetrySource, IncidentDetector, VisionClient, GuidancePolicy, AudioPlayer, EmergencyBridge). 현대차 전용 연결부(`oem/HyundaiAdapters.kt`)는 '연결 안 됨'만 보고하는 자리표시입니다.
- `backend/` — 의존성 없는 Node.js AI 서버(로컬 Ollama 비전 모델 중계, 응답 검사, 토큰·허용 주소 제한).
- `preview-site/` — 미리 보기 정적 사이트.
- `docs/` — 기획서 초안, 검증 결과([VERIFICATION.md](docs/VERIFICATION.md)), 실행 안내, 화면 캡처.
- `reference/` — 이전 웹 시제품(기준 자료).
- `video/` — 시연 영상 편집 소스(큰 녹화·출력 파일은 저장소에서 제외).

## 솔직한 현황

- 확인함: 에뮬레이터에서 체험 흐름, 하윤 음성 재생, 보험사 질문 → 저장한 번호가 입력된 전화 화면 열림, 카메라·영상 파일 입력, 시험용 고정 응답 서버와의 연결 흐름.
- 아직 확인 못 함: 실제 AI 비전 모델 추론, 실제 휴대폰·차량 시험, 실제 사고 감지 정확도, 현대차 차량 연동, 실제 사람 목소리 인식, 보험사 접수 시스템 연동(전화 연결까지만 구현). 보험 질문 음성은 아직 녹음 전이라 화면 글로 표시합니다.
- 체험 화면의 도로 장면·차량 신호·AI 결과는 예시이며, 시연 영상은 화면 문구 개편 전 녹화본입니다.
