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
  if [[ "${1}" == "aeolus_sourcing" ]]; then
    return 0 # just source to use the methods in the subshell, no execution
  fi
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  gradle
}

main "${@}"
