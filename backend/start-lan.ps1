# 실제 휴대폰용: LAN에 바인딩합니다. AFTERSTOP_TOKEN 환경변수(16자 이상)가 필요합니다.
param(
  [int]$Port = 8787,
  [string]$AllowedHosts = 'localhost,127.0.0.1',
  [string]$AllowedClients = '127.0.0.1,::1,192.168.0.0/16'
)
if (-not $env:AFTERSTOP_TOKEN -or $env:AFTERSTOP_TOKEN.Length -lt 16) {
  Write-Error 'AFTERSTOP_TOKEN 환경변수가 없거나 16자 미만입니다. 먼저 설정하세요. 예: $env:AFTERSTOP_TOKEN = "<16자 이상 임의 문자열>"'
  exit 1
}
$env:HOST = '0.0.0.0'
$env:PORT = "$Port"
$env:ALLOWED_HOSTS = $AllowedHosts
$env:ALLOWED_CLIENTS = $AllowedClients
Write-Host "참고: 이 PC의 LAN IP를 -AllowedHosts 에 추가해야 휴대폰의 Host 헤더가 통과합니다."
Write-Host "방화벽 규칙 제안(자동 실행하지 않습니다. 관리자 PowerShell에서 직접 실행):"
Write-Host "  New-NetFirewallRule -DisplayName 'AfterStop Backend' -Direction Inbound -Protocol TCP -LocalPort $Port -Action Allow -Profile Private"
Set-Location $PSScriptRoot
node server.cjs
