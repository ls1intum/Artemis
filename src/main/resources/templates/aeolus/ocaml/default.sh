#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=$(pwd)

build_and_test_the_code () {
  echo '⚙️ executing build_and_test_the_code'
  cd "tests"
  # the build process is specified in `run.sh` in the test repository
  chmod +x run.sh
  ./run.sh -s
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
  # just source to use the methods in the subshell, no execution
  return 0
  fi
  local _script_name
  _script_name=$(realpath "${0}")
  trap final_aeolus_post_action EXIT
  bash -c "source ${_script_name} aeolus_sourcing;build_and_test_the_code"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
}

main "${@}"
