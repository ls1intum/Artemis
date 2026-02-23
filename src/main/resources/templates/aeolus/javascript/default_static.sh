#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
install_dependencies () {
  echo '⚙️ executing install_dependencies'
  npm ci --prefer-offline --no-audit
}

syntax_check () {
  echo '⚙️ executing syntax_check'
  # Check for syntax errors in the student code
  find "${studentParentWorkingDirectoryName}" -name "*.js" -not -path "*/node_modules/*" -exec node --check {} +
  COMPILATION_EXIT_CODE=$?

  if [ $COMPILATION_EXIT_CODE -ne 0 ]; then
      exit 1
  fi
}

static_code_analysis () {
  echo '⚙️ executing static_code_analysis'
  npm run lint:ci || [ $? -eq 1 ]
}

test () {
  echo '⚙️ executing test'
  # Run the tests
  npm run test:ci || true
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
  bash -c "source ${_script_name} aeolus_sourcing; syntax_check"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; static_code_analysis"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; test"
}

main "${@}"
