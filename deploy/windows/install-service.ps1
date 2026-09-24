# Optional: install GoView API as a Windows service via NSSM.
# Requires NSSM on PATH: https://nssm.cc/download
#
# Usage (Admin PowerShell):
#   .\deploy\windows\install-service.ps1
#   .\deploy\windows\install-service.ps1 -WarPath D:\builds\goview.war
#
# For day-to-day test/dev, prefer:  .\run-dev.ps1

param(
  [string]$ServiceName = "goview",
  [string]$InstallDir = "C:\goview",
  [string]$WarPath = ""
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")

if (-not (Get-Command nssm -ErrorAction SilentlyContinue)) {
  Write-Error "nssm not found on PATH. Download from https://nssm.cc/download and add to PATH, or use .\run-dev.ps1 for foreground runs."
}

$java = (Get-Command java -ErrorAction Stop).Source
if (-not $WarPath) {
  $WarPath = Join-Path $Root "target\goview_admin-0.0.1-SNAPSHOT.war"
}
if (-not (Test-Path $WarPath)) {
  Write-Error "WAR not found: $WarPath — build with: mvn -DskipTests package"
}

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $InstallDir "upload") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $InstallDir "logs") | Out-Null

$warDest = Join-Path $InstallDir "goview.war"
Copy-Item -Force $WarPath $warDest

$envFile = Join-Path $InstallDir "goview.env"
if (-not (Test-Path $envFile)) {
  Copy-Item (Join-Path $Root ".env.example") $envFile
  Add-Content $envFile @"

SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8083
GOVIEW_OSS_FILE=file:$($InstallDir.Replace('\','/'))/upload/
GOVIEW_FILE_URL=$InstallDir\upload
GOVIEW_HTTP_URL=http://127.0.0.1:8083/
"@
  Write-Host "Created $envFile — edit GOVIEW_DB_* before starting."
}

$existing = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($existing) {
  nssm stop $ServiceName confirm
  nssm remove $ServiceName confirm
}

$jvmArgs = @(
  "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
  "--add-opens", "java.base/java.lang=ALL-UNNAMED",
  "--add-opens", "java.base/java.math=ALL-UNNAMED",
  "--add-opens", "java.base/java.util=ALL-UNNAMED",
  "-jar", $warDest
)

nssm install $ServiceName $java
nssm set $ServiceName AppParameters ($jvmArgs -join " ")
nssm set $ServiceName AppDirectory $InstallDir
nssm set $ServiceName AppStdout (Join-Path $InstallDir "logs\stdout.log")
nssm set $ServiceName AppStderr (Join-Path $InstallDir "logs\stderr.log")
nssm set $ServiceName AppEnvironmentExtra "SPRING_PROFILES_ACTIVE=prod"

# Load env file into service environment (NSSM AppEnvironmentExtra is limited;
# prefer a wrapper — write a small launcher that loads goview.env)
$launcher = Join-Path $InstallDir "start-goview.ps1"
@"
`$ErrorActionPreference = 'Stop'
Get-Content '$envFile' | ForEach-Object {
  `$line = `$_.Trim()
  if (-not `$line -or `$line.StartsWith('#')) { return }
  `$i = `$line.IndexOf('=')
  if (`$i -lt 1) { return }
  Set-Item -Path ('Env:' + `$line.Substring(0,`$i).Trim()) -Value `$line.Substring(`$i+1).Trim().Trim('"','''')
}
& '$java' --add-opens java.base/java.lang.invoke=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.math=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED -jar '$warDest'
"@ | Set-Content -Encoding UTF8 $launcher

nssm set $ServiceName Application (Get-Command powershell.exe).Source
nssm set $ServiceName AppParameters "-NoProfile -ExecutionPolicy Bypass -File `"$launcher`""

Write-Host @"

Installed Windows service '$ServiceName'
  Dir : $InstallDir
  Env : $envFile

Next:
  notepad $envFile
  nssm start $ServiceName
  nssm status $ServiceName

Or for quick tests without a service:
  cd $Root
  .\run-dev.ps1
"@
