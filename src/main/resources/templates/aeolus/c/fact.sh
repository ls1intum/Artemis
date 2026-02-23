#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
setup_the_build_environment () {
  echo '⚙️ executing setup_the_build_environment'
  #!/usr/bin/env bash
  # ------------------------------
  # Task Description:
  # Build and run all tests
  # ------------------------------
  # Update ownership to avoid permission issues
  sudo chown artemis_user:artemis_user .
  # Update ownership in ${studentParentWorkingDirectoryName} and test-reports
  sudo chown artemis_user:artemis_user ${studentParentWorkingDirectoryName}/ -R || true
  sudo mkdir test-reports
  sudo chown artemis_user:artemis_user test-reports/ -R || true
}

build_and_run_all_tests () {
  echo '⚙️ executing build_and_run_all_tests'
  #!/usr/bin/env bash
  # ------------------------------
  # Task Description:
  # Build and run all tests
  # ------------------------------

  rm -f ${studentParentWorkingDirectoryName}/GNUmakefile
  rm -f ${studentParentWorkingDirectoryName}/Makefile
  cp -f ${testWorkingDirectory}/Makefile ${studentParentWorkingDirectoryName}/Makefile || exit 2

  # Compile the code
  make -C ${studentParentWorkingDirectoryName}/ exercise
  COMPILATION_EXIT_CODE=$?

  if [ $COMPILATION_EXIT_CODE -ne 0 ]; then
      exit 1
  fi

  cd ${testWorkingDirectory}

  # Run the tests
  python3 Tests.py || true
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
    return 0 # just source to use the methods in the subshell, no execution
  fi
  local _script_name
  _script_name=${BASH_SOURCE[0]:-$0}
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; setup_the_build_environment"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; build_and_run_all_tests"
}

main "${@}"
