# Load .env, build with mvnd, run the WAR with JDK 17 --add-opens.
# Usage:  .\run-dev.ps1
# Optional:  .\run-dev.ps1 -SkipBuild

param(
  [switch]$SkipBuild
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

# Do NOT set JAVA_TOOL_OPTIONS / MAVEN_OPTS here - they break the mvnd daemon.
Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
Remove-Item Env:MAVEN_OPTS -ErrorAction SilentlyContinue

if (-not $env:GOVIEW_DB_URL -or $env:GOVIEW_DB_URL -like "*127.0.0.1*") {
  Write-Error "GOVIEW_DB_URL is missing or still pointing at localhost. Check .env"
}

Write-Host ("GOVIEW_DB_URL=" + $env:GOVIEW_DB_URL)
Write-Host ("GOVIEW_DB_USER=" + $env:GOVIEW_DB_USER)

$war = Join-Path $PSScriptRoot "target\goview_admin-0.0.1-SNAPSHOT.war"

if (-not $SkipBuild) {
  Write-Host "Building (mvnd clean package -DskipTests)..."
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

Write-Host "Starting Spring Boot from WAR..."
& java `
  --add-opens java.base/java.lang.invoke=ALL-UNNAMED `
  --add-opens java.base/java.lang=ALL-UNNAMED `
  --add-opens java.base/java.math=ALL-UNNAMED `
  --add-opens java.base/java.util=ALL-UNNAMED `
  -jar $war
