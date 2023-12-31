#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
tests () {
  echo '⚙️ executing tests'
  chmod +x ./gradlew
./gradlew clean test tiaTests --run-all-tests

}

static_code_analysis () {
  echo '⚙️ executing static_code_analysis'
  ./gradlew check -x test
}

setup_working_directory_for_cleanup () {
  echo '⚙️ executing setup_working_directory_for_cleanup'
  chmod -R 777 .
}

final_aeolus_post_action () {
  set +e # from now on, we don't exit on errors
  echo '⚙️ executing final_aeolus_post_action'
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  static_code_analysis
  setup_working_directory_for_cleanup
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
    return 0 # just source to use the methods in the subshell, no execution
  fi
  local _script_name
  _script_name=${BASH_SOURCE[0]:-$0}
  trap final_aeolus_post_action EXIT

  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; tests"
}

main "${@}"
