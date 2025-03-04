#!/bin/bash

# Script to migrate Artemis API paths in Jenkins job definitions from Artemis versions prior to 8.0 to paths after 8.0.
# Replaces the paths by modifying the job definitions stored in files. This script needs to be executed on your Jenkins (controller) server.
# Usage: ./migrate_api_paths.sh

ROOT_DIR="$JENKINS_HOME/data/jobs"

if [ -z "$ROOT_DIR" ]; then
  echo "Error: JENKINS_HOME environment variable is not set. You can manually override by changing 'ROOT_DIR' in this script."
  exit 1
fi

if ! command -v perl &> /dev/null; then
  echo "Error: Perl is not installed. Please install Perl and try again."
  exit 1
fi

TOTAL_REPLACEMENTS=0
TOTAL_MODIFIED_FILES=0
TOTAL_UNCHANGED_FILES=0
TOTAL_FILES_SCANNED=0

REPLACEMENTS=(
  "/api/public/programming-exercises/new-result|/api/assessment/public/programming-exercises/new-result"
  "/api/public/athena/programming-exercises/|/api/athena/public/programming-exercises/"
  "/api/public/programming-exercises/{exerciseId number}/build-plan|/api/programming/public/programming-exercises/{exerciseId number}/build-plan"
)

while IFS= read -r -d '' file; do
  FILE_MODIFIED=0
  FILE_REPLACEMENTS=0
  ((TOTAL_FILES_SCANNED++))

  for pair in "${REPLACEMENTS[@]}"; do
    SEARCH_STRING="${pair%%|*}"
    REPLACEMENT_STRING="${pair##*|}"

    MATCH_COUNT=$(grep -F -c "$SEARCH_STRING" "$file")

    if [ "$MATCH_COUNT" -gt 0 ]; then
      perl -pi -e "s|$SEARCH_STRING|$REPLACEMENT_STRING|g" "$file"

      FILE_REPLACEMENTS=$((FILE_REPLACEMENTS + MATCH_COUNT))
      FILE_MODIFIED=1
    fi
  done

  if [ "$FILE_MODIFIED" -eq 1 ]; then
      ((TOTAL_MODIFIED_FILES++))
      ((TOTAL_REPLACEMENTS += FILE_REPLACEMENTS))
    else
      ((TOTAL_UNCHANGED_FILES++))
    fi
done < <(find "$ROOT_DIR" -type f -print0)

echo "Total files scanned: $TOTAL_FILES_SCANNED"
echo "Total replacements made: $TOTAL_REPLACEMENTS"
echo "Total files modified: $TOTAL_MODIFIED_FILES"
echo "Total files without replacements: $TOTAL_UNCHANGED_FILES"
