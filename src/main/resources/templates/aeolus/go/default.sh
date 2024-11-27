#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
build () {
  echo '⚙️ executing build'
  cd "${testWorkingDirectory}"
  go test -c -o /dev/null ./...

}

test () {
  echo '⚙️ executing test'
  cd "${testWorkingDirectory}"
  go test ./... -json 2>&1 | go-junit-report -parser gojson -out test-results.xml

}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
    return 0 # just source to use the methods in the subshell, no execution
  fi
  local _script_name
  _script_name=${BASH_SOURCE[0]:-$0}
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; build"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; test"
}

main "${@}"
