#!/bin/bash

# Script to migrate Artemis API paths in Jenkins job definitions from Artemis versions prior to 8.0 to paths after 8.0.
# Replaces the paths by modifying the job definitions stored in files. This script needs to be executed on your Jenkins (controller) server.
# Usage: ./migrate_api_paths.sh

ROOT_DIR="$JENKINS_HOME/data/jobs"

if [ -z "$JENKINS_HOME" ]; then
  echo "Error: JENKINS_HOME environment variable is not set. You can manually override by changing 'ROOT_DIR' in this script."
  exit 1
fi

if ! command -v perl &> /dev/null; then
  echo "Error: Perl is not installed. Please install Perl and try again."
  exit 1
fi

TOTAL_MODIFIED_FILES=0
TOTAL_UNCHANGED_FILES=0
TOTAL_FILES_SCANNED=0

REPLACEMENTS=(
  "/api/public/programming-exercises/new-result|/api/assessment/public/programming-exercises/new-result"
  "/api/public/athena/programming-exercises/|/api/athena/public/programming-exercises/"
  "/api/public/programming-exercises/([0-9]+)/build-plan|/api/programming/public/programming-exercises/\1/build-plan"
)

while IFS= read -r -d '' file; do
  ((TOTAL_FILES_SCANNED++))
  ORIGINAL_CONTENT=$(cat "$file")

  for pair in "${REPLACEMENTS[@]}"; do
    SEARCH_STRING="${pair%%|*}"
    REPLACEMENT_STRING="${pair##*|}"
    perl -pi -e "s|$SEARCH_STRING|$REPLACEMENT_STRING|g" "$file"
  done

  # Check if file was modified
  if ! cmp -s <(echo "$ORIGINAL_CONTENT") "$file"; then
    ((TOTAL_MODIFIED_FILES++))
  else
    ((TOTAL_UNCHANGED_FILES++))
  fi


  if [ $((TOTAL_FILES_SCANNED % 50)) -eq 0 ]; then
    echo "Progress - Files scanned: $TOTAL_FILES_SCANNED"
  fi
done < <(find "$ROOT_DIR" -type f -print0)

echo "Total files scanned: $TOTAL_FILES_SCANNED"
echo "Total files modified: $TOTAL_MODIFIED_FILES"
echo "Total files without replacements: $TOTAL_UNCHANGED_FILES"
