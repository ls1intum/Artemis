#!/usr/bin/env bash
set -e

gradle () {
  echo '⚙️ executing gradle'
  chmod +x ./gradlew
  ./gradlew clean test
}

main () {
  gradle
}

main "${@}"
