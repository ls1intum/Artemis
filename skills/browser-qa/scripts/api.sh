#!/usr/bin/env bash
# Usage: api.sh <run-dir> <server-port> <user> <out-name> <METHOD> <path> [<json-body> | @<file>]
# Signs in as <user> (password = username, or $PASS), sends one request, and saves
# <run>/api/<out-name>.txt with request and response metadata, and the complete response in <out-name>.body.
# Prints "<status> <METHOD> <path>". Exit 0 whatever the status; judging it is the caller's job.
set -euo pipefail

run=${1:?run dir}
port=${2:?server port}
user=${3:?user, or "anonymous"}
name=${4:?output name, e.g. F01-api-03}
method=${5:?METHOD}
path=${6:?path, e.g. /api/lecture/lectures/1}
body=${7:-}
base="http://localhost:$port"
mkdir -p "$run/api" "$run/api/.jar"
jar="$run/api/.jar/${port}_$user"
case "$body" in @/*) ;; @*) echo "body file must be an absolute path: $body" >&2; exit 2 ;; esac
tmp=$(mktemp -d "$run/api/.call.XXXXXX")
trap 'rm -rf "$tmp"' EXIT

identity_matches() {
    local j=${1:-$jar}
    [ -s "$j" ] || return 1
    curl -fsS -m 20 -b "$j" "$base/api/core/public/account" | \
        node -e 'let data=""; process.stdin.on("data", c => data += c); process.stdin.on("end", () => { try { process.exit(JSON.parse(data).login === process.argv[1] ? 0 : 1); } catch { process.exit(1); } });' "$user"
}

if [ "$user" != anonymous ]; then
    if ! identity_matches; then
        # Sign in into a private jar, then swap it in whole, so parallel calls never read a half-written jar.
        auth=$(QA_USER="$user" QA_PASSWORD="${PASS:-$user}" node -e 'process.stdout.write(JSON.stringify({username:process.env.QA_USER,password:process.env.QA_PASSWORD,rememberMe:false}))')
        code=$(curl -s -m 20 -c "$tmp/jar" -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' \
            -d "$auth" "$base/api/core/public/authenticate") || code=000
        unset auth
        [ "$code" = 200 ] || { echo "sign-in as $user failed: $code" >&2; exit 2; }
        identity_matches "$tmp/jar" || { echo "could not verify identity as $user" >&2; exit 2; }
        mv -f "$tmp/jar" "$jar"
    fi
fi

args=(-s -m 60 -X "$method" -D "$tmp/headers" -o "$tmp/body" -w '%{http_code}')
[ "$user" != anonymous ] && args+=(-b "$jar")
if [ -n "$body" ]; then
    args+=(-H 'Content-Type: application/json' --data-binary "$body")
fi
status=$(curl "${args[@]}" "$base$path") || { echo "no answer from $base$path (server down?)" >&2; exit 3; }

out="$run/api/$name.txt"
{
    echo "### request"
    echo "$method $path as $user"
    if [ -n "$body" ]; then
        if [[ "$body" == @* ]]; then cat "${body#@}"; else echo "$body"; fi
    fi
    echo
    echo "### status $status"
    grep -iE '^(x-artemisapp-|content-type|location)' "$tmp/headers" || true
    echo
    echo "### body"
    echo "Saved to $name.body"
} >"$out"
mv "$tmp/body" "$run/api/$name.body"
echo "$status $method $path"
