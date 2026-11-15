@echo off
chcp 65001 >nul
rem 에뮬레이터용: 루프백에만 바인딩합니다. 에뮬레이터는 http://10.0.2.2:8787 로 접속합니다.
set HOST=127.0.0.1
if "%PORT%"=="" set PORT=8787
cd /d "%~dp0"
node server.cjs
pause
