#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=$(pwd)

setupthebuildenvironment () {
  echo '⚙️ executing setup_the_build_environment'
  #!/usr/bin/env bash
  # ------------------------------
  # Task Description:
  # Build and run all tests
  # ------------------------------
  # Updating assignment and test-reports ownership...
  sudo chown artemis_user:artemis_user assignment/ -R
  sudo rm -rf test-reports
  mkdir test-reports
  chown artemis_user:artemis_user test-reports/ -R
  # assignment
  cd tests
  REQ_FILE=requirements.txt
  if [ -f "$REQ_FILE" ]; then
      pip3 install --user -r requirements.txt || true
  else
      echo "$REQ_FILE does not exist"
  fi
  cd ..
}

setupmakefile () {
  echo '⚙️ executing setup_makefile'
  #!/usr/bin/env bash
  # ------------------------------
  # Task Description:
  # Setup makefile
  # ------------------------------
  shadowFilePath="../tests/testUtils/c/shadow_exec.c"
  foundIncludeDirs=`grep -m 1 'INCLUDEDIRS\s*=' assignment/Makefile`
  foundSource=`grep -m 1 'SOURCE\s*=' assignment/Makefile`
  foundSource="$foundSource $shadowFilePath"
  rm -f assignment/GNUmakefile
  rm -f assignment/makefile
  cp -f tests/Makefile assignment/Makefile || exit 2
  sed -i "s~\bINCLUDEDIRS\s*=.*~${foundIncludeDirs}~; s~\bSOURCE\s*=.*~${foundSource}~" assignment/Makefile
}

buildandrunalltests () {
  echo '⚙️ executing build_and_run_all_tests'
  #!/usr/bin/env bash
  # ------------------------------
  # Task Description:
  # Build and run all tests if the compilation succeeds
  # ------------------------------
  sudo chown artemis_user:artemis_user .
  gcc -c -Wall assignment/*.c || error=true
  if [ ! $error ]
  then
      cd tests || exit 0
      python3 Tests.py || true
  fi
}

cleanup () {
  echo '⚙️ executing cleanup'
  mkdir -p /var/tmp/aeolus-results
  shopt -s extglob
  local _sources="test-reports/tests-results.xml"
  local _directory
  _directory=$(dirname "${_sources}")
  mkdir -p /var/tmp/aeolus-results/"${_directory}"
  cp -a "${_sources}" /var/tmp/aeolus-results/test-reports/tests-results.xml
  sudo rm -rf tests/ assignment/ test-reports/ || true
  chmod -R 777 .
}

# always steps
final_aeolus_post_action () {
  set +e # from now on, we don't exit on errors
  echo '⚙️ executing final_aeolus_post_action'
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  cleanup "${_current_lifecycle}"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
}

main () {
  local _current_lifecycle="${1}"
    if [[ "${_current_lifecycle}" == "aeolus_sourcing" ]]; then
    # just source to use the methods in the subshell, no execution
    return 0
  fi
  local _script_name
  _script_name=$(realpath "${0}")
  trap final_aeolus_post_action EXIT
  bash -c "source ${_script_name} aeolus_sourcing;setupthebuildenvironment ${_current_lifecycle}"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing;setupmakefile ${_current_lifecycle}"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing;buildandrunalltests ${_current_lifecycle}"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
}

main "${@}"
