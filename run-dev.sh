#!/usr/bin/env bash
# Foreground run for local Linux testing (same idea as run-dev.ps1).
# Usage:
#   ./run-dev.sh              # build + run
#   ./run-dev.sh --skip-build # run existing WAR

set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

SKIP_BUILD=0
if [[ "${1:-}" == "--skip-build" ]]; then
  SKIP_BUILD=1
fi

ENV_FILE="${ROOT}/.env"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing .env — copy .env.example to .env and fill GOVIEW_DB_*" >&2
  exit 1
fi

# Load KEY=VALUE (safe for passwords with @, spaces, etc.)
while IFS= read -r line || [[ -n "$line" ]]; do
  line="${line%$'\r'}"
  [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
  key="${line%%=*}"
  val="${line#*=}"
  key="$(echo "$key" | xargs)"
  [[ -z "$key" ]] && continue
  # strip optional surrounding quotes
  if [[ "$val" =~ ^\".*\"$ || "$val" =~ ^\'.*\'$ ]]; then
    val="${val:1:${#val}-2}"
  fi
  export "$key=$val"
done < "$ENV_FILE"

if [[ -z "${GOVIEW_DB_URL:-}" || "$GOVIEW_DB_URL" == *"127.0.0.1"* ]]; then
  echo "GOVIEW_DB_URL is missing or still pointing at localhost. Check .env" >&2
  exit 1
fi

WAR="${ROOT}/target/goview_admin-0.0.1-SNAPSHOT.war"

if [[ "$SKIP_BUILD" -eq 0 ]]; then
  echo "Building (mvn clean package -DskipTests)..."
  if command -v mvnd >/dev/null 2>&1; then
    mvnd clean package -DskipTests
  else
    mvn clean package -DskipTests
  fi
fi

if [[ ! -f "$WAR" ]]; then
  echo "WAR not found: $WAR (run without --skip-build first)" >&2
  exit 1
fi

echo "GOVIEW_DB_URL=${GOVIEW_DB_URL}"
echo "Starting Spring Boot from WAR on port ${SERVER_PORT:-8083}..."
exec java \
  --add-opens java.base/java.lang.invoke=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.math=ALL-UNNAMED \
  --add-opens java.base/java.util=ALL-UNNAMED \
  -jar "$WAR"
