#!/usr/bin/env bash
# Compare committed changes with the PR merge base, then select module consumers + smoke.
# Node is available on GitHub's hosted detector runner and is already required by Artemis.
# E2E_FULL_SUITE does not weaken this selection: the workflow runs the exact complement
# when full coverage is requested, and intentionally omits it for ordinary eligible PRs.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec node "$SCRIPT_DIR/select-tests.mjs" "${1:-origin/develop}"
