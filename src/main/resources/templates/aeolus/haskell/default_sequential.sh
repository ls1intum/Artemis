#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
build_the_code () {
  echo '⚙️ executing build_the_code'
  # the build process is specified in `run.sh` in the test repository
  # -s enables the safe testing mode
  chmod +x run.sh

  # Compile the code - run.sh exits with 1 on compilation failure
  ./run.sh -s
  COMPILATION_EXIT_CODE=$?

  if [ $COMPILATION_EXIT_CODE -ne 0 ]; then
      exit 1
  fi
}

run_structural_tests () {
  echo '⚙️ executing run_structural_tests'
  chmod +x run.sh
  ./run.sh -s || true
}

run_behavior_tests () {
  echo '⚙️ executing run_behavior_tests'
  chmod +x run.sh

  # Run the behavioral tests
  ./run.sh -s || true
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
    return 0 # just source to use the methods in the subshell, no execution
  fi
  local _script_name
  _script_name=${BASH_SOURCE[0]:-$0}
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; build_the_code"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; run_structural_tests"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; run_behavior_tests"
}

main "${@}"
