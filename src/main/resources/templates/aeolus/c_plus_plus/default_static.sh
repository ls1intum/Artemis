#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
setup_the_build_environment () {
  echo '⚙️ executing setup_the_build_environment'
  #!/usr/bin/env bash

  # ------------------------------
  # Task Description:
  # Setup the build environment
  # ------------------------------

  mkdir test-reports

  # Update ownership to avoid permission issues
  chown -R artemis_user:artemis_user .

  REQ_FILE=requirements.txt
  if [ -f "$REQ_FILE" ]; then
      python3 -m venv /venv
      /venv/bin/pip3 install -r "$REQ_FILE"
  else
      echo "$REQ_FILE does not exist"
  fi
}

build_and_run_all_tests () {
  echo '⚙️ executing build_and_run_all_tests'
  #!/usr/bin/env bash

  # ------------------------------
  # Task Description:
  # Build and run all tests
  # ------------------------------

  if [ -d /venv ]; then
      . /venv/bin/activate
  fi

  # Compile the code
  g++ -c -Wall ${studentParentWorkingDirectoryName}/*.cpp 2>&1
  COMPILATION_EXIT_CODE=$?

  if [ $COMPILATION_EXIT_CODE -ne 0 ]; then
      exit 1
  fi

  # Run tests as unprivileged user
  runuser -u artemis_user python3 Tests.py || true
}

static_code_analysis () {
  echo '⚙️ executing static_code_analysis'
  #!/usr/bin/env bash

  # ------------------------------
  # Task Description:
  # Check the student code for common issues
  # ------------------------------

  ln -s build/compile_commands.json .
  clang-tidy "${studentParentWorkingDirectoryName}"/**/*.{c,h,cpp,hpp} | clang-tidy-sarif --output test-reports/clang-tidy.sarif
}

final_aeolus_post_action () {
  set +e # from now on, we don't exit on errors
  echo '⚙️ executing final_aeolus_post_action'
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  static_code_analysis
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
    return 0 # just source to use the methods in the subshell, no execution
  fi
  local _script_name
  _script_name=${BASH_SOURCE[0]:-$0}
  trap final_aeolus_post_action EXIT

  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; setup_the_build_environment"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; build_and_run_all_tests"
}

main "${@}"
