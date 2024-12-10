#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
static_code_analysis () {
  echo '⚙️ executing static_code_analysis'
  ruff check --config=ruff-student.toml --output-format=sarif --output-file=ruff.sarif --exit-zero "${studentParentWorkingDirectoryName}"
}

build_and_test_the_code () {
  echo '⚙️ executing build_and_test_the_code'
  python3 -m compileall . -q || error=true
  if [ ! $error ]
  then
      pytest --junitxml=test-reports/results.xml
  fi
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
    return 0 # just source to use the methods in the subshell, no execution
  fi
  local _script_name
  _script_name=${BASH_SOURCE[0]:-$0}
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; static_code_analysis"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; build_and_test_the_code"
}

main "${@}"
