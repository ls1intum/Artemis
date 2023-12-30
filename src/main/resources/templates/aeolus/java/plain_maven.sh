#!/usr/bin/env bash
set -e

maven () {
  echo '⚙️ executing maven'
  mvn clean test
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
    return 0 # just source to use the methods in the subshell, no execution
  fi
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  maven
}

main "${@}"
