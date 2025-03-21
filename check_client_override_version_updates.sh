#!/bin/bash

# This script checks your package.json overrides and only reports updates when the major or minor version changes.
# Patch-level differences in packages that use a caret (^) prefix are ignored.

PACKAGE_JSON="package.json"

if [ ! -f "$PACKAGE_JSON" ]; then
  echo "package.json not found!"
  exit 1
fi

echo "Checking for updates..." >&2

# Extract the top-level override keys.
OVERRIDES=$(jq -r '.overrides | keys[]' "$PACKAGE_JSON")

check_dep() {
  local DEP_NAME="$1"
  local CUR_VERSION="$2"

  # Get the latest stable version from npm.
  local LATEST_VERSION
  LATEST_VERSION=$(npm show "$DEP_NAME" version 2>/dev/null)
  if [ -z "$LATEST_VERSION" ]; then
    # If no version is found, skip.
    return
  fi

  # If current version uses ^, only report if major or minor changes.
  # For example, ^19.2.0 => ignore 19.2.x updates but show 19.3.x or 20.x.x updates.
  if [[ "$CUR_VERSION" =~ ^\^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    local CUR_MAJOR="${BASH_REMATCH[1]}"
    local CUR_MINOR="${BASH_REMATCH[2]}"
    local LATEST_MAJOR LATEST_MINOR LATEST_PATCH
    LATEST_MAJOR=$(echo "$LATEST_VERSION" | cut -d. -f1)
    LATEST_MINOR=$(echo "$LATEST_VERSION" | cut -d. -f2)
    LATEST_PATCH=$(echo "$LATEST_VERSION" | cut -d. -f3)

    # Only show if major is bigger, or if same major but minor is bigger.
    if (( LATEST_MAJOR > CUR_MAJOR )); then
      echo "$DEP_NAME: Current -> $CUR_VERSION, Latest -> $LATEST_VERSION"
    elif (( LATEST_MAJOR == CUR_MAJOR && LATEST_MINOR > CUR_MINOR )); then
      echo "$DEP_NAME: Current -> $CUR_VERSION, Latest -> $LATEST_VERSION"
    fi
  else
    # If no caret, do a simple direct comparison.
    # This means we show if they differ at all.
    if [[ "$LATEST_VERSION" != "$CUR_VERSION" ]]; then
      echo "$DEP_NAME: Current -> $CUR_VERSION, Latest -> $LATEST_VERSION"
    fi
  fi
}

for PACKAGE in $OVERRIDES; do
  CUR_VALUE=$(jq -r ".overrides[\"$PACKAGE\"]" "$PACKAGE_JSON")

  # If it's an object of subdependencies, handle them individually.
  if [[ "$CUR_VALUE" =~ "{" ]]; then
    SUB_PACKAGES=$(jq -r ".overrides[\"$PACKAGE\"] | keys[]" "$PACKAGE_JSON")
    for SUB_DEP in $SUB_PACKAGES; do
      CUR_SUB_VERSION=$(jq -r ".overrides[\"$PACKAGE\"][\"$SUB_DEP\"]" "$PACKAGE_JSON")
      check_dep "$SUB_DEP" "$CUR_SUB_VERSION"
    done
  else
    # Direct dependency.
    check_dep "$PACKAGE" "$CUR_VALUE"
  fi

done
