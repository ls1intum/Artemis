#!/bin/bash
# =============================================================================
# Bring up the full local Playwright stack for Artemis E2E tests.
#
# Provisions every dependency the Playwright runner (or the VS Code Playwright
# extension's play button) expects: database, backend, frontend, ssh keys, and
# the playwright workspace itself. Idempotent — skips anything already running.
#
# What it does:
#   1. Ensures Node (version from .nvmrc) is active (via nvm)
#   2. Ensures Docker daemon is running (starts Docker Desktop on macOS if not)
#   3. Ensures Postgres container is up (via e2e-local-fast-postgres.yml)
#   4. Ensures Artemis server is running on :8080
#   5. Ensures frontend is serving on :9000 (ng serve, or prebuilt static + proxy)
#   6. Ensures ssh-keys symlink exists at repo root (for cwd-relative lookup)
#   7. Ensures playwright.env BASE_URL uses localhost
#   8. Runs prepareVSCodeForE2ETests.sh (installs deps + patches config)
#
# Usage:
#   ./start-playwright-stack.sh           # ng serve client (HMR, slow startup)
#   ./start-playwright-stack.sh --static  # prebuilt static bundle + reverse proxy
#                                         #   (no HMR, ~1s startup; builds once if missing)
#   ./start-playwright-stack.sh --stop      # stop ONLY the frontend (server + db stay up)
#   ./start-playwright-stack.sh --stop-all  # full teardown (client + server + postgres)
# =============================================================================

set -e

case "${1:-}" in
    ""|--)        STATIC_MODE=false ;;
    --static)     STATIC_MODE=true ;;
    --stop|--stop-all) ;; # handled below
    *)
        echo "Unknown option: ${1}"
        echo "Usage: $0 [--static | --stop | --stop-all]"
        exit 1
        ;;
esac
STATIC_MODE="${STATIC_MODE:-false}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log()  { echo -e "${BLUE}[stack]${NC} $*"; }
ok()   { echo -e "${GREEN}[stack]${NC} $*"; }
warn() { echo -e "${YELLOW}[stack]${NC} $*"; }
err()  { echo -e "${RED}[stack]${NC} $*"; }

check_client() { curl -sf http://localhost:9000 >/dev/null 2>&1 || curl -sf http://127.0.0.1:9000 >/dev/null 2>&1; }

cd "$(dirname "$0")"
REPO_ROOT="$(pwd)"
LOCAL_DIR=".e2e-local"
COMPOSE_FILE="docker/e2e-local-fast-postgres.yml"
mkdir -p "$LOCAL_DIR"

# ----- Stop modes -----------------------------------------------------------
# --stop      : kill only the frontend (so you can switch ng-serve <-> static
#               cheaply). Leaves server + postgres up — next start is instant.
# --stop-all  : full teardown (client + server + postgres).
if [ "${1:-}" = "--stop" ]; then
    log "Stopping client only (server + postgres stay up)..."
    if [ -f "$LOCAL_DIR/client.pid" ]; then
        kill "$(cat "$LOCAL_DIR/client.pid")" 2>/dev/null || true
    fi
    pkill -f "ng serve" 2>/dev/null || true
    pkill -f "static-server.mjs" 2>/dev/null || true
    rm -f "$LOCAL_DIR/client.pid"
    ok "Client stopped. Server + postgres still running."
    exit 0
fi
if [ "${1:-}" = "--stop-all" ]; then
    log "Stopping client, server, postgres..."
    if [ -f "$LOCAL_DIR/client.pid" ]; then
        kill "$(cat "$LOCAL_DIR/client.pid")" 2>/dev/null || true
    fi
    if [ -f "$LOCAL_DIR/server.pid" ]; then
        kill "$(cat "$LOCAL_DIR/server.pid")" 2>/dev/null || true
    fi
    pkill -f "ng serve" 2>/dev/null || true
    pkill -f "static-server.mjs" 2>/dev/null || true
    pkill -f "bootRun"  2>/dev/null || true
    docker compose --env-file .env -f "$COMPOSE_FILE" down -v 2>/dev/null || true
    rm -f "$LOCAL_DIR/server.pid" "$LOCAL_DIR/client.pid"
    ok "All stopped."
    exit 0
fi

# ----- 1. Node (from .nvmrc) via nvm ----------------------------------------
log "Ensuring correct Node version is active..."
export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
# shellcheck disable=SC1091
[ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"
if command -v nvm >/dev/null 2>&1; then
    nvm use >/dev/null
    ok "Node $(node -v)"
else
    warn "nvm not found — using system node $(node -v 2>/dev/null || echo 'NONE')"
fi

# ----- 2. Docker daemon -----------------------------------------------------
log "Ensuring Docker is running..."
if ! docker info >/dev/null 2>&1; then
    if [ "$(uname)" = "Darwin" ]; then
        warn "Docker not running — starting Docker Desktop..."
        open -a Docker
        for _ in $(seq 1 60); do
            sleep 2
            docker info >/dev/null 2>&1 && break
        done
        docker info >/dev/null 2>&1 || { err "Docker did not start in time"; exit 1; }
    else
        err "Docker is not running. Start Docker and re-run this script."
        exit 1
    fi
fi
ok "Docker ready"

# ----- 3. Postgres ----------------------------------------------------------
log "Ensuring Postgres is up..."
if docker exec artemis-postgres pg_isready -U Artemis -d Artemis >/dev/null 2>&1; then
    ok "Postgres already running"
else
    docker compose --env-file .env -f "$COMPOSE_FILE" up -d
    for _ in $(seq 1 30); do
        docker exec artemis-postgres pg_isready -U Artemis -d Artemis >/dev/null 2>&1 && break
        sleep 2
    done
    docker exec artemis-postgres pg_isready -U Artemis -d Artemis >/dev/null 2>&1 \
        || { err "Postgres failed to start"; exit 1; }
    ok "Postgres ready"
fi

# ----- 4. Server on :8080 ---------------------------------------------------
log "Ensuring server is up on :8080..."
if curl -sf http://localhost:8080/management/health >/dev/null 2>&1; then
    ok "Server already healthy"
else
    # Same env block as run-e2e-tests-local-fast.sh
    export SPRING_PROFILES_ACTIVE="artemis,scheduling,localvc,localci,buildagent,core,dev"
    export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/Artemis?sslmode=disable"
    export SPRING_DATASOURCE_USERNAME="Artemis"
    export SPRING_DATASOURCE_PASSWORD=""
    export SPRING_LIQUIBASE_CONTEXTS="dev,e2e"
    export ARTEMIS_BCRYPTSALTROUNDS="4"
    export ARTEMIS_USERMANAGEMENT_INTERNALADMIN_USERNAME="artemis_admin"
    export ARTEMIS_USERMANAGEMENT_INTERNALADMIN_PASSWORD="artemis_admin"
    export ARTEMIS_USERMANAGEMENT_USEEXTERNAL="false"
    export ARTEMIS_VERSIONCONTROL_URL="http://localhost:8080"
    export ARTEMIS_VERSIONCONTROL_USER="artemis_admin"
    export ARTEMIS_VERSIONCONTROL_PASSWORD="artemis_admin"
    export ARTEMIS_CONTINUOUSINTEGRATION_EMPTYCOMMITNECESSARY="true"
    export ARTEMIS_CONTINUOUSINTEGRATION_ARTEMISAUTHENTICATIONTOKENVALUE="demo"
    export ARTEMIS_CONTINUOUSINTEGRATION_BUILD_IMAGES_C_DEFAULT="ls1tum/artemis-c-minimal-docker:1.0.0"
    if [ -S "$HOME/.docker/run/docker.sock" ]; then
        export ARTEMIS_CONTINUOUSINTEGRATION_DOCKERCONNECTIONURI="unix://$HOME/.docker/run/docker.sock"
    else
        export ARTEMIS_CONTINUOUSINTEGRATION_DOCKERCONNECTIONURI="unix:///var/run/docker.sock"
    fi
    export ARTEMIS_GIT_NAME="artemis"
    export ARTEMIS_GIT_EMAIL="artemis@example.com"
    export ARTEMIS_VERSIONCONTROL_SSHHOSTKEYPATH="$REPO_ROOT/src/test/playwright/ssh-keys"
    export ARTEMIS_VERSIONCONTROL_SSHPORT="7921"
    export ARTEMIS_TELEMETRY_ENABLED="false"
    export SERVER_URL="http://localhost:8080"
    export EUREKA_CLIENT_ENABLED="false"
    export INFO_TESTSERVER="true"
    [ "$(uname -m)" = "arm64" ] && export ARTEMIS_CONTINUOUSINTEGRATION_IMAGEARCHITECTURE="arm64"

    log "Starting server (bootRun)..."
    nohup ./gradlew bootRun -x webapp > "$LOCAL_DIR/server.log" 2>&1 &
    echo $! > "$LOCAL_DIR/server.pid"
    log "Waiting for server (up to 5 min)..."
    for _ in $(seq 1 60); do
        sleep 5
        curl -sf http://localhost:8080/management/health >/dev/null 2>&1 && break
    done
    curl -sf http://localhost:8080/management/health >/dev/null 2>&1 \
        || { err "Server failed to become healthy. Tail: $LOCAL_DIR/server.log"; tail -30 "$LOCAL_DIR/server.log"; exit 1; }
    ok "Server ready"
fi

# ----- 5. Client on :9000 ---------------------------------------------------
log "Ensuring client is up on :9000..."
if check_client; then
    ok "Client already serving"
elif [ "$STATIC_MODE" = true ]; then
    if [ ! -f "build/resources/main/static/index.html" ]; then
        if command -v corepack >/dev/null 2>&1; then
            corepack enable >/dev/null 2>&1 || true
        fi
        log "No prebuilt bundle found — running pnpm run webapp:prod (one-time, ~3-5 min)..."
        pnpm run webapp:prod
    else
        ok "Prebuilt bundle found at build/resources/main/static"
    fi
    log "Starting static server on :9000..."
    nohup node "supporting_scripts/playwright/static-server.mjs" > "$LOCAL_DIR/client.log" 2>&1 &
    echo $! > "$LOCAL_DIR/client.pid"
    for _ in $(seq 1 20); do
        sleep 1
        check_client && break
    done
    check_client \
        || { err "Static server failed. Tail: $LOCAL_DIR/client.log"; tail -30 "$LOCAL_DIR/client.log"; exit 1; }
    ok "Static server ready (starts in ~1s on subsequent runs)"
else
    log "Starting client (pnpm start)..."
    nohup pnpm start > "$LOCAL_DIR/client.log" 2>&1 &
    echo $! > "$LOCAL_DIR/client.pid"
    log "Waiting for client (up to 3 min)..."
    for _ in $(seq 1 60); do
        sleep 3
        check_client && break
    done
    check_client \
        || { err "Client failed to start. Tail: $LOCAL_DIR/client.log"; tail -30 "$LOCAL_DIR/client.log"; exit 1; }
    ok "Client ready"
fi

# ----- 6. ssh-keys symlink at repo root -------------------------------------
log "Ensuring ssh-keys symlink at repo root..."
if [ ! -e "ssh-keys" ]; then
    ln -s src/test/playwright/ssh-keys ssh-keys
    ok "Symlink created"
else
    ok "Symlink already in place"
fi

# ----- 7. playwright.env BASE_URL -> localhost -----------------------------------
log "Ensuring playwright.env uses localhost..."
PW_ENV="src/test/playwright/playwright.env"
DESIRED_BASE_URL="http://localhost:9000"
if grep -q "^BASE_URL=$DESIRED_BASE_URL$" "$PW_ENV"; then
    ok "BASE_URL already $DESIRED_BASE_URL"
else
    sed -i.bak "s|^BASE_URL=.*|BASE_URL=$DESIRED_BASE_URL|" "$PW_ENV" && rm -f "$PW_ENV.bak"
    ok "BASE_URL set to $DESIRED_BASE_URL"
fi

# ----- 8. prepareVSCodeForE2ETests.sh (deps + config patch) -----------------
log "Running prepareVSCodeForE2ETests.sh..."
./supporting_scripts/playwright/prepareVSCodeForE2ETests.sh

echo ""
ok "Stack ready. Run tests via VS Code play button or 'pnpm --prefix src/test/playwright run playwright:test'."
echo "  Logs: $LOCAL_DIR/server.log  |  $LOCAL_DIR/client.log"
echo "  Stop everything: ./start-playwright-stack.sh --stop-all"
