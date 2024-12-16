#!/bin/bash

# Determines the changed modules following the module-directory structure.
# Based on git-diff between the local state and an input branch name.
# Returns a comma-separated list of changed modules.
# Example: "./get_changed_modules.sh develop"

# Check for the branch input argument.
if [ $# -eq 0 ]; then
    echo "Usage: $0 <branch_to_compare>"
    exit 1
fi

BRANCH_TO_COMPARE="$1"
if ! git show-ref --verify --quiet "refs/heads/$BRANCH_TO_COMPARE"; then
    echo "Branch $BRANCH_TO_COMPARE does not exist."
    exit 1
fi

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
