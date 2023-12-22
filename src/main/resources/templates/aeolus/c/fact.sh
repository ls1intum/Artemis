#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=$(pwd)

setup_the_build_environment () {
  echo '⚙️ executing setup_the_build_environment'
  #!/usr/bin/env bash
  # ------------------------------
  # Task Description:
  # Build and run all tests
  # ------------------------------
  # Updating assignment and test-reports ownership...
  sudo chown artemis_user:artemis_user assignment/ -R || true
  sudo rm -rf test-reports
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
  rm -f assignment/GNUmakefile
  rm -f assignment/Makefile
  cp -f tests/Makefile assignment/Makefile || exit 2
  cd tests
  python3 Tests.py
  rm Tests.py
  rm -rf ./tests || true
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
  # just source to use the methods in the subshell, no execution
  return 0
  fi
  local _script_name
  _script_name=$(realpath "${0}")
  bash -c "source ${_script_name} aeolus_sourcing;setup_the_build_environment"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing;build_and_run_all_tests"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
}

main "${@}"
