#!/bin/bash

# Directory to search for Java files
SEARCH_DIR="$1"

# Check if directory is provided
if [ -z "$SEARCH_DIR" ]; then
  echo "Usage: $0 [path_to_directory]"
  exit 1
fi

# Find all Java files, extract class and record declarations, and identify duplicates
find "$SEARCH_DIR" -type f -name "*.java" -print0 | 
xargs -0 grep -Eho 'class\s+\w+|record\s+\w+' | 
sed -E 's/(class|record)\s+//g' | 
sort | 
uniq -cd | 
grep -E '[0-9]+\s+' 

# Explanation of flags and commands:
# - 'grep -Eho' extracts matching patterns with extended regex, only the matching part (-o).
# - 'sed' is used to remove the 'class' or 'record' prefix, isolating the names.
# - 'sort' and 'uniq -cd' are used to count duplicates (lines appearing more than once).
# - 'grep -E' filters to show only the lines where the count is greater than 1.


