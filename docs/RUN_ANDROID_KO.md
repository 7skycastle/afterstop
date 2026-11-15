# 멈춘 다음 · Android 실행 안내 (Windows 기준)

## 1. 준비

| 항목 | 버전 | 비고 |
|---|---|---|
| JDK | 17 | `JAVA_HOME` 을 JDK 17 로 지정 (예: `C:\dev\jdk17`) |
| Android SDK | platform 36, build-tools 36.0.0 | `android/local.properties` 의 `sdk.dir` 로 지정 |
| Gradle | 8.14.3 | 저장소의 Gradle Wrapper(`android/gradlew.bat`)가 자동으로 받음 |
| Node.js | 22 이상 | 분석 서버(`backend/`) 실행용. 외부 npm 패키지 없음 |
| Ollama + 비전 모델 | 선택 | 실제 AI 분석에만 필요. 이 패키지에는 모델이 없음 |

`android/local.properties` 예시 (저장소에 올리지 않음):

```
sdk.dir=C:/dev/android-sdk
```

## 2. 빌드

**작업 폴더 경로에 한글이 있으면 단위 테스트가 실패합니다**(테스트 실행 JVM이 한글 클래스 경로를 읽지 못함). 영문 경로로 옮기거나, 아래처럼 가상 드라이브를 만들어 그 경로에서 빌드하세요. 가상 드라이브는 재부팅하거나 `subst Q: /d`를 실행하면 사라집니다.

```bat
subst Q: "E:\Claude\HD-APP\멈춘다음_Claude_개발인계패키지\afterstop-claude"
```

```bat
cd /d Q:\android
set JAVA_HOME=C:\dev\jdk17
gradlew.bat clean testDebugUnitTest assembleDebug
```

결과: `android/app/build/outputs/apk/debug/app-debug.apk` (빌드 완료본 사본은 `dist/`).

실제 휴대폰이 PC 서버에 HTTP 로 접속하려면 PC 의 LAN IP 를 debug 빌드의 허용 주소로 넣어 다시 빌드합니다. 다른 주소의 평문 HTTP 는 OS 가 차단합니다.

```bat
gradlew.bat assembleDebug -PafterstopDevHost=192.168.0.10
```

release 빌드는 평문 HTTP 를 전혀 허용하지 않습니다(HTTPS 서버만).

## 3. 설치

```bat
C:\dev\android-sdk\platform-tools\adb.exe install -r dist\afterstop-debug.apk
```

패키지 이름은 `com.afterstop.app.debug`, 앱 이름은 **멈춘 다음** 입니다. 실기기는 개발자 옵션의 USB 디버깅을 켜야 합니다.

## 4. 제품 시연 (서버·네트워크·카메라 권한 불필요)

1. 앱 첫 화면에서 **전체 흐름 시연**을 누릅니다.
2. 제품 시연 화면에서 고속도로 / 터널 / 야간 / 과속방지턱 중 하나를 고르고 다시 **전체 흐름 시연**을 누릅니다.
3. 상단의 주황색 표시줄에 "예시 영상·차량 신호·분석 응답"임이 항상 표시됩니다.

## 5. 분석 서버 연결

### 에뮬레이터

```bat
cd backend
start-local.bat
```

앱 **설정**의 서버 주소: `http://10.0.2.2:8787` (debug 빌드 기본값). 에뮬레이터 안의 `localhost` 는 에뮬레이터 자신이므로 PC 를 가리키지 않습니다. `adb reverse tcp:8787 tcp:8787` 을 실행한 경우에만 `http://localhost:8787` 이 PC 로 연결됩니다.

### 실제 휴대폰 (같은 Wi-Fi)

```powershell
cd backend
$env:AFTERSTOP_TOKEN = "<16자 이상 임의 문자열>"
.\start-lan.ps1 -AllowedHosts "localhost,127.0.0.1,192.168.0.10" -AllowedClients "127.0.0.1,::1,192.168.0.0/24"
```

- 관리자 PowerShell 에서 방화벽 허용(스크립트가 제안만 출력):
  `New-NetFirewallRule -DisplayName 'AfterStop Backend' -Direction Inbound -Protocol TCP -LocalPort 8787 -Action Allow -Profile Private`
- 앱 **설정**: 서버 주소 `http://192.168.0.10:8787`, 접속 토큰에 위 값 입력 → **저장 후 연결 확인**.
- 휴대폰 브라우저로 `http://192.168.0.10:8787/api/health` 가 열리는지 먼저 확인하면 방화벽·네트워크 문제를 빨리 구분할 수 있습니다.

### 실제 AI 모델

```powershell
ollama pull <비전 모델 이름>
$env:VISION_MODEL = "<비전 모델 이름>"
```

서버를 다시 시작한 뒤 앱의 **연결 확인**에서 "모델: 설치 확인"이 나오는지 봅니다. 이것은 모델 설치 여부일 뿐 추론 성공을 뜻하지 않습니다. 추론 성공은 **AI 분석 요청** 결과(배지 "실제 AI 응답")로만 확인됩니다. 실패하면 예시 응답으로 바꾸지 않고 오류와 공통 안내를 보여줍니다.

## 6. 실제 입력 사용 순서

1. **실제 입력** → **휴대폰 카메라**(권한 허용) 또는 **블랙박스 영상 파일**(시스템 파일 선택기).
2. 영상 파일은 슬라이더로 사고 시점을 고릅니다. 시점 기준 -5·-3·-1·0·+2·+5초 프레임을 영상 범위 안에서 확보합니다.
3. **사고 전후 프레임 확보** → 프레임 전송 동의 → **AI 분석 요청**.
4. 사고 후보 이벤트 연결:
   - **테스트 사고 이벤트**: 수동 시험 입력.
   - **서버 이벤트 수신**: PC 에서 `node backend/send-demo-telemetry.cjs` 를 실행하면 예시 신호의 사고 후보가 전달됩니다(실제 사고 아님).
   - **휴대폰 센서 감지**: 선형 가속도 + GPS 속도. 차량 인증 센서가 아니며, 속도 미수신·오래된 값은 무효 처리합니다. 실차 급정거로 시험하지 마세요.
5. 이벤트를 받으면 분석을 기다리지 않고 공통 안내가 먼저 시작됩니다.

## 7. 문제 해결

| 증상 | 확인 |
|---|---|
| 연결 확인이 "서버에 연결하지 못했습니다" | 주소(10.0.2.2 / LAN IP), 서버 실행 여부, 방화벽, 같은 Wi-Fi |
| "debug 빌드의 HTTP 허용 주소가 아닙니다" | `-PafterstopDevHost=<PC IP>` 로 다시 빌드 |
| 서버가 403 `host_not_allowed` | `ALLOWED_HOSTS` 에 PC LAN IP 추가 |
| 서버가 403 `client_not_allowed` | `ALLOWED_CLIENTS` 에 휴대폰 IP 또는 대역 추가 |
| 401 `unauthorized` | 앱 설정의 토큰과 `AFTERSTOP_TOKEN` 일치 여부 |
| 503 `model_not_configured` | `VISION_MODEL` 설정 |
| 504 `model_timeout` | 모델 자원·크기 확인, `MODEL_TIMEOUT_MS` |
