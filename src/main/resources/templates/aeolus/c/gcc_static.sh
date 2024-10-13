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

  # Updating ${studentParentWorkingDirectoryName} and test-reports ownership...
  sudo chown artemis_user:artemis_user ${studentParentWorkingDirectoryName}/ -R
  mkdir test-reports
  chown artemis_user:artemis_user test-reports/ -R

  # assignment
  cd ${testWorkingDirectory}
  REQ_FILE=requirements.txt
  if [ -f "$REQ_FILE" ]; then
      pip3 install --user -r requirements.txt || true
  else
      echo "$REQ_FILE does not exist"
  fi
  cd ..
}

setup_makefile () {
  echo '⚙️ executing setup_makefile'
  #!/usr/bin/env bash

  # ------------------------------
  # Task Description:
  # Setup makefile
  # ------------------------------

  shadowFilePath="../${testWorkingDirectory}/testUtils/c/shadow_exec.c"

  foundIncludeDirs=`grep -m 1 'INCLUDEDIRS\s*=' ${studentParentWorkingDirectoryName}/Makefile`

  foundSource=`grep -m 1 'SOURCE\s*=' ${studentParentWorkingDirectoryName}/Makefile`
  foundSource="$foundSource $shadowFilePath"

  rm -f ${studentParentWorkingDirectoryName}/GNUmakefile
  rm -f ${studentParentWorkingDirectoryName}/makefile

  cp -f ${testWorkingDirectory}/Makefile ${studentParentWorkingDirectoryName}/Makefile || exit 2
  sed -i "s~\bINCLUDEDIRS\s*=.*~${foundIncludeDirs}~; s~\bSOURCE\s*=.*~${foundSource}~" ${studentParentWorkingDirectoryName}/Makefile
}

build_and_run_all_tests () {
  echo '⚙️ executing build_and_run_all_tests'
  #!/usr/bin/env bash

  # ------------------------------
  # Task Description:
  # Build and run all tests if the compilation succeeds
  # ------------------------------
  sudo chown artemis_user:artemis_user .
  gcc -c -Wall ${studentParentWorkingDirectoryName}/*.c || error=true
  if [ ! $error ]
  then
      cd ${testWorkingDirectory} || exit 0
      python3 Tests.py || true
  else
      exit 1
  fi
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
  bash -c "source ${_script_name} aeolus_sourcing; setup_makefile"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; build_and_run_all_tests"
}

main "${@}"
