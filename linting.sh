#!/bin/sh
join_by () {
  local IFS="$1";
  shift;
  echo "$*";
}

FILES=$(join_by "," "$@")

if [[ "$OSTYPE" == "msys" ]]; then
  # replace backslashes with double backslashes in Windows file paths when
  # using MinGW (msys = lightweight shell and GNU utilities compiled for Windows (part of MinGW)
  FILES=$(echo $FILES | sed 's/\\/\\\\/g')
fi

./gradlew spotlessApply -PspotlessFiles="${FILES}"
