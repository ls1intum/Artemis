#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=$(pwd)

build_and_test_the_code () {
  echo '⚙️ executing build_and_test_the_code'
  python3 -m compileall . -q || error=true
  if [ ! $error ]
  then
      pytest --junitxml=test-reports/results.xml
  fi
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
  _script_name=$(realpath "${0}")
  trap final_aeolus_post_action EXIT
  bash -c "source ${_script_name} aeolus_sourcing;build_and_test_the_code"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
}

main "${@}"
