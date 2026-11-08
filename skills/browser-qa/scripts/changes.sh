#!/usr/bin/env bash
# Usage: changes.sh <checkout> <base-ref> > <run>/inventory.tsv
# One row per changed hunk: id, kind, file, line, enclosing symbol from the hunk header.
set -euo pipefail

wt=${1:?checkout}
base=${2:?base ref, e.g. origin/develop}
cd "$wt"
mb=$(git merge-base "$base" HEAD)

kind_of() {
    case "$1" in
        src/test/* | *.spec.ts) echo test ;;
        documentation/* | *.md | *.mdx) echo docs ;;
        src/main/java/*/web/*Resource.java) echo server-endpoint ;;
        src/main/java/*/dto/*) echo server-dto ;;
        src/main/java/*/domain/*) echo server-domain ;;
        src/main/java/*/repository/*) echo server-repository ;;
        src/main/java/*/service/*) echo server-service ;;
        src/main/java/*/config/* | src/main/java/*/security/*) echo server-config ;;
        src/main/java/*) echo server-other ;;
        src/main/resources/config/liquibase/*) echo db-migration ;;
        src/main/resources/config/*) echo server-config ;;
        src/main/resources/templates/*) echo server-template ;;
        src/main/webapp/i18n/*) echo client-i18n ;;
        src/main/webapp/app/openapi/*) echo client-generated ;;
        src/main/webapp/*.route.ts | src/main/webapp/*.routes.ts | src/main/webapp/*-routes.ts) echo client-route ;;
        src/main/webapp/*.html) echo client-template ;;
        src/main/webapp/*.scss | src/main/webapp/*.css) echo client-style ;;
        src/main/webapp/*.component.ts) echo client-component ;;
        src/main/webapp/*.service.ts | src/main/webapp/*api*.ts) echo client-service ;;
        src/main/webapp/*) echo client-other ;;
        *) echo other ;;
    esac
}

printf 'id\tkind\tfile\tline\tsymbol\n'
# Git names each changed file NUL-delimited, so paths need no unquoting. Each
# file's own diff is read for "@@" lines only; with -U0 every content line
# starts with +, - or \, so no content line can pass for a hunk header.
n=0
git diff -z --name-status --no-ext-diff --find-renames "$mb" HEAD | while IFS= read -r -d '' status; do
    IFS= read -r -d '' path
    paths=("$path")
    case "$status" in R* | C*) IFS= read -r -d '' path; paths+=("$path") ;; esac
    for p in "${paths[@]}"; do
        case "$p" in *$'\t'* | *$'\n'*) echo "changes.sh: path with a tab or newline is not supported: ${p@Q}" >&2; exit 1 ;; esac
    done
    kind=$(kind_of "$path")
    if [ "$status" = D ]; then
        printf 'D-%s\t%s\t%s\t0\tfile deleted\n' "$(basename "$path")" "$kind" "$path"
    fi
    patch=$(git --literal-pathspecs diff -U0 --no-color --no-ext-diff --no-textconv --find-renames "$mb" HEAD -- "${paths[@]}") ||
        { echo "changes.sh: git diff failed for ${paths[*]@Q}" >&2; exit 1; }
    hunks=0
    while IFS= read -r l; do
        case "$l" in
            '@@ '*)
                line=$(sed -E 's/^@@ -[0-9,]+ \+([0-9]+).*/\1/' <<<"$l")
                sym=$(sed -E 's/^@@ [^@]+ @@ ?//' <<<"$l" | tr '\t' ' ' | cut -c1-160)
                n=$((n + 1)); hunks=$((hunks + 1))
                printf 'C%03d\t%s\t%s\t%s\t%s\n' "$n" "$kind" "$path" "$line" "$sym"
                ;;
        esac
    done <<<"$patch"
    if [ "$hunks" = 0 ] && [ "$status" != D ]; then
        n=$((n + 1))
        printf 'C%03d\t%s\t%s\t0\t%s\n' "$n" "$kind" "$path" "no text hunk (binary, mode or pure rename)"
    fi
done
