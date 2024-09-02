#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
install_dependencies () {
  echo '⚙️ executing install_dependencies'
  echo 'Install dependencies'
}

run_tests () {
  echo '⚙️ executing run_tests'
  echo 'Hello World'
}

process_results () {
  echo '⚙️ executing process_results'
  rm -rf results
  mkdir results
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
    return 0 # just source to use the methods in the subshell, no execution
  fi
  local _script_name
  _script_name=${BASH_SOURCE[0]:-$0}
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; install_dependencies"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; run_tests"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; process_results"
}

main "${@}"
