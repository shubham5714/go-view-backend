# GoView / vScreen backend (`goview_admin`)

Spring Boot API for the Vue editor. Uses **Postgres/Supabase** (same DB as AI-SOC) and SSO handoff — not the old SQLite + workspace signup flow.

Requires **JDK 17+** (MyBatis-Plus needs `--add-opens`).

---

## Quick start — Windows (dev / test)

```powershell
copy .env.example .env
# edit GOVIEW_DB_URL / GOVIEW_DB_USER / GOVIEW_DB_PASSWORD

.\run-dev.ps1              # build WAR + run on :8083
.\run-dev.ps1 -SkipBuild   # rerun without rebuild
.\run-dev.ps1 -Profile prod
```

Optional Windows service (needs [NSSM](https://nssm.cc/download) on PATH):

```powershell
# Admin PowerShell, after a successful mvn package
.\deploy\windows\install-service.ps1
notepad C:\goview\goview.env   # fill DB + public URL
nssm start goview
```

---

## Quick start — Linux (foreground)

```bash
cp .env.example .env
# edit GOVIEW_DB_*

chmod +x run-dev.sh
./run-dev.sh              # build + run
./run-dev.sh --skip-build
```

---

## Ubuntu VM — systemd service

### 1. Build the WAR (on Windows or the VM)

```bash
mvn -DskipTests package
# → target/goview_admin-0.0.1-SNAPSHOT.war
```

### 2. On the VM

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless

# Copy this repo (or just the WAR + deploy/linux + .env.example) to the VM, then:
chmod +x deploy/linux/*.sh run-dev.sh
sudo ./deploy/linux/install.sh
# or: sudo ./deploy/linux/install.sh /path/to/goview_admin-0.0.1-SNAPSHOT.war
```

### 3. Configure secrets

```bash
sudo nano /opt/goview/goview.env
```

Set at least:

| Variable | Example |
|----------|---------|
| `GOVIEW_DB_URL` | `jdbc:postgresql://…pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0` |
| `GOVIEW_DB_USER` | `postgres.<ref>` |
| `GOVIEW_DB_PASSWORD` | your password |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `GOVIEW_HTTP_URL` | public base URL of this API (trailing `/`) |
| `GOVIEW_OSS_FILE` | `file:/opt/goview/upload/` |
| `GOVIEW_FILE_URL` | `/opt/goview/upload` |

### 4. Start

```bash
sudo systemctl start goview
sudo systemctl status goview
sudo journalctl -u goview -f
```

### 5. Redeploy

```bash
mvn -DskipTests package
sudo ./deploy/linux/install.sh ./target/goview_admin-0.0.1-SNAPSHOT.war
sudo systemctl restart goview
```

### Uninstall

```bash
sudo ./deploy/linux/uninstall.sh          # keep /opt/goview
sudo ./deploy/linux/uninstall.sh --purge  # delete app dir too
```

---

## Environment reference

See `.env.example`. Important paths:

- **Windows defaults** — `D:/upload`, `http://127.0.0.1:8083/`
- **Ubuntu install** — `/opt/goview/upload`, set `GOVIEW_HTTP_URL` to the public URL AI-SOC / Vue will call

AI-SOC should point its GoView base URL at this service (e.g. `http://vm:8083` or your reverse proxy).

---

## Legacy notes

Older README referred to SQLite `sqllite/goview.db` and hard-coded upload paths. That path is obsolete — use Postgres env vars and `GOVIEW_*` upload settings above.
