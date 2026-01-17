#!/bin/bash

# Determines which e2e test folders are relevant based on changed source code folders.
# Returns a space-separated list of relevant e2e test folder paths.
# Example: "./get-relevant-tests.sh origin/develop"

# Check for the branch input argument.
if [ $# -eq 0 ]; then
    echo "Usage: $0 <branch_to_compare>"
    exit 1
fi

BRANCH_TO_COMPARE="$1"

# Base paths
JAVA_BASE_PATH="src/main/java/de/tum/cit/aet/artemis"
WEBAPP_BASE_PATH="src/main/webapp/app"
E2E_BASE_PATH="src/test/playwright/e2e"

# Get all changed files
CHANGED_FILES=$(git diff "$BRANCH_TO_COMPARE" --name-only)

if [ -z "$CHANGED_FILES" ]; then
    echo ""
    exit 0
fi

# Set to store unique test folders
declare -A RELEVANT_TESTS

# Function to add test folder if it exists
add_test_folder() {
    local test_folder="$1"
    if [ -d "$test_folder" ]; then
        RELEVANT_TESTS["$test_folder"]=1
    fi
}

# Check each changed file
while IFS= read -r file; do
    # Skip empty lines
    if [ -z "$file" ]; then
        continue
    fi

    # Check Java modules
    if [[ "$file" == "$JAVA_BASE_PATH"/* ]]; then
        # Extract module name (e.g., "exercise", "exam", "course")
        MODULE=$(echo "$file" | sed "s|^$JAVA_BASE_PATH/||" | cut -d'/' -f1)
        
        # Map Java modules to e2e test folders
        case "$MODULE" in
            exercise)
                add_test_folder "$E2E_BASE_PATH/exercise"
                ;;
            exam)
                add_test_folder "$E2E_BASE_PATH/exam"
                ;;
            lecture)
                add_test_folder "$E2E_BASE_PATH/lecture"
                ;;
            atlas)
                add_test_folder "$E2E_BASE_PATH/atlas"
                ;;
            communication)
                add_test_folder "$E2E_BASE_PATH/course"  # Course messages are in course folder
                ;;
            *)
                # For other modules, check if there's a matching e2e folder
                if [ -d "$E2E_BASE_PATH/$MODULE" ]; then
                    add_test_folder "$E2E_BASE_PATH/$MODULE"
                fi
                ;;
        esac
    fi

    # Check Angular/webapp modules
    if [[ "$file" == "$WEBAPP_BASE_PATH"/* ]]; then
        # Extract module name (e.g., "exercise", "exam", "course")
        MODULE=$(echo "$file" | sed "s|^$WEBAPP_BASE_PATH/||" | cut -d'/' -f1)
        
        # Map Angular modules to e2e test folders
        case "$MODULE" in
            exercise)
                add_test_folder "$E2E_BASE_PATH/exercise"
                ;;
            exam)
                add_test_folder "$E2E_BASE_PATH/exam"
                ;;
            lecture)
                add_test_folder "$E2E_BASE_PATH/lecture"
                ;;
            atlas)
                add_test_folder "$E2E_BASE_PATH/atlas"
                ;;
            communication)
                add_test_folder "$E2E_BASE_PATH/course"  # Course messages are in course folder
                ;;
            course)
                add_test_folder "$E2E_BASE_PATH/course"
                ;;
            *)
                # For other modules, check if there's a matching e2e folder
                if [ -d "$E2E_BASE_PATH/$MODULE" ]; then
                    add_test_folder "$E2E_BASE_PATH/$MODULE"
                fi
                ;;
        esac
    fi

    # Check for changes in core/shared areas that might affect all tests
    if [[ "$file" == "$JAVA_BASE_PATH/core"/* ]] || \
       [[ "$file" == "$JAVA_BASE_PATH/config"/* ]] || \
       [[ "$file" == "$WEBAPP_BASE_PATH/core"/* ]] || \
       [[ "$file" == "$WEBAPP_BASE_PATH/shared"/* ]] || \
       [[ "$file" == "src/main/webapp/app/app."* ]] || \
       [[ "$file" == "src/main/webapp/app/app.routes.ts" ]] || \
       [[ "$file" == "src/main/webapp/app/app.config.ts" ]]; then
        # Core changes affect everything - return empty to run all tests
        echo ""
        exit 0
    fi

    # Check for changes in test infrastructure
    if [[ "$file" == "src/test/playwright/"* ]] || \
       [[ "$file" == "docker/"* ]] || \
       [[ "$file" == ".ci/E2E-tests/"* ]] || \
       [[ "$file" == ".github/workflows/"* ]]; then
        # Test infrastructure changes - return empty to run all tests
        echo ""
        exit 0
    fi

done <<< "$CHANGED_FILES"

# Output unique test folders
if [ ${#RELEVANT_TESTS[@]} -eq 0 ]; then
    echo ""
else
    printf '%s\n' "${!RELEVANT_TESTS[@]}" | sort | tr '\n' ' '
fi
