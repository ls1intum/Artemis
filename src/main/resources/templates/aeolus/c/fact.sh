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
  # Updating assignment and test-reports ownership...
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
  cd ${testWorkingDirectory}
  python3 Tests.py
  rm Tests.py
  rm -rf ./${testWorkingDirectory} || true
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
