#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=$(pwd)

compilethecode () {
  echo '⚙️ executing compile_the_code'
  python3 -m compileall . -q
}

runstructuraltests () {
  echo '⚙️ executing run_structural_tests'
  pytest structural/* --junitxml=test-reports/structural-results.xml
}

runbehaviortests () {
  echo '⚙️ executing run_behavior_tests'
  pytest behavior/* --junitxml=test-reports/behavior-results.xml
}

junit () {
  echo '⚙️ executing junit'
  mkdir -p /var/tmp/aeolus-results
  shopt -s extglob
  local _sources="test-reports/*results.xml"
  local _directory
  _directory=$(dirname "${_sources}")
  mkdir -p /var/tmp/aeolus-results/"${_directory}"
  cp -a "${_sources}" /var/tmp/aeolus-results/test-reports/*results.xml
  #empty script action, just for the results
}

final_aeolus_post_action () {
  set +e # from now on, we don't exit on errors
  echo '⚙️ executing final_aeolus_post_action'
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  junit "${_current_lifecycle}"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
}

main () {
  local _current_lifecycle="${1}"
    if [[ "${_current_lifecycle}" == "aeolus_sourcing" ]]; then
    # just source to use the methods in the subshell, no execution
    return 0
  fi
  local _script_name
  _script_name=$(realpath "${0}")
  trap final_aeolus_post_action EXIT
  bash -c "source ${_script_name} aeolus_sourcing;compilethecode ${_current_lifecycle}"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing;runstructuraltests ${_current_lifecycle}"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing;runbehaviortests ${_current_lifecycle}"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
}

main "${@}"
