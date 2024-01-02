#!/usr/bin/env bash
set -e

gradle () {
  echo '⚙️ executing gradle'
  chmod +x ./gradlew
  ./gradlew clean test

}

setup_working_directory_for_cleanup () {
  echo '⚙️ executing setup_working_directory_for_cleanup'
  chmod -R 777 .
}

main () {
  gradle
}

main "${@}"
