#!/usr/bin/env bash
#
# Fails when a tracked file says "frontend" or "backend".
#
# Both words are too vague to say which component is meant, they flip meaning depending on who is
# speaking, and they hide the boundary that actually matters. Name the component instead: the
# client, the server, the grading service, the distributed data provider, the embedding service,
# the database. The full rationale, the mapping table and the exceptions are in
# documentation/docs/developer/guidelines/terminology.mdx.
#
# Usage: ./supporting_scripts/check_terminology.sh [--self-test]
#
# Run from anywhere inside the repository. Exits 0 when clean, 1 with a per-hit report otherwise.
#
# --self-test asserts the patterns still classify a fixed set of strings correctly. It exists
# because every failure mode of this script is a silent pass: `git grep -P` needs a PCRE-enabled
# git, and if that is missing the grep errors out, the hit list comes back empty, and the check
# reports success. CI runs the self-test first so a broken checker fails loudly instead.

set -Eeuo pipefail

cd "$(git rev-parse --show-toplevel)"

# Two patterns, because one regex cannot express both without also matching innocent text.
#
#   WORD_INITIAL  case-insensitive, anchored with \b so that "frontend", "Front-End", "back end"
#                 and "BACKEND" are caught while "feedback endpoint" (the "back end" inside
#                 "feedback endpoint") is not. No trailing \b, so a coined identifier such as
#                 `backendUrl` is still caught.
#   CAMEL_CASE    case-sensitive, for the form the first pattern cannot reach: a capitalised
#                 Frontend/Backend glued to the end of a lowercase word, as in `prewarmBackend`.
WORD_INITIAL='(?i)\b(front|back)[-_ ]?end'
CAMEL_CASE='[a-z](Front|Back)end'

# Third-party identifiers. Each is a name somebody else chose, so renaming it would break the code.
# Add an entry only for a name you did not choose, and say who owns it.
declare -a ALLOWED=(
    'frontendUrl'            # Keycloak realm configuration key
    'backendRefs'            # Kubernetes Gateway API route specification field
    'HttpXhrBackend'         # Angular, @angular/common/http
    'frontend_server_client' # Dart package, in the Dart exercise template lockfile
    'com.docker.backend'     # macOS process name
)

# This file necessarily contains every word it forbids.
readonly SELF='supporting_scripts/check_terminology.sh'
# The guideline page quotes the forbidden words to explain them.
readonly GUIDELINE='documentation/docs/developer/guidelines/terminology.mdx'

# A line stating the rule has to name the words it forbids. Such a line opts out with a trailing
# marker, which renders as nothing in markdown and reads as a deliberate exception in a diff:
#
#     - Never write "frontend" or "backend". <!-- terminology-check: allow -->
#
# Use it only where the words are the subject. Anywhere else, name the component.
readonly OPT_OUT='terminology-check: allow'

allowed_filter() {
    local pattern
    # `grep -F -v` drops a hit whose line contains an allowlisted identifier. A line is only ever
    # dropped for the identifier it actually contains, so a coined name on the same line as an
    # allowlisted one is the single blind spot, and it is one a reviewer would see anyway.
    local -a args=()
    for pattern in "${ALLOWED[@]}"; do
        args+=(-e "$pattern")
    done
    grep -F -v "${args[@]}"
}

self_test() {
    # Deliberately `git grep --no-index` and not plain `grep -P`: this has to exercise the same
    # regex engine the real scan uses. BSD grep on macOS has no -P at all, while git bundles PCRE
    # on every platform, so testing through grep would fail locally and prove nothing about CI.
    #
    # Each case is "expected<TAB>text", where expected is `hit` or `clean`. Line N of the scratch
    # file is case N, so a reported line number identifies the case.
    local -a cases=(
        $'hit\tthe backend returns 2xx'
        $'hit\tthe frontend consumes this DTO'
        $'hit\tthe Front-End team'
        $'hit\ta back end service'
        $'hit\tBACKEND in caps'
        $'hit\tconst backendUrl = 1'
        $'hit\tawait prewarmBackend(jwt)'
        $'hit\tsnake_case front_end name'
        $'clean\tthe request-feedback endpoint'
        $'clean\tdecodes the long-feedback endpoint'
        $'clean\tbackground jobs are fine'
        $'clean\tthe provider is selected by a property'
    )

    # Inside the repository, because `git grep --no-index` refuses a path outside it. The name is
    # dot-prefixed and `--no-exclude-standard` is passed so no .gitignore rule can hide the file
    # and make the whole self-test pass vacuously.
    local scratch
    scratch=$(mktemp "./.terminology-self-test.XXXXXX")
    trap 'rm -f "$scratch"' RETURN

    local case
    for case in "${cases[@]}"; do
        printf '%s\n' "${case#*$'\t'}" >> "$scratch"
    done

    local matched
    matched=$(
        {
            git grep --no-index --no-exclude-standard -nP -- "$WORD_INITIAL" -- "$scratch" || true
            git grep --no-index --no-exclude-standard -nP -- "$CAMEL_CASE" -- "$scratch" || true
        } | cut -d: -f2 | sort -u
    )

    local failed=0 index=0 expected text
    for case in "${cases[@]}"; do
        index=$((index + 1))
        expected=${case%%$'\t'*}
        text=${case#*$'\t'}
        if printf '%s\n' "$matched" | grep -qx "$index"; then
            [ "$expected" = "hit" ] || { echo "FAIL: expected clean, got hit: $text"; failed=1; }
        else
            [ "$expected" = "clean" ] || { echo "FAIL: expected hit, got clean: $text"; failed=1; }
        fi
    done

    if [ "$failed" -ne 0 ]; then
        echo "Terminology checker self-test failed. Is this git built with PCRE support?"
        return 1
    fi
    echo "OK: terminology checker self-test passed (${#cases[@]} cases)."
}

if [ "${1:-}" = "--self-test" ]; then
    self_test
    exit $?
fi

if [ "$#" -gt 0 ]; then
    echo "Unknown argument: $1" >&2
    echo "Usage: $0 [--self-test]" >&2
    exit 2
fi

hits=$(
    {
        git grep -InP -- "$WORD_INITIAL" -- ":!$SELF" ":!$GUIDELINE" || true
        git grep -InP -- "$CAMEL_CASE" -- ":!$SELF" ":!$GUIDELINE" || true
    } | sort -u -t: -k1,1 -k2,2n | grep -F -v -e "$OPT_OUT" | allowed_filter || true
)

if [ -z "$hits" ]; then
    echo "Terminology check passed: no occurrence of 'frontend' or 'backend'."
    exit 0
fi

count=$(printf '%s\n' "$hits" | wc -l | tr -d ' ')

echo "Terminology check failed: $count occurrence(s) of 'frontend' or 'backend'."
echo
printf '%s\n' "$hits"
echo
cat <<'EOF'
Name the component instead:

  frontend  ->  the client, the web client, the user interface
  backend   ->  the server, the application server, or the service (the grading service)
  backend   ->  the provider, when a swappable distributed data implementation is meant
                (Hazelcast, Redis, Local), matching artemis.distributed-data.provider
  backend   ->  the concrete system otherwise: the embedding service, the database,
                the mail transport, the version control system

Full rationale, mapping table and exceptions:
  documentation/docs/developer/guidelines/terminology.mdx

If a hit is a third-party identifier you did not name, add it to ALLOWED in
supporting_scripts/check_terminology.sh together with the owner it belongs to.
EOF
exit 1
