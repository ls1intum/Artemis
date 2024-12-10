#!/bin/bash

BRANCH_TO_COMPARE="$1"

MODULE_BASE_PATH="src/main/java/de/tum/cit/aet/artemis"
MODULES=$(find "$MODULE_BASE_PATH" -maxdepth 1 -mindepth 1 -type d -exec basename {} \;)
CHANGED_MODULES=()

for MODULE in $MODULES; do
    if git diff "$BRANCH_TO_COMPARE" --name-only | grep -q "^$MODULE_BASE_PATH/$MODULE/"; then
        CHANGED_MODULES+=("$MODULE")
    fi
done

IFS=,
echo "${CHANGED_MODULES[*]}"
