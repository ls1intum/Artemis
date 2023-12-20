#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=$(pwd)

gradle () {
  echo '⚙️ executing gradle'
  chmod +x ./gradlew
  ./gradlew clean test
}

staticcodeanalysis () {
  echo '⚙️ executing static_code_analysis'
  ./gradlew check -x test
}

setupworkingdirectoryforcleanup () {
  echo '⚙️ executing setup_working_directory_for_cleanup'
  mkdir -p /var/tmp/aeolus-results
  shopt -s extglob
  local _sources="**/test-results/test/*.xml"
  local _directory
  _directory=$(dirname "${_sources}")
  mkdir -p /var/tmp/aeolus-results/"${_directory}"
  cp -a "${_sources}" /var/tmp/aeolus-results/**/test-results/test/*.xml
  chmod -R 777 .
  mkdir -p /var/tmp/aeolus-results
  shopt -s extglob
  local _sources="target/spotbugsXml.xml"
  local _directory
  _directory=$(dirname "${_sources}")
  mkdir -p /var/tmp/aeolus-results/"${_directory}"
  cp -a "${_sources}" /var/tmp/aeolus-results/target/spotbugsXml.xml
  local _sources="target/checkstyle-result.xml"
  local _directory
  _directory=$(dirname "${_sources}")
  mkdir -p /var/tmp/aeolus-results/"${_directory}"
  cp -a "${_sources}" /var/tmp/aeolus-results/target/checkstyle-result.xml
  local _sources="target/pmd.xml"
  local _directory
  _directory=$(dirname "${_sources}")
  mkdir -p /var/tmp/aeolus-results/"${_directory}"
  cp -a "${_sources}" /var/tmp/aeolus-results/target/pmd.xml
  local _sources="target/cpd.xml"
  local _directory
  _directory=$(dirname "${_sources}")
  mkdir -p /var/tmp/aeolus-results/"${_directory}"
  cp -a "${_sources}" /var/tmp/aeolus-results/target/cpd.xml
}

final_aeolus_post_action () {
  set +e # from now on, we don't exit on errors
  echo '⚙️ executing final_aeolus_post_action'
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  static_code_analysis "${_current_lifecycle}"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  setup_working_directory_for_cleanup "${_current_lifecycle}"
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
  bash -c "source ${_script_name} aeolus_sourcing;gradle ${_current_lifecycle}"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
}

main "${@}"
