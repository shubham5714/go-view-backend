#!/usr/bin/env bash
# Stop and remove the systemd unit (keeps /opt/goview data unless --purge).
# Usage: sudo ./deploy/linux/uninstall.sh [--purge]

set -euo pipefail

SERVICE_NAME="goview"
APP_DIR="${GOVIEW_APP_DIR:-/opt/goview}"
PURGE=0
[[ "${1:-}" == "--purge" ]] && PURGE=1

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root: sudo $0 $*" >&2
  exit 1
fi

systemctl stop "$SERVICE_NAME" 2>/dev/null || true
systemctl disable "$SERVICE_NAME" 2>/dev/null || true
rm -f "/etc/systemd/system/${SERVICE_NAME}.service"
systemctl daemon-reload

if [[ "$PURGE" -eq 1 ]]; then
  rm -rf "$APP_DIR"
  echo "Removed $APP_DIR"
else
  echo "Left $APP_DIR in place (use --purge to delete WAR, env, uploads)."
fi

echo "Uninstalled ${SERVICE_NAME}.service"
