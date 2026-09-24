#!/usr/bin/env bash
# Install GoView / vScreen API as a systemd service on Ubuntu.
#
# Prerequisites on the VM:
#   - JDK 17+ (java on PATH or at JAVA_HOME)
#   - Built WAR: target/goview_admin-0.0.1-SNAPSHOT.war
#     (build on any machine: mvn -DskipTests package)
#
# Usage (from repo root, as root):
#   sudo ./deploy/linux/install.sh
#   sudo ./deploy/linux/install.sh /path/to/goview_admin-0.0.1-SNAPSHOT.war
#
# Then edit /opt/goview/goview.env and:
#   sudo systemctl enable --now goview
#   sudo journalctl -u goview -f

set -euo pipefail

APP_DIR="${GOVIEW_APP_DIR:-/opt/goview}"
SERVICE_USER="${GOVIEW_USER:-goview}"
SERVICE_NAME="goview"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
UNIT_SRC="$(cd "$(dirname "$0")" && pwd)/goview.service"
WAR_SRC="${1:-${ROOT}/target/goview_admin-0.0.1-SNAPSHOT.war}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root: sudo $0 $*" >&2
  exit 1
fi

if [[ ! -f "$WAR_SRC" ]]; then
  echo "WAR not found: $WAR_SRC" >&2
  echo "Build first:  mvn -DskipTests package" >&2
  exit 1
fi

if [[ ! -f "$UNIT_SRC" ]]; then
  echo "Missing unit file: $UNIT_SRC" >&2
  exit 1
fi

JAVA_BIN="$(command -v java || true)"
if [[ -z "$JAVA_BIN" && -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
  JAVA_BIN="${JAVA_HOME}/bin/java"
fi
if [[ -z "$JAVA_BIN" ]]; then
  echo "java not found. Install JDK 17+:  sudo apt install openjdk-17-jre-headless" >&2
  exit 1
fi

JAVA_VER="$("$JAVA_BIN" -version 2>&1 | head -n1 || true)"
echo "Using $JAVA_BIN ($JAVA_VER)"

if ! id -u "$SERVICE_USER" >/dev/null 2>&1; then
  useradd --system --home-dir "$APP_DIR" --shell /usr/sbin/nologin "$SERVICE_USER"
  echo "Created user: $SERVICE_USER"
fi

mkdir -p "$APP_DIR/upload" "$APP_DIR/logs"
install -m 0644 "$WAR_SRC" "$APP_DIR/goview.war"

ENV_FILE="$APP_DIR/goview.env"
if [[ ! -f "$ENV_FILE" ]]; then
  if [[ -f "$ROOT/.env.example" ]]; then
    cp "$ROOT/.env.example" "$ENV_FILE"
  else
    touch "$ENV_FILE"
  fi
  cat >> "$ENV_FILE" <<'EOF'

# --- Ubuntu service defaults (edit before starting) ---
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8083
GOVIEW_OSS_FILE=file:/opt/goview/upload/
GOVIEW_FILE_URL=/opt/goview/upload
GOVIEW_HTTP_URL=http://127.0.0.1:8083/
EOF
  echo "Created $ENV_FILE — EDIT secrets before starting the service."
else
  echo "Keeping existing $ENV_FILE"
fi

# Patch ExecStart java path in a local copy of the unit if needed
UNIT_DST="/etc/systemd/system/${SERVICE_NAME}.service"
sed "s|/usr/bin/java|${JAVA_BIN}|g" "$UNIT_SRC" > "$UNIT_DST"

chown -R "$SERVICE_USER:$SERVICE_USER" "$APP_DIR"
chmod 640 "$ENV_FILE"
chmod 750 "$APP_DIR" "$APP_DIR/upload" "$APP_DIR/logs"

systemctl daemon-reload
systemctl enable "$SERVICE_NAME"

echo
echo "Installed ${SERVICE_NAME}.service"
echo "  App dir : $APP_DIR"
echo "  WAR     : $APP_DIR/goview.war"
echo "  Env     : $ENV_FILE  (fill GOVIEW_DB_* then start)"
echo
echo "Next:"
echo "  sudo nano $ENV_FILE"
echo "  sudo systemctl start $SERVICE_NAME"
echo "  sudo systemctl status $SERVICE_NAME"
echo "  sudo journalctl -u $SERVICE_NAME -f"
echo
echo "Health check:  curl -sS http://127.0.0.1:8083/  (or your SERVER_PORT)"
echo "Redeploy WAR:  sudo ./deploy/linux/install.sh /path/to/new.war && sudo systemctl restart $SERVICE_NAME"
