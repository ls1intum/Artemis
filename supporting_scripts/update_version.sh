#!/bin/bash

# Script to bump the Artemis version across build.gradle, openapi/openapi.yaml and README.md.
#
# Canonical Artemis versions are two-part (X.Y, e.g. "9.2"). A three-part hotfix release
# (X.Y.Z with Z >= 1, e.g. "10.4.1") is allowed as an exception; "X.Y.0" is NOT a valid
# canonical form. build.gradle is the single source of truth — package.json no longer
# carries an Artemis version.
#
# Usage:
#   ./update_version.sh major     # next major: X.Y[.Z] -> (X+1).0
#   ./update_version.sh patch     # bump minor: X.Y[.Z] -> X.(Y+1)
#   ./update_version.sh hotfix    # bump/introduce third component: X.Y -> X.Y.1, X.Y.Z -> X.Y.(Z+1)
#   ./update_version.sh --check   # verify build.gradle, openapi.yaml, README.md all agree on a canonical version

set -euo pipefail

USAGE="Usage: $0 [major|patch|hotfix|--check]"

if [[ $# -ne 1 ]]; then
    echo "$USAGE" >&2
    exit 1
fi

ACTION=$1

BUILD_GRADLE_REGEX='^version = "[0-9]+\.[0-9]+(\.[1-9][0-9]*)?"$'
CANONICAL_REGEX='^[0-9]+\.[0-9]+(\.[1-9][0-9]*)?$'

read_build_gradle_version() {
    local raw
    raw=$(grep -E "$BUILD_GRADLE_REGEX" build.gradle | head -n 1 || true)
    if [[ -z "$raw" ]]; then
        echo "Could not find a valid 'version = \"X.Y[.Z]\"' line in build.gradle (X.Y.0 is rejected)." >&2
        exit 1
    fi
    # Strip the `version = "..."` wrapper.
    echo "$raw" | sed -E 's/^version = "(.*)"$/\1/'
}

read_openapi_version() {
    local raw
    raw=$(grep -E '^  version: "[^"]+"' openapi/openapi.yaml | head -n 1 || true)
    if [[ -z "$raw" ]]; then
        echo "Could not find a quoted 'version: \"X.Y[.Z]\"' line in openapi/openapi.yaml." >&2
        exit 1
    fi
    echo "$raw" | sed -E 's/^  version: "(.*)"$/\1/'
}

read_readme_version() {
    local raw
    raw=$(grep -E 'Artemis-[0-9]+\.[0-9]+(\.[0-9]+)?\.war' README.md | head -n 1 || true)
    if [[ -z "$raw" ]]; then
        echo "Could not find an 'Artemis-X.Y[.Z].war' reference in README.md." >&2
        exit 1
    fi
    echo "$raw" | sed -E 's/.*Artemis-([0-9]+\.[0-9]+(\.[0-9]+)?)\.war.*/\1/'
}

check_consistency() {
    local gradle_version openapi_version readme_version
    gradle_version=$(read_build_gradle_version)
    openapi_version=$(read_openapi_version)
    readme_version=$(read_readme_version)

    if ! [[ "$gradle_version" =~ $CANONICAL_REGEX ]]; then
        echo "build.gradle version '$gradle_version' is not a canonical Artemis version (X.Y or X.Y.Z with Z>=1)." >&2
        exit 1
    fi

    if [[ "$openapi_version" != "$gradle_version" ]]; then
        echo "Version drift: build.gradle=$gradle_version but openapi/openapi.yaml=$openapi_version" >&2
        exit 1
    fi
    if [[ "$readme_version" != "$gradle_version" ]]; then
        echo "Version drift: build.gradle=$gradle_version but README.md=Artemis-$readme_version.war" >&2
        exit 1
    fi

    echo "Version check passed: $gradle_version"
}

compute_new_version() {
    local current=$1
    local action=$2
    local major minor patch
    IFS='.' read -r major minor patch <<< "$current"

    case "$action" in
        major)
            echo "$((major + 1)).0"
            ;;
        patch)
            echo "$major.$((minor + 1))"
            ;;
        hotfix)
            if [[ -z "${patch:-}" ]]; then
                echo "$major.$minor.1"
            else
                local next=$((patch + 1))
                if [[ "$next" -lt 1 ]]; then
                    echo "Refusing to emit hotfix with patch < 1." >&2
                    exit 1
                fi
                echo "$major.$minor.$next"
            fi
            ;;
        *)
            echo "$USAGE" >&2
            exit 1
            ;;
    esac
}

stamp_file() {
    local file=$1
    local pattern=$2
    local replacement=$3
    if ! grep -qE "$pattern" "$file"; then
        echo "Expected pattern '$pattern' not found in $file; aborting." >&2
        exit 1
    fi
    # macOS / BSD sed: -i '' for in-place edits.
    sed -i '' -E "s|$pattern|$replacement|" "$file"
}

bump_version() {
    local action=$1
    local current new

    current=$(read_build_gradle_version)
    if ! [[ "$current" =~ $CANONICAL_REGEX ]]; then
        echo "Refusing to bump from non-canonical version '$current'." >&2
        exit 1
    fi

    new=$(compute_new_version "$current" "$action")

    if ! [[ "$new" =~ $CANONICAL_REGEX ]]; then
        echo "Computed new version '$new' is not canonical." >&2
        exit 1
    fi

    echo "Updating version from $current to $new..."

    stamp_file build.gradle "^version = \"$current\"$" "version = \"$new\""
    stamp_file openapi/openapi.yaml "^  version: \"$current\"$" "  version: \"$new\""
    stamp_file README.md "Artemis-$current\\.war" "Artemis-$new.war"

    echo "Staging changes for git..."
    git add build.gradle openapi/openapi.yaml README.md

    echo "Creating git commit..."
    git commit -m "Development: Bump version to $new ($action update)"

    echo "Version update and git commit complete. New version: $new"
}

case "$ACTION" in
    --check)
        check_consistency
        ;;
    major|patch|hotfix)
        bump_version "$ACTION"
        ;;
    *)
        echo "$USAGE" >&2
        exit 1
        ;;
esac
