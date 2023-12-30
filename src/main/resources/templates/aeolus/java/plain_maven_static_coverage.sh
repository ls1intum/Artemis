#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=$(pwd)

maven () {
  echo '⚙️ executing maven'
  mvn clean test -Pcoverage
}

move_report_file () {
  echo '⚙️ executing move_report_file'
  mv target/tia/reports/*/testwise-coverage-*.json target/tia/reports/tiaTests.json
}

maven_1 () {
  echo '⚙️ executing maven'
  mvn spotbugs:spotbugs checkstyle:checkstyle pmd:pmd pmd:cpd
}

final_aeolus_post_action () {
  set +e # from now on, we don't exit on errors
  echo '⚙️ executing final_aeolus_post_action'
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  maven_1
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
    return 0 # just source to use the methods in the subshell, no execution
  fi
  local _script_name
  _script_name=${BASH_SOURCE[0]:-$0}
  trap final_aeolus_post_action EXIT
  bash -c "source ${_script_name} aeolus_sourcing;maven"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing;move_report_file"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
}

main "${@}"
