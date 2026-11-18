#!/usr/bin/env bash
# Move an entire sub-package directory into a new package location, rewriting
# `package` declarations in every Java file inside and updating imports across
# the codebase.
#
# Usage:  move-subpackage.sh <old_pkg> <new_pkg> <src_dir>
# Example:
#   move-subpackage.sh \
#       de.tum.cit.aet.artemis.communication.domain.course_notifications \
#       de.tum.cit.aet.artemis.notification.domain.course_notifications \
#       src/main/java/de/tum/cit/aet/artemis/communication/domain/course_notifications
#
# Works on macOS (BSD sed) and Linux (GNU sed).

set -euo pipefail

if [ "$#" -ne 3 ]; then
    echo "usage: $0 <old_pkg> <new_pkg> <src_dir>" >&2
    exit 2
fi

OLD_PKG="$1"
NEW_PKG="$2"
SRC_DIR="$3"

if [ ! -d "$SRC_DIR" ]; then
    echo "error: $SRC_DIR is not a directory" >&2
    exit 1
fi

case "$SRC_DIR" in
    src/test/java/*) target_base="src/test/java" ;;
    *)               target_base="src/main/java" ;;
esac
DST_DIR="$target_base/$(echo "$NEW_PKG" | tr . /)"

sed_inplace() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        sed -i '' "$@"
    else
        sed -i "$@"
    fi
}

# Use git mv to preserve history. Create the parent directory first so git mv
# treats this as a rename rather than a recursive copy.
mkdir -p "$(dirname "$DST_DIR")"
git mv "$SRC_DIR" "$DST_DIR"

# Rewrite the package declaration in every moved Java file. Handles both
# `package <OLD_PKG>;` (the subpackage root) and `package <OLD_PKG>.nested;`
# (deeper nesting). BSD sed has no `\b`, so split into two explicit patterns.
find "$DST_DIR" -name '*.java' -type f | while read -r f; do
    sed_inplace "s|^package ${OLD_PKG};|package ${NEW_PKG};|" "$f"
    sed_inplace "s|^package ${OLD_PKG}\.|package ${NEW_PKG}.|" "$f"
done

# Update imports across the whole repo. Because the whole sub-package moves
# atomically, rewriting any occurrence of `OLD_PKG.` or `OLD_PKG;` is safe.
mapfile -t files < <(grep -rlE "import (static )?${OLD_PKG//./\\.}(\.|;)" src/ --include='*.java' 2>/dev/null || true)
if [ "${#files[@]}" -gt 0 ]; then
    for f in "${files[@]}"; do
        sed_inplace "s|${OLD_PKG}\.|${NEW_PKG}.|g" "$f"
        sed_inplace "s|${OLD_PKG};|${NEW_PKG};|g" "$f"
    done
fi

echo "moved $SRC_DIR -> $DST_DIR; rewrote imports in ${#files[@]} file(s)"
