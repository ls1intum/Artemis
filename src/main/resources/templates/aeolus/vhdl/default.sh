#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
provide_environment_information () {
  echo '⚙️ executing provide_environment_information'
  #!/bin/bash
  echo "--------------------Python versions--------------------"
  python3 --version
  pip3 --version

  echo "--------------------Contents of tests repository--------------------"
  ls -la tests
  echo "---------------------------------------------"
  echo "--------------------Contents of assignment repository--------------------"
  ls -la assignment
  echo "---------------------------------------------"

  #Fallback in case Docker does not work as intended
  REQ_FILE=tests/requirements.txt
  if [ -f "$REQ_FILE" ]; then
      pip3 install --user -r tests/requirements.txt || true
  else
      echo "$REQ_FILE does not exist"
  fi
}

prepare_files () {
  echo '⚙️ executing prepare_files'
  rm -f assignment/{GNUmakefile, Makefile, makefile}
  rm -f assignment/*_tb.vhd
  cp -f tests/Makefile assignment/Makefile || exit 2
  cp -f tests/*_tb.vhd assignment/ || exit 2
}

run_and_compile () {
  echo '⚙️ executing run_and_compile'
  cd "tests"
  python3 compileTest.py ../assignment/
  rm compileTest.py
  cp result.xml ../assignment/result.xml
}

junit () {
  echo '⚙️ executing junit'
  #empty script action, just for the results
}

final_aeolus_post_action () {
  set +e # from now on, we don't exit on errors
  echo '⚙️ executing final_aeolus_post_action'
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  junit
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
    return 0 # just source to use the methods in the subshell, no execution
  fi
  local _script_name
  _script_name=${BASH_SOURCE[0]:-$0}
  trap final_aeolus_post_action EXIT

  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; provide_environment_information"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; prepare_files"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; run_and_compile"
}

main "${@}"
