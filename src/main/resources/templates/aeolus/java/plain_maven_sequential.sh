#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=$(pwd)

structural () {
  echo '⚙️ executing structural'
  cd "structural"
  mvn clean test
}

behavior () {
  echo '⚙️ executing behavior'
  cd "behavior"
  mvn clean test
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
  # just source to use the methods in the subshell, no execution
  return 0
  fi
  local _script_name
  _script_name=$(realpath "${0}")
  bash -c "source ${_script_name} aeolus_sourcing;structural"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing;behavior"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
}

main "${@}"
