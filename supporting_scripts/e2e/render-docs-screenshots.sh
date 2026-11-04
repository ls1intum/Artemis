#!/bin/bash
set -Eeuo pipefail

# =============================================================================
# Renders the documentation screenshots from the demo data, in one command.
# =============================================================================
# Starts its own PostgreSQL container, Artemis server (profile `demo`) and client on ports of its own, renders every entry of
# documentation/screenshots/manifest.json with src/test/playwright/docs-screenshots, compares the images with the checked-in ones or
# replaces them, and stops everything it started. It never touches a process or a container that it did not start, so it can run next
# to a development environment. A port that is already in use is an error, not something to free.
#
# Usage:
#   ./supporting_scripts/e2e/render-docs-screenshots.sh [options]
#
# Options:
#   --update            Replace the checked-in images with the rendered ones. Look at them (git diff shows the changed files) before committing.
#   --render-only       Render into build/docs-screenshots and stop, without comparing (the weekly workflow compares in a later job).
#   --only <ids>        Render only these manifest ids, comma separated.
#   --browser <where>   `container` (default): the browser runs in the Playwright image that the CI uses, so fonts and anti-aliasing are the
#                       same on every machine and the images can be compared with the ones of the weekly workflow. `host`: use the
#                       Playwright browsers of this machine, which is faster but renders text slightly differently.
#   --threshold <share> Share of changed pixels that counts as a difference, default 0.01.
#   --skip-install      Do not run pnpm install and the build of the UI kit (use when the dependencies are current).
#   --keep              Leave the database, server and client running (stop them with --stop).
#   --stop              Stop what a previous run with --keep left, then exit.
#   --help              Show this text.
#
# Environment (all optional): DOCS_SHOTS_SERVER_PORT (18080), DOCS_SHOTS_CLIENT_PORT (19000), DOCS_SHOTS_SSH_PORT (17921),
# DOCS_SHOTS_DB_PORT (15432), SERVER_READY_TIMEOUT in seconds (900), PLAYWRIGHT_IMAGE.
# Needs Docker, Java 25 (JAVA_HOME), Node 24 and pnpm, as described in the local setup guide.
# =============================================================================

UPDATE=false
RENDER_ONLY=false
ONLY=""
BROWSER="container"
THRESHOLD="0.01"
SKIP_INSTALL=false
KEEP=false
STOP=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --update) UPDATE=true; shift ;;
        --render-only) RENDER_ONLY=true; shift ;;
        --only) ONLY="${2:?--only needs a comma separated list of ids}"; shift 2 ;;
        --browser) BROWSER="${2:?--browser needs container or host}"; shift 2 ;;
        --threshold) THRESHOLD="${2:?--threshold needs a number}"; shift 2 ;;
        --skip-install) SKIP_INSTALL=true; shift ;;
        --keep) KEEP=true; shift ;;
        --stop) STOP=true; shift ;;
        --help) sed -n '3,34p' "$0"; exit 0 ;;
        *) echo "Unknown option: $1" >&2; exit 1 ;;
    esac
done
if [[ "$BROWSER" != "container" && "$BROWSER" != "host" ]]; then
    echo "--browser must be container or host" >&2
    exit 1
fi

cd "$(dirname "$0")/../.."
ROOT="$(pwd)"
SERVER_PORT="${DOCS_SHOTS_SERVER_PORT:-18080}"
CLIENT_PORT="${DOCS_SHOTS_CLIENT_PORT:-19000}"
SSH_PORT="${DOCS_SHOTS_SSH_PORT:-17921}"
DB_PORT="${DOCS_SHOTS_DB_PORT:-15432}"
READY_TIMEOUT="${SERVER_READY_TIMEOUT:-900}"
PLAYWRIGHT_IMAGE="${PLAYWRIGHT_IMAGE:-mcr.microsoft.com/playwright:v1.64.1-noble}"
DB_CONTAINER="docs-shots-pg"
STATE_DIR="$ROOT/build/docs-screenshots-stack"
OUTPUT_DIR="build/docs-screenshots"
mkdir -p "$STATE_DIR"

log() { echo "[docs-screenshots] $*"; }

# Stops what this script started: the processes that listen on its own ports and its database container.
stop_stack() {
    local port pid
    for port in "$CLIENT_PORT" "$SERVER_PORT" "$SSH_PORT"; do
        # Only a process that this script started can listen here, because the ports were free when it began.
        for pid in $(lsof -nP -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true); do
            kill "$pid" 2>/dev/null || true
        done
    done
    if [ -f "$STATE_DIR/gradle.pid" ]; then
        kill "$(cat "$STATE_DIR/gradle.pid")" 2>/dev/null || true
        rm -f "$STATE_DIR/gradle.pid"
    fi
    if [ -f "$STATE_DIR/client.pid" ]; then
        kill "$(cat "$STATE_DIR/client.pid")" 2>/dev/null || true
        rm -f "$STATE_DIR/client.pid"
    fi
    docker rm -f "$DB_CONTAINER" >/dev/null 2>&1 || true
    sleep 3
    for port in "$SERVER_PORT" "$SSH_PORT"; do
        for pid in $(lsof -nP -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true); do
            kill -9 "$pid" 2>/dev/null || true
        done
    done
}

if [ "$STOP" = true ]; then
    stop_stack
    log "Stopped."
    exit 0
fi

for port in "$SERVER_PORT" "$CLIENT_PORT" "$SSH_PORT" "$DB_PORT"; do
    if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
        echo "Port $port is in use. This script never stops a process it did not start; choose other ports with DOCS_SHOTS_*_PORT or stop that process yourself." >&2
        exit 1
    fi
done
if docker ps -a --format '{{.Names}}' | grep -qx "$DB_CONTAINER"; then
    echo "The container $DB_CONTAINER exists already. Run with --stop if it is a leftover of this script." >&2
    exit 1
fi

if [ "$KEEP" = false ]; then
    trap stop_stack EXIT
fi

# -----------------------------------------------------------------------------
if [ "$SKIP_INSTALL" = false ]; then
    log "Installing dependencies and building the UI kit"
    pnpm install --frozen-lockfile
    (cd src/test/playwright && pnpm install --frozen-lockfile)
    pnpm run tum-aet-ui:build
fi
pnpm run prebuild -- --develop

# -----------------------------------------------------------------------------
log "Starting PostgreSQL on port $DB_PORT"
docker run -d --name "$DB_CONTAINER" -p "127.0.0.1:${DB_PORT}:5432" -e POSTGRES_USER=Artemis -e POSTGRES_DB=Artemis -e POSTGRES_HOST_AUTH_METHOD=trust \
    "postgres:$(sed -n 's/^POSTGRES_VERSION=//p' .env | head -n 1)-alpine" >/dev/null
for _ in $(seq 1 60); do
    if docker exec "$DB_CONTAINER" pg_isready -U Artemis >/dev/null 2>&1; then break; fi
    sleep 1
done

# -----------------------------------------------------------------------------
if [ -S "/var/run/docker.sock" ]; then
    DOCKER_SOCK="/var/run/docker.sock"
elif [ -S "$HOME/.docker/run/docker.sock" ]; then
    DOCKER_SOCK="$HOME/.docker/run/docker.sock"
else
    DOCKER_SOCK="/var/run/docker.sock"
fi

log "Starting the server on port $SERVER_PORT (log: build/docs-screenshots-stack/server.log)"
(
    export SERVER_PORT
    export SPRING_PROFILES_ACTIVE="artemis,scheduling,localvc,localci,buildagent,core,dev,local,demo"
    export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:${DB_PORT}/Artemis?sslmode=disable"
    export SPRING_DATASOURCE_USERNAME="Artemis"
    export SPRING_DATASOURCE_PASSWORD=""
    export SPRING_LIQUIBASE_CONTEXTS="dev"
    export ARTEMIS_BCRYPTSALTROUNDS="4"
    export ARTEMIS_USERMANAGEMENT_INTERNALADMIN_USERNAME="artemis_admin"
    export ARTEMIS_USERMANAGEMENT_INTERNALADMIN_PASSWORD="artemis_admin"
    export ARTEMIS_USERMANAGEMENT_USEEXTERNAL="false"
    export ARTEMIS_USERMANAGEMENT_PASSKEY_ENABLED="true"
    export ARTEMIS_USERMANAGEMENT_PASSKEY_ADDITIONALALLOWEDORIGINS="http://localhost:${CLIENT_PORT}"
    export ARTEMIS_VERSIONCONTROL_URL="http://localhost:${SERVER_PORT}"
    export ARTEMIS_VERSIONCONTROL_USER="artemis_admin"
    export ARTEMIS_VERSIONCONTROL_PASSWORD="artemis_admin"
    export ARTEMIS_VERSIONCONTROL_SSHPORT="$SSH_PORT"
    export ARTEMIS_VERSIONCONTROL_SSHHOSTKEYPATH="$ROOT/src/test/playwright/ssh-keys"
    export ARTEMIS_CONTINUOUSINTEGRATION_EMPTYCOMMITNECESSARY="true"
    export ARTEMIS_CONTINUOUSINTEGRATION_ARTEMISAUTHENTICATIONTOKENVALUE="demo"
    export ARTEMIS_CONTINUOUSINTEGRATION_BUILDCONTAINERPREFIX="docsshots-local-ci-"
    export ARTEMIS_CONTINUOUSINTEGRATION_DOCKERCONNECTIONURI="unix://$DOCKER_SOCK"
    if [ "$(uname -m)" = "arm64" ] || [ "$(uname -m)" = "aarch64" ]; then
        export ARTEMIS_CONTINUOUSINTEGRATION_IMAGEARCHITECTURE="arm64"
    fi
    export ARTEMIS_GIT_NAME="artemis"
    export ARTEMIS_GIT_EMAIL="artemis@example.com"
    export ARTEMIS_TELEMETRY_ENABLED="false"
    export ARTEMIS_RATELIMITING_EXEMPTADDRESSES="127.0.0.1/32,::1/128"
    export SERVER_URL="http://localhost:${SERVER_PORT}"
    export EUREKA_CLIENT_ENABLED="false"
    export INFO_TESTSERVER="true"
    exec ./gradlew bootRun -x webapp
) > "$STATE_DIR/server.log" 2>&1 &
echo $! > "$STATE_DIR/gradle.pid"

# -----------------------------------------------------------------------------
cat > "$STATE_DIR/proxy.conf.mjs" <<EOF
export default [
    {
        context: ['/api/', '/services/', '/management/', '^/management\$', '/swagger-resources/', '/v3/api-docs/', '/h2-console/', '/auth/', '/health/', '/public/', '/.well-known/', '/webauthn/', '/login/webauthn', '/saml2/', '/login/saml2/', '/oauth2/', '/login/oauth2/'],
        target: 'http://localhost:${SERVER_PORT}',
        secure: false,
        // The browser of the container reaches the client as host.docker.internal. The server sees the request as the one of a browser on its own
        // machine, as in every other setup, instead of rejecting a host and an origin that it does not know.
        changeOrigin: true,
        headers: { Origin: 'http://localhost:${SERVER_PORT}' },
    },
    { context: ['/websocket/'], target: 'ws://127.0.0.1:${SERVER_PORT}', ws: true },
];
EOF

CLIENT_ARGS=(--port "$CLIENT_PORT" --proxy-config "$STATE_DIR/proxy.conf.mjs" --no-hmr)
if [ "$BROWSER" = "container" ]; then
    # The browser in the container reaches the client from outside the loopback interface.
    CLIENT_ARGS+=(--host 0.0.0.0)
fi
log "Starting the client on port $CLIENT_PORT (log: build/docs-screenshots-stack/client.log)"
pnpm exec ng serve "${CLIENT_ARGS[@]}" > "$STATE_DIR/client.log" 2>&1 &
echo $! > "$STATE_DIR/client.pid"

# -----------------------------------------------------------------------------
log "Waiting for the server, the demo data and the client (at most ${READY_TIMEOUT}s)"
deadline=$((SECONDS + READY_TIMEOUT))
until curl -sf "http://localhost:${SERVER_PORT}/management/health/readiness" >/dev/null 2>&1; do
    if [ $SECONDS -gt $deadline ]; then echo "The server did not become ready, see build/docs-screenshots-stack/server.log" >&2; exit 1; fi
    sleep 5
done
until [ "$(curl -s -o /dev/null -w '%{http_code}' -X POST "http://localhost:${SERVER_PORT}/api/core/public/authenticate" -H 'Content-Type: application/json' \
        -d '{"username":"demo_student","password":"demo1234","rememberMe":true}')" = "200" ]; do
    if [ $SECONDS -gt $deadline ]; then echo "The demo data was not created, see build/docs-screenshots-stack/server.log" >&2; exit 1; fi
    sleep 5
done
until curl -sf "http://localhost:${CLIENT_PORT}" >/dev/null 2>&1; do
    if [ $SECONDS -gt $deadline ]; then echo "The client did not start, see build/docs-screenshots-stack/client.log" >&2; exit 1; fi
    sleep 5
done

# -----------------------------------------------------------------------------
if [ "$UPDATE" = true ]; then
    export DOCS_SCREENSHOT_UPDATE=true
else
    rm -rf "${ROOT:?}/${OUTPUT_DIR}"
fi
export CLIENT_PORT
export DOCS_SCREENSHOT_DIR="$OUTPUT_DIR"
if [ -n "$ONLY" ]; then
    export DOCS_SCREENSHOT_ONLY="$ONLY"
fi

log "Rendering (browser: $BROWSER)"
if [ "$BROWSER" = "container" ]; then
    # The development server of the client answers only to a host name that it knows, and to an IP address. The container therefore reaches
    # the host by the IP address that host.docker.internal resolves to.
    docker run --rm --ipc=host --add-host=host.docker.internal:host-gateway --user "$(id -u):$(id -g)" -e HOME=/tmp \
        -v "$ROOT":/work -w /work/src/test/playwright \
        -e CLIENT_PORT -e DOCS_SCREENSHOT_DIR -e DOCS_SCREENSHOT_UPDATE -e DOCS_SCREENSHOT_ONLY -e DOCS_SCREENSHOT_FIXED_TIME \
        "$PLAYWRIGHT_IMAGE" bash -c 'export BASE_URL="http://$(getent ahostsv4 host.docker.internal | head -n 1 | cut -d" " -f1):${CLIENT_PORT}"; \
        exec node node_modules/@playwright/test/cli.js test --config docs-screenshots/playwright.config.ts'
else
    (cd src/test/playwright && BASE_URL="http://localhost:${CLIENT_PORT}" pnpm exec playwright test --config docs-screenshots/playwright.config.ts)
fi

if [ "$RENDER_ONLY" = true ]; then
    log "Rendered into $OUTPUT_DIR."
elif [ "$UPDATE" = true ]; then
    log "The images below documentation/docs were replaced. Review them with git status and git diff --stat, then commit what you want to keep."
    git status --short documentation/docs | head -40
else
    python3 supporting_scripts/check_docs_screenshots.py --compare "$OUTPUT_DIR" --all --threshold "$THRESHOLD"
fi
