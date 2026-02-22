#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
set_permissions () {
  echo '⚙️ executing set_permissions'
  find "${studentParentWorkingDirectoryName}" -type f -exec chmod +x "{}" +
}

create_results_directory () {
  echo '⚙️ executing create_results_directory'
  mkdir results
}

test () {
  echo '⚙️ executing test'
  # Run the tests
  bats --report-formatter junit --output results "${testWorkingDirectory}" || true
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
    return 0 # just source to use the methods in the subshell, no execution
  fi
  local _script_name
  _script_name=${BASH_SOURCE[0]:-$0}
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; set_permissions"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; create_results_directory"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; test"
}

main "${@}"
