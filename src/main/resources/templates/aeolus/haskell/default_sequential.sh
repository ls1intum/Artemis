#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
run_structural_tests () {
  echo '⚙️ executing run_structural_tests'
  # the build process is specified in `run.sh` in the test repository
# -s enables the safe testing mode
chmod +x run.sh
./run.sh -s

}

run_behavior_tests () {
  echo '⚙️ executing run_behavior_tests'
  # the build process is specified in `run.sh` in the test repository
# -s enables the safe testing mode
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
    return 0 # just source to use the methods in the subshell, no execution
  fi
  local _script_name
  _script_name=${BASH_SOURCE[0]:-$0}
  trap final_aeolus_post_action EXIT

  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; run_structural_tests"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; run_behavior_tests"
}

main "${@}"
