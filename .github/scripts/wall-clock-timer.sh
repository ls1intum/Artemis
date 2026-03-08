#!/usr/bin/env bash
# Record a start time or compute elapsed wall-clock duration.
# Usage: wall-clock-timer.sh start
#        wall-clock-timer.sh stop <epoch-start>

MODE="$1"

if [ "$MODE" = "start" ]; then
  echo "start=$(date +%s)" >> "$GITHUB_OUTPUT"

elif [ "$MODE" = "stop" ]; then
  START="$2"
  END=$(date +%s)
  ELAPSED=$((END - START))
  HOURS=$((ELAPSED / 3600))
  MINS=$(( (ELAPSED % 3600) / 60 ))
  SECS=$((ELAPSED % 60))
  if [ "$HOURS" -gt 0 ]; then
    echo "duration=${HOURS}h ${MINS}m ${SECS}s" >> "$GITHUB_OUTPUT"
  elif [ "$MINS" -gt 0 ]; then
    echo "duration=${MINS}m ${SECS}s" >> "$GITHUB_OUTPUT"
  else
    echo "duration=${SECS}s" >> "$GITHUB_OUTPUT"
  fi
fi
