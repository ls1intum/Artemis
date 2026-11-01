#!/usr/bin/env bash
# Verify the actual selector for a combined set of modules, or every module with --all.
# Portable to macOS Bash 3.2; Node is already required by Artemis.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec node "$SCRIPT_DIR/verify-selection.mjs" "$@"
