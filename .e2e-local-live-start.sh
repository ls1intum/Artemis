#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd "$ROOT_DIR"
mkdir -p .e2e-local
COMPOSE_FILE=docker/e2e-local-fast-postgres.yml
kill_tree() {
  local pid=$1
  for child in $(pgrep -P "$pid" 2>/dev/null || true); do kill_tree "$child"; done
  kill "$pid" 2>/dev/null || true
}
stop_tracked_process() {
  local pid_file=$1 name=$2
  [ -f "$pid_file" ] || return 0
  local pid
  pid=$(cat "$pid_file")
  if kill -0 "$pid" 2>/dev/null; then
    local process_cwd
    process_cwd=$(readlink -f "/proc/$pid/cwd" 2>/dev/null || true)
    if [ "$process_cwd" != "$ROOT_DIR" ]; then
      echo "Refusing to stop $name PID $pid because it does not belong to this worktree." >&2
      exit 1
    fi
    kill_tree "$pid"
  fi
  rm -f "$pid_file"
}
require_free_port() {
  local port=$1 name=$2
  local pids
  pids=$(lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | awk 'NR>1 {print $2}' | sort -u || true)
  if [ -n "$pids" ]; then
    echo "Port $port is already used by $name listener PID(s): $pids" >&2
    exit 1
  fi
}
stop_tracked_process .e2e-local/server.pid server
stop_tracked_process .e2e-local/client.pid client
if docker container inspect artemis-postgres >/dev/null 2>&1; then
  compose_worktree=$(docker container inspect --format '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}' artemis-postgres)
  compose_worktree=$(readlink -f "$compose_worktree")
  if [ -n "$compose_worktree" ] && [ "$compose_worktree" != "$ROOT_DIR" ] && [ "$compose_worktree" != "$ROOT_DIR/docker" ]; then
    echo "Refusing to remove the Artemis database owned by another worktree: $compose_worktree" >&2
    exit 1
  fi
fi
docker compose --env-file .env -f "$COMPOSE_FILE" down -v >/dev/null 2>&1 || true
require_free_port 8080 server
require_free_port 9000 client
require_free_port 7921 local-vc-ssh
require_free_port 5432 postgres
docker compose --env-file .env -f "$COMPOSE_FILE" up -d
postgres_ready=false
for _ in {1..60}; do
  if docker exec artemis-postgres pg_isready -U Artemis >/dev/null 2>&1; then postgres_ready=true; break; fi
  sleep 1
done
if [ "$postgres_ready" != "true" ]; then
  echo "PostgreSQL did not become ready." >&2
  exit 1
fi
if [ -S "/var/run/docker.sock" ]; then DOCKER_SOCK="/var/run/docker.sock"; elif [ -S "$HOME/.docker/run/docker.sock" ]; then DOCKER_SOCK="$HOME/.docker/run/docker.sock"; else DOCKER_SOCK="/var/run/docker.sock"; fi
export SPRING_PROFILES_ACTIVE="artemis,scheduling,localvc,localci,buildagent,core,dev"
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/Artemis?sslmode=disable"
export SPRING_DATASOURCE_USERNAME="Artemis"
export SPRING_DATASOURCE_PASSWORD=""
export SPRING_LIQUIBASE_CONTEXTS="dev,e2e"
export ARTEMIS_BCRYPTSALTROUNDS="4"
export ARTEMIS_USERMANAGEMENT_INTERNALADMIN_USERNAME="artemis_admin"
export ARTEMIS_USERMANAGEMENT_INTERNALADMIN_PASSWORD="artemis_admin"
export ARTEMIS_USERMANAGEMENT_USEEXTERNAL="false"
export ARTEMIS_USERMANAGEMENT_PASSKEY_ENABLED="true"
export ARTEMIS_VERSIONCONTROL_URL="http://localhost:8080"
export ARTEMIS_VERSIONCONTROL_USER="artemis_admin"
export ARTEMIS_VERSIONCONTROL_PASSWORD="artemis_admin"
export ARTEMIS_CONTINUOUSINTEGRATION_EMPTYCOMMITNECESSARY="true"
export ARTEMIS_CONTINUOUSINTEGRATION_ARTEMISAUTHENTICATIONTOKENVALUE="demo"
export ARTEMIS_CONTINUOUSINTEGRATION_SPECIFYCONCURRENTBUILDS="true"
export ARTEMIS_CONTINUOUSINTEGRATION_CONCURRENTBUILDSIZE="${HYPERION_LIVE_BUILD_CONCURRENCY:-2}"
export ARTEMIS_CONTINUOUSINTEGRATION_BUILD_IMAGES_C_DEFAULT="ls1tum/artemis-c-minimal-docker:1.0.0"
export ARTEMIS_CONTINUOUSINTEGRATION_DOCKERCONNECTIONURI="unix://$DOCKER_SOCK"
export ARTEMIS_GIT_NAME="artemis"
export ARTEMIS_GIT_EMAIL="artemis@example.com"
export ARTEMIS_VERSIONCONTROL_SSHHOSTKEYPATH="$ROOT_DIR/src/test/playwright/ssh-keys"
export ARTEMIS_VERSIONCONTROL_SSHPORT="7921"
export ARTEMIS_TELEMETRY_ENABLED="false"
export SERVER_URL="http://localhost:8080"
export ARTEMIS_USERMANAGEMENT_PASSKEY_ADDITIONALALLOWEDORIGINS="http://localhost:9000"
export EUREKA_CLIENT_ENABLED="false"
export INFO_TESTSERVER="true"
export ARTEMIS_HYPERION_ENABLED="true"
export ARTEMIS_HYPERION_EXERCISE_GENERATION_ENABLED="true"
export ARTEMIS_HYPERION_AGENT_TRANSCRIPTDIR="$ROOT_DIR/.e2e-local/hyperion-transcripts"
export ARTEMIS_CONTINUOUSINTEGRATION_BUILDAGENT_MAXGENERATIONSANDBOXSLOTS="${HYPERION_LIVE_CONCURRENCY:-1}"
export ARTEMIS_HYPERION_GENERATION_MAXCONCURRENTJOBSPERCORENODE="${HYPERION_LIVE_CONCURRENCY:-1}"
export SPRING_AI_OPENAI_TIMEOUT="8m"
export SPRING_AI_OPENAI_MICROSOFT_FOUNDRY="${SPRING_AI_OPENAI_MICROSOFT_FOUNDRY:-false}"
export SPRING_AI_OPENAI_CHAT_TEMPERATURE="${SPRING_AI_OPENAI_CHAT_TEMPERATURE:-0.4}"
: "${SPRING_AI_OPENAI_BASE_URL:?SPRING_AI_OPENAI_BASE_URL required}"
: "${SPRING_AI_OPENAI_API_KEY:?SPRING_AI_OPENAI_API_KEY required}"
: "${SPRING_AI_OPENAI_CHAT_MODEL:?SPRING_AI_OPENAI_CHAT_MODEL required}"
nohup ./gradlew bootRun -x webapp > .e2e-local/server.log 2>&1 < /dev/null &
echo $! > .e2e-local/server.pid
nohup pnpm start > .e2e-local/client.log 2>&1 < /dev/null &
echo $! > .e2e-local/client.pid
for ((attempt = 0; attempt < 90; attempt++)); do curl -sf http://localhost:8080/management/health >/dev/null 2>&1 && break; if ! kill -0 "$(cat .e2e-local/server.pid)" 2>/dev/null; then echo "server died"; tail -80 .e2e-local/server.log; exit 1; fi; sleep 5; done
curl -sf http://localhost:8080/management/health >/dev/null || { echo "server not ready"; tail -80 .e2e-local/server.log; exit 1; }
for ((attempt = 0; attempt < 60; attempt++)); do curl -sf http://localhost:9000 >/dev/null 2>&1 && break; if ! kill -0 "$(cat .e2e-local/client.pid)" 2>/dev/null; then echo "client died"; tail -80 .e2e-local/client.log; exit 1; fi; sleep 3; done
curl -sf http://localhost:9000 >/dev/null || { echo "client not ready"; tail -80 .e2e-local/client.log; exit 1; }
echo "Live Hyperion E2E services ready"
if [ "${KEEP_LIVE_SERVICES:-false}" = "true" ]; then
  echo "Keeping live services attached; interrupt this session to stop."
  while true; do sleep 3600; done
fi
