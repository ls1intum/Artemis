#!/bin/sh
join_by () {
  local IFS="$1";
  shift;
  echo "$*";
}

FILES=$(join_by "," "$@")

./gradlew spotlessApply -PspotlessFiles="${FILES}"
