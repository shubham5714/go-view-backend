#!/usr/bin/env bash
# Used by systemd ExecStart — loads /opt/goview/goview.env then runs the WAR.
# Also usable for a quick foreground test:
#   sudo -u goview /opt/goview/start.sh

set -euo pipefail

APP_DIR="${GOVIEW_APP_DIR:-/opt/goview}"
ENV_FILE="${GOVIEW_ENV_FILE:-$APP_DIR/goview.env}"
WAR="${GOVIEW_WAR:-$APP_DIR/goview.war}"
JAVA_BIN="${JAVA_BIN:-/usr/bin/java}"

if [[ ! -f "$WAR" ]]; then
  echo "WAR missing: $WAR" >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Env file missing: $ENV_FILE" >&2
  echo "Create it with GOVIEW_DB_URL, GOVIEW_DB_USER, GOVIEW_DB_PASSWORD" >&2
  exit 1
fi

# Strip Windows CRLF, then export KEY=VALUE
while IFS= read -r line || [[ -n "$line" ]]; do
  line="${line%$'\r'}"
  [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
  key="${line%%=*}"
  val="${line#*=}"
  key="${key#"${key%%[![:space:]]*}"}"
  key="${key%"${key##*[![:space:]]}"}"
  [[ -z "$key" ]] && continue
  if [[ "$val" =~ ^\".*\"$ || "$val" =~ ^\'.*\'$ ]]; then
    val="${val:1:${#val}-2}"
  fi
  export "$key=$val"
done < "$ENV_FILE"

# Fail fast with a clear message (avoids cryptic JDBC "${GOVIEW_DB_URL}" errors)
missing=0
for v in GOVIEW_DB_URL GOVIEW_DB_USER GOVIEW_DB_PASSWORD; do
  if [[ -z "${!v:-}" ]]; then
    echo "ERROR: $v is empty or unset in $ENV_FILE" >&2
    missing=1
  fi
done
if [[ "$missing" -ne 0 ]]; then
  exit 1
fi

if [[ "$GOVIEW_DB_URL" == *'${'* ]]; then
  echo "ERROR: GOVIEW_DB_URL still contains a placeholder: $GOVIEW_DB_URL" >&2
  echo "Put the real jdbc:postgresql://... URL in $ENV_FILE (not \${GOVIEW_DB_URL})" >&2
  exit 1
fi

if [[ "$GOVIEW_DB_URL" != jdbc:postgresql://* ]]; then
  echo "ERROR: GOVIEW_DB_URL must start with jdbc:postgresql://  (got: $GOVIEW_DB_URL)" >&2
  exit 1
fi

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"

echo "Starting goview: profile=$SPRING_PROFILES_ACTIVE port=${SERVER_PORT:-8083}"
echo "DB URL host hint: ${GOVIEW_DB_URL%%\?*}"

exec "$JAVA_BIN" \
  --add-opens java.base/java.lang.invoke=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.math=ALL-UNNAMED \
  --add-opens java.base/java.util=ALL-UNNAMED \
  -jar "$WAR"
