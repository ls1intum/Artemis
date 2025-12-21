#!/usr/bin/env bash
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

echo "==> npm install (repo root)"
npm install

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

revert_line="$old_line"
if grep -Fq "$old_line_commented" "$config_file" && ! grep -Fq "$old_line" "$config_file"; then
    revert_line="$old_line_commented"
fi

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

echo "==> Reverting change"

 export REVERT_LINE="$revert_line"
 perl -0777 -i -pe 'BEGIN { $new=$ENV{NEW_DOTENV_LINE}; $revert=$ENV{REVERT_LINE}; }
     s/\Q$new\E/$revert/g;
 ' "$config_file"
 if ! grep -Fq "$revert_line" "$config_file"; then
     echo "Error: Expected reverted dotenv line not found." >&2
     exit 1
 fi
 rm -f "$backup_file"

echo "==> Leaving patched file in place at: $config_file"

# Keep the backup file available for manual restore if needed:
echo "Backup saved at: $backup_file"

trap - EXIT

echo "==> Done"
