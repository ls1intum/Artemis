#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=$(pwd)

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

junit () {
  echo '⚙️ executing junit'
  #empty script action, just for the results
}
final_aeolus_post_action () {
  set +e # from now on, we don't exit on errors
  echo '⚙️ executing final_aeolus_post_action'
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  junit
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
  # just source to use the methods in the subshell, no execution
  return 0
  fi
  local _script_name
  _script_name=$(realpath "${0}")
  trap final_aeolus_post_action EXIT
  bash -c "source ${_script_name} aeolus_sourcing;compile_the_code"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing;run_structural_tests"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing;run_behavior_tests"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
}

main "${@}"
