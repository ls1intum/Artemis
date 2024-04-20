#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
compile_the_code () {
  echo '⚙️ executing compile_the_code'
  python3 -m compileall . -q

}

run_structural_tests () {
  echo '⚙️ executing run_structural_tests'
  pytest structural/* --junitxml=test-reports/structural-results.xml
}

run_behavior_tests () {
  echo '⚙️ executing run_behavior_tests'
  pytest behavior/* --junitxml=test-reports/behavior-results.xml
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
    return 0 # just source to use the methods in the subshell, no execution
  fi
  local _script_name
  _script_name=${BASH_SOURCE[0]:-$0}
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; compile_the_code"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; run_structural_tests"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; run_behavior_tests"
}

main "${@}"
