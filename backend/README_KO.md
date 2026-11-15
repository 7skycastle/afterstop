# 멈춘 다음 분석 서버 (backend)

Node 내장 모듈만 사용합니다(npm 설치 불필요, Node 22 이상).

## 실행

- 에뮬레이터: `start-local.bat` 실행. 앱의 서버 주소는 `http://10.0.2.2:8787` 입니다(에뮬레이터 안의 localhost는 에뮬레이터 자신입니다).
- 실제 휴대폰: PC와 같은 Wi-Fi에서, 토큰을 정한 뒤 실행합니다.
  ```powershell
  $env:AFTERSTOP_TOKEN = "<16자 이상 임의 문자열>"
  .\start-lan.ps1 -AllowedHosts "localhost,127.0.0.1,<PC의 LAN IP>" -AllowedClients "127.0.0.1,::1,<휴대폰 IP 또는 192.168.0.0/16>"
  ```
  앱의 서버 주소는 `http://<PC의 LAN IP>:8787` 이며 토큰은 앱 설정에서 입력합니다(APK에 고정하지 않습니다).
- 시작 시 터미널에 LAN 주소와 에뮬레이터 주소가 출력됩니다. 토큰 값은 출력하지 않습니다.

## 방화벽

실제 휴대폰이 접속하려면 Windows 방화벽에서 TCP 8787 인바운드를 허용해야 합니다. `start-lan.ps1`이 제안 명령(`New-NetFirewallRule`)을 출력하지만 자동으로 실행하지는 않습니다. 관리자 PowerShell에서 직접 실행하세요.

## 보안 정책

모든 요청은 클라이언트 IP, Host(호스트명+포트), Origin(있을 때)을 검사합니다. 토큰이 설정되면 `/api/health`를 제외한 `/api/*`는 `Authorization: Bearer <토큰>`이 필요합니다(SSE는 `?token=`도 허용). HOST가 루프백이 아니면 토큰 16자 이상이 필수입니다.

## Ollama 모델 설정

Ollama는 같은 PC에서 실행하며 서버만 접근합니다(루프백 주소만 허용).

```
ollama pull <비전 모델 이름>
set VISION_MODEL=<비전 모델 이름>
```

`/api/health?checkModel=1`(토큰 필요 시 인증 후)로 모델이 설치되어 있는지 확인할 수 있습니다.

## health가 증명하지 않는 것

`/api/health`는 서버에 닿았는지와 모델 이름이 설정/설치되어 있는지만 보여줍니다. 모델이 실제로 추론에 성공한다는 뜻이 아니며(`inferenceVerified`는 항상 `false`), 차량·하드웨어 연결(`hardwareConnected:false`)도 의미하지 않습니다. 추론 성공은 `/api/analyze` 응답으로만 확인됩니다. 실패 시 가짜 결과를 대신 돌려주지 않습니다.

## 테스트

`npm test`
