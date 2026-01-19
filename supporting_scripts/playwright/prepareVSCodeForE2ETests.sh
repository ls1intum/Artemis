#!/usr/bin/env bash

# prepareVSCodeForE2ETests.sh
# 
# Purpose:
#   Prepare the repository for running Playwright end-to-end tests in VS Code.
#   This script installs dependencies and temporarily patches the Playwright
#   configuration so that dotenv loads environment variables from ./playwright.env.
#
# What it does:
#   - Locates the repository root based on this script's location.
#   - Runs `npm ci` in the repo root.
#   - Runs `npm ci` in src/test/playwright.
#   - Temporarily replaces the dotenv.config(...) line in
#     src/test/playwright/playwright.config.ts to use ./playwright.env, verifies
#     the patch, then reverts the change and keeps a backup copy.
#
# Usage:
#   Run this script from anywhere:
#       supporting_scripts/playwright/prepareVSCodeForE2ETests.sh
#   It will determine the repo root automatically. Requires bash, npm, and perl.
#
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

config_file="$repo_root/src/test/playwright/playwright.config.ts"

old_line="dotenv.config({ path: path.join(__dirname, 'playwright.env') });"
old_line_commented="//dotenv.config({ path: path.join(__dirname, 'playwright.env') });"
new_line="dotenv.config({ path: './playwright.env' });"

if [[ ! -f "$config_file" ]]; then
    echo "Error: File not found: $config_file" >&2
    exit 1
fi

echo "==> npm ci (repo root)"
npm ci

echo "==> npm ci (src/test/playwright)"
(cd "$repo_root/src/test/playwright" && npm ci)

backup_file="$(mktemp -t playwright.config.ts.XXXXXX)"
cp "$config_file" "$backup_file"

restore() {
    if [[ -f "$backup_file" ]]; then
        cp "$backup_file" "$config_file"
        rm -f "$backup_file"
    fi
}

trap restore EXIT

echo "==> Patching dotenv.config(...) in $config_file"

export OLD_DOTENV_LINE="$old_line"
export OLD_DOTENV_LINE_COMMENTED="$old_line_commented"
export NEW_DOTENV_LINE="$new_line"

perl -0777 -i -pe 'BEGIN { $old=$ENV{OLD_DOTENV_LINE}; $old2=$ENV{OLD_DOTENV_LINE_COMMENTED}; $new=$ENV{NEW_DOTENV_LINE}; }
    s/\Q$old\E/$new/g;
    s/\Q$old2\E/$new/g;
' "$config_file"

if ! grep -Fq "$new_line" "$config_file"; then
    echo "Error: Expected patched dotenv line not found." >&2
    exit 1
fi

# File restoration is handled by the EXIT trap via the restore() function.

echo "==> Leaving patched file in place at: $config_file"

# Keep the backup file available for manual restore if needed:
echo "Backup saved at: $backup_file"
echo "==> Done"
