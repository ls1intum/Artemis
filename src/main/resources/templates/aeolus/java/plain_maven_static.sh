#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
maven () {
  echo '⚙️ executing maven'
  mvn clean test
}

static_analysis () {
  echo '⚙️ executing static_analysis'
  mvn spotbugs:spotbugs checkstyle:checkstyle pmd:pmd pmd:cpd
}

final_aeolus_post_action () {
  set +e # from now on, we don't exit on errors
  echo '⚙️ executing final_aeolus_post_action'
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  static_analysis
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
    return 0 # just source to use the methods in the subshell, no execution
  fi
  local _script_name
  _script_name=${BASH_SOURCE[0]:-$0}
  trap final_aeolus_post_action EXIT

  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; maven"
}

main "${@}"
