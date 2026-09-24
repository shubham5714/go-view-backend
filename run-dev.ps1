# Load .env, build with mvnd/mvn, run the WAR with JDK 17 --add-opens.
# Usage:
#   .\run-dev.ps1
#   .\run-dev.ps1 -SkipBuild
#   .\run-dev.ps1 -Profile prod

param(
  [switch]$SkipBuild,
  [string]$Profile = ""
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$envFile = Join-Path $PSScriptRoot ".env"
if (-not (Test-Path $envFile)) {
  Write-Error "Missing .env - copy .env.example to .env and fill GOVIEW_DB_*"
}

Get-Content $envFile -Encoding UTF8 | ForEach-Object {
  $line = $_.Trim()
  if (-not $line -or $line.StartsWith("#")) { return }
  $idx = $line.IndexOf("=")
  if ($idx -lt 1) { return }
  $key = $line.Substring(0, $idx).Trim()
  $val = $line.Substring($idx + 1).Trim()
  if (($val.StartsWith('"') -and $val.EndsWith('"')) -or ($val.StartsWith("'") -and $val.EndsWith("'"))) {
    $val = $val.Substring(1, $val.Length - 2)
  }
  Set-Item -Path ("Env:" + $key) -Value $val
}

if ($Profile) {
  $env:SPRING_PROFILES_ACTIVE = $Profile
}

# Do NOT set JAVA_TOOL_OPTIONS / MAVEN_OPTS here - they break the mvnd daemon.
Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
Remove-Item Env:MAVEN_OPTS -ErrorAction SilentlyContinue

if (-not $env:GOVIEW_DB_URL -or $env:GOVIEW_DB_URL -like "*127.0.0.1*") {
  Write-Error "GOVIEW_DB_URL is missing or still pointing at localhost. Check .env"
}

Write-Host ("GOVIEW_DB_URL=" + $env:GOVIEW_DB_URL)
Write-Host ("SPRING_PROFILES_ACTIVE=" + $env:SPRING_PROFILES_ACTIVE)
Write-Host ("SERVER_PORT=" + $(if ($env:SERVER_PORT) { $env:SERVER_PORT } else { "8083" }))

$war = Join-Path $PSScriptRoot "target\goview_admin-0.0.1-SNAPSHOT.war"

if (-not $SkipBuild) {
  Write-Host "Building (mvnd/mvn clean package -DskipTests)..."
  if (Get-Command mvnd -ErrorAction SilentlyContinue) {
    & mvnd clean package -DskipTests
  } else {
    & mvn clean package -DskipTests
  }
  if ($LASTEXITCODE -ne 0) {
    Write-Error "Build failed"
  }
}

if (-not (Test-Path $war)) {
  Write-Error "WAR not found: $war  (run without -SkipBuild first)"
}

# Ensure local Windows upload dir exists when using defaults
$uploadDir = if ($env:GOVIEW_FILE_URL) { $env:GOVIEW_FILE_URL } else { "D:\upload" }
if (-not (Test-Path $uploadDir)) {
  New-Item -ItemType Directory -Force -Path $uploadDir | Out-Null
  Write-Host "Created upload dir: $uploadDir"
}

Write-Host "Starting Spring Boot from WAR..."
& java `
  --add-opens java.base/java.lang.invoke=ALL-UNNAMED `
  --add-opens java.base/java.lang=ALL-UNNAMED `
  --add-opens java.base/java.math=ALL-UNNAMED `
  --add-opens java.base/java.util=ALL-UNNAMED `
  -jar $war
