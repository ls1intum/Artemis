#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
structural_tests () {
  echo '⚙️ executing structural_tests'
  chmod +x ./gradlew
  ./gradlew clean structuralTests

}

behavior_tests () {
  echo '⚙️ executing behavior_tests'
  ./gradlew behaviorTests
}

setup_working_directory_for_cleanup () {
  echo '⚙️ executing setup_working_directory_for_cleanup'
  chmod -R 777 .
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
    return 0 # just source to use the methods in the subshell, no execution
  fi
  local _script_name
  _script_name=${BASH_SOURCE[0]:-$0}
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; structural_tests"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; behavior_tests"
}

main "${@}"
