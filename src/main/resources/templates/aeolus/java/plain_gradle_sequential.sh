#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=$(pwd)

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

final_aeolus_post_action () {
  set +e # from now on, we don't exit on errors
  echo '⚙️ executing final_aeolus_post_action'
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  setup_working_directory_for_cleanup
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
    return 0 # just source to use the methods in the subshell, no execution
  fi
  local _script_name
  _script_name=$(realpath "${0}")
  trap final_aeolus_post_action EXIT
  bash -c "source ${_script_name} aeolus_sourcing;structural_tests"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing;behavior_tests"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
}

main "${@}"
