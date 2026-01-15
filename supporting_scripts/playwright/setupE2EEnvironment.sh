#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"

playwright_dir="$repo_root/supporting_scripts/playwright"
config_file="$repo_root/supporting_scripts/course-scripts/config.ini"

require_file() {
    local file="$1"
    if [[ ! -f "$file" ]]; then
        echo "Error: Required file not found: $file" >&2
        exit 1
    fi
}

read_config_value() {
    local key="$1"
    local file="$2"
    grep -E "^[[:space:]]*${key}[[:space:]]*=" "$file" | head -n 1 | cut -d= -f2- | xargs
}

wait_for_url() {
    local url="$1"
    local timeout_seconds="${2:-300}"
    local interval_seconds="${3:-5}"
    local start_time=$SECONDS

    while (( SECONDS - start_time < timeout_seconds )); do
        if curl -fsS --max-time 2 "$url" >/dev/null 2>&1; then
            return 0
        fi
        sleep "$interval_seconds"
    done

    return 1
}

require_file "$playwright_dir/prepareVSCodeForE2ETests.sh"
require_file "$playwright_dir/setupUsers.sh"
require_file "$config_file"

echo "==> Preparing Playwright dependencies and VS Code settings"
"$playwright_dir/prepareVSCodeForE2ETests.sh"

server_url="$(read_config_value "server_url" "$config_file")"
if [[ -z "$server_url" ]]; then
    server_url="http://localhost:8080/api"
fi

base_url="$server_url"
if [[ "$server_url" == */api ]]; then
    base_url="${server_url%/api}"
fi
health_url="${base_url%/}/management/health"

if curl -fsS --max-time 2 "$health_url" >/dev/null 2>&1; then
    echo "==> Artemis appears to be running already."
else
    os_name="$(uname -s)"
    start_script=""
    if [[ "$os_name" == "Darwin" ]]; then
        start_script="$playwright_dir/runArtemisInDocker_macOS.sh"
    elif [[ "$os_name" == "Linux" ]]; then
        start_script="$playwright_dir/runArtemisInDocker_linux.sh"
    else
        echo "Error: Unsupported OS '$os_name'. Start Artemis manually." >&2
        exit 1
    fi

    require_file "$start_script"

    log_dir="$repo_root/.e2e"
    mkdir -p "$log_dir"
    artemis_log="$log_dir/artemis-dev.log"

    echo "==> Starting Artemis in the background"
    nohup "$start_script" > "$artemis_log" 2>&1 &
    echo "Artemis logs: $artemis_log"
fi

echo "==> Waiting for Artemis at $health_url"
if wait_for_url "$health_url" 300 5; then
    echo "==> Setting up test users"
    "$playwright_dir/setupUsers.sh"
else
    echo "Warning: Artemis did not become ready in time; skipping user setup." >&2
    echo "You can run manually: $playwright_dir/setupUsers.sh" >&2
fi

echo "==> Ready for E2E development"
echo "Run headless tests: $playwright_dir/startPlaywright.sh"
echo "Run UI mode:        $playwright_dir/startPlaywrightUI.sh"
