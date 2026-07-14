#!/bin/sh

set -eu

mkdir -p ./test-reports
HYPERION_LLM_MOCK_PORT="${HYPERION_LLM_MOCK_PORT:-1234}" node ./support/hyperion-llm-mock/server.mjs > ./test-reports/hyperion-llm-mock.log 2>&1 &
mock_pid=$!

for _ in $(seq 1 50); do
    if curl --silent --fail "http://127.0.0.1:${HYPERION_LLM_MOCK_PORT:-1234}/health" >/dev/null; then
        exit 0
    fi
    sleep 0.1
done

kill "$mock_pid" 2>/dev/null || true
echo "Hyperion LLM mock did not become ready" >&2
exit 1
