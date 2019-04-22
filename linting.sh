#!/bin/sh
join_by () {
  local IFS="$1";
  shift;
  echo "$*";
}
PROJECT_DIR=$pwd

FILES=$(join_by "," "$@")

./gradlew spotlessApply -PspotlessFiles=${FILES}
