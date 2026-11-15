#!/usr/bin/env bash

# Print a sorted CSV for -DincludeModules, or an empty line for the full suite.
# Compare the merge base with the tracked working tree; keep reasons on stderr.
# Example: ./supporting_scripts/get_changed_modules.sh origin/develop
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <branch_to_compare>" >&2
    exit 1
fi

SCRIPT_DIRECTORY=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
exec python3 "$SCRIPT_DIRECTORY/select_server_test_modules.py" "$1"
