#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=$(pwd)

gradle () {
  echo '⚙️ executing gradle'
  chmod +x ./gradlew
  ./gradlew clean test
}

static_code_analysis () {
  echo '⚙️ executing static_code_analysis'
  ./gradlew check -x test
}

final_aeolus_post_action () {
  set +e # from now on, we don't exit on errors
  echo '⚙️ executing final_aeolus_post_action'
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  static_code_analysis
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  setup_working_directory_for_cleanup
  cd "${AEOLUS_INITIAL_DIRECTORY}"
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
  # just source to use the methods in the subshell, no execution
  return 0
  fi
  local _script_name
  _script_name=$(realpath "${0}")
  trap final_aeolus_post_action EXIT
  bash -c "source ${_script_name} aeolus_sourcing;gradle"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
}

main "${@}"
