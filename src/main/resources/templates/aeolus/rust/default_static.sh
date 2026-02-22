#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
static_code_analysis () {
  echo '⚙️ executing static_code_analysis'
  cd assignment
  # clippy-sarif creates a result object for every span, but we want one result per message.
  # Select the first primary span and replace macro expansions with their original invocation.
  cargo clippy --message-format=json | jq -c '.message.spans[]? |= first(select(.is_primary) | if .expansion then .expansion.span else . end)' | clippy-sarif --output ../clippy.sarif
}

build () {
  echo '⚙️ executing build'
  # Compile the code
  cargo build --tests --profile test
  COMPILATION_EXIT_CODE=$?

  if [ $COMPILATION_EXIT_CODE -ne 0 ]; then
      exit 1
  fi
}

run_all_tests () {
  echo '⚙️ executing run_all_tests'
  # Run the tests
  cargo nextest run --profile ci || true
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
  bash -c "source ${_script_name} aeolus_sourcing; build"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; run_all_tests"
}

main "${@}"
