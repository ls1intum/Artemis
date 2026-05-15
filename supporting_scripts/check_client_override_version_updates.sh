#!/bin/bash

# Reports pnpm override entries (across every pnpm-workspace.yaml in the repo) that
# have a newer major/minor release on npm. Patch-level differences in entries that
# use a caret (^) prefix are ignored.
#
# Since pnpm 11, overrides moved out of `package.json` (which used to host them under
# `pnpm.overrides`) and into the per-project `pnpm-workspace.yaml`. This script
# parses that YAML directly (via python3 + pyyaml) and queries the npm registry via
# `npm view`. The root project, the `documentation/` Docusaurus site, and the
# `src/test/playwright/` test workspace each have their own overrides set.

set -e

WORKSPACE_FILES=(
    "pnpm-workspace.yaml"
    "documentation/pnpm-workspace.yaml"
    "src/test/playwright/pnpm-workspace.yaml"
)

if ! command -v python3 >/dev/null 2>&1; then
    echo "Error: python3 is required to parse pnpm-workspace.yaml." >&2
    exit 1
fi
if ! python3 -c "import yaml" 2>/dev/null; then
    echo "Error: PyYAML is required. Install it with 'pip3 install pyyaml'." >&2
    exit 1
fi
if ! command -v npm >/dev/null 2>&1; then
    echo "Error: npm is required (to query the registry via 'npm view')." >&2
    exit 1
fi

list_overrides() {
    # Emits one line per override: "<package>\t<version>". For parent-scoped entries
    # ("monaco-editor>dompurify") we keep the full key so the caller can decide whether
    # to look up the leaf package on npm.
    local file="$1"
    python3 -c "
import yaml, sys
data = yaml.safe_load(open('$file')) or {}
overrides = data.get('overrides', {}) or {}
for k, v in overrides.items():
    print(str(k) + '\t' + str(v))
"
}

check_dep() {
    local file=$1 raw_name=$2 cur=$3
    # Strip 'parent>' prefix and semver-range qualifier ('@<3.4.0') so 'npm view' gets
    # just the package name. Range-scoped overrides like 'dompurify@<3.4.3' resolve to
    # the leaf 'dompurify'.
    local name="${raw_name##*>}"
    name="${name%@*}"
    [[ -z "$name" ]] && return

    local latest
    latest=$(npm view "$name" version 2>/dev/null) || return
    [[ -z "$latest" ]] && return

    if [[ "$cur" =~ ^\^?([0-9]+)\.([0-9]+)\.([0-9]+)([.-].+)?$ ]]; then
        local cur_major="${BASH_REMATCH[1]}" cur_minor="${BASH_REMATCH[2]}"
        local latest_major latest_minor
        latest_major=$(echo "$latest" | cut -d. -f1)
        latest_minor=$(echo "$latest" | cut -d. -f2)
        if (( latest_major > cur_major )) || (( latest_major == cur_major && latest_minor > cur_minor )); then
            echo "$file: $raw_name: Current -> $cur, Latest -> $latest"
        fi
    elif [[ "$cur" != "$latest" ]]; then
        echo "$file: $raw_name: Current -> $cur, Latest -> $latest"
    fi
}

for wf in "${WORKSPACE_FILES[@]}"; do
    if [[ ! -f "$wf" ]]; then
        continue
    fi
    echo "Checking overrides in $wf..." >&2
    while IFS=$'\t' read -r name version; do
        [[ -z "$name" ]] && continue
        check_dep "$wf" "$name" "$version"
    done < <(list_overrides "$wf")
done
