#!/usr/bin/env bash
# Move a list of Java files into a new package, rewriting the `package`
# declaration in each moved file and updating imports across the codebase
# ONLY for the specific class names being moved (not for unrelated classes
# that happen to share the old package).
#
# Usage:  move-package.sh <old_pkg> <new_pkg> <file1> [file2 ...]
# Example:
#   move-package.sh \
#       de.tum.cit.aet.artemis.communication.domain \
#       de.tum.cit.aet.artemis.notification.domain \
#       src/main/java/de/tum/cit/aet/artemis/communication/domain/CourseNotification.java
#
# Works on macOS (BSD sed) and Linux (GNU sed).

set -euo pipefail

if [ "$#" -lt 3 ]; then
    echo "usage: $0 <old_pkg> <new_pkg> <file> [file...]" >&2
    exit 2
fi

OLD_PKG="$1"; shift
NEW_PKG="$1"; shift

NEW_DIR_MAIN="src/main/java/$(echo "$NEW_PKG" | tr . /)"
NEW_DIR_TEST="src/test/java/$(echo "$NEW_PKG" | tr . /)"

sed_inplace() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        sed -i '' "$@"
    else
        sed -i "$@"
    fi
}

classes=()
moved_count=0

for f in "$@"; do
    if [ ! -f "$f" ]; then
        echo "skip: $f (not a file)" >&2
        continue
    fi
    base=$(basename "$f")
    class="${base%.java}"
    case "$f" in
        src/test/java/*) target_dir="$NEW_DIR_TEST" ;;
        *)               target_dir="$NEW_DIR_MAIN" ;;
    esac
    mkdir -p "$target_dir"
    # Rewrite the package declaration. Exact match `^package OLD;` — BSD sed
    # does not support `\b` (word boundary).
    sed_inplace "s|^package ${OLD_PKG};|package ${NEW_PKG};|" "$f"
    git mv "$f" "$target_dir/$base"
    classes+=("$class")
    moved_count=$((moved_count + 1))
done

# Update import statements across the whole repo, ONLY for the specific
# classes that were just moved. Handles three import forms:
#   1) import OLD_PKG.ClassName;
#   2) import OLD_PKG.ClassName.NestedThing;
#   3) import static OLD_PKG.ClassName.X;
files_changed=0
for class in "${classes[@]}"; do
    mapfile -t files < <(grep -rlE "^import (static )?${OLD_PKG//./\\.}\.${class}([.;])" src/ --include='*.java' 2>/dev/null || true)
    for f in "${files[@]}"; do
        sed_inplace "s|^import \(static \)\{0,1\}${OLD_PKG}\.${class}\.|import \1${NEW_PKG}.${class}.|g" "$f"
        sed_inplace "s|^import \(static \)\{0,1\}${OLD_PKG}\.${class};|import \1${NEW_PKG}.${class};|g" "$f"
        files_changed=$((files_changed + 1))
    done
done

echo "moved ${moved_count} file(s); rewrote imports in ${files_changed} file occurrence(s)"
