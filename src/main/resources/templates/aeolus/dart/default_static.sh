#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
install_student_dependencies () {
  echo '⚙️ executing install_student_dependencies'
  cd "${studentParentWorkingDirectoryName}"
  dart pub get
}

install_dependencies () {
  echo '⚙️ executing install_dependencies'
  cd "${testWorkingDirectory}"
  dart pub get
}

static_code_analysis () {
  echo '⚙️ executing static_code_analysis'
  dart analyze --format=json "${studentParentWorkingDirectoryName}" | analyze_sarif --srcroot "${studentParentWorkingDirectoryName}" --output 'dart_analyze.sarif'
}

test () {
  echo '⚙️ executing test'
  cd "${testWorkingDirectory}"
  # Run the tests
  dart test --reporter=json | tojunit | xmlstarlet ed -d '//failure/@message' -d '//error/@message' > report.xml || true
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
    return 0 # just source to use the methods in the subshell, no execution
  fi
  local _script_name
  _script_name=${BASH_SOURCE[0]:-$0}
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; install_student_dependencies"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; install_dependencies"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; static_code_analysis"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; test"
}

main "${@}"
