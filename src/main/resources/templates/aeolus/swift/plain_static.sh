#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=$(pwd)

buildandtestthecode () {
  echo '⚙️ executing build_and_test_the_code'
  # Delete possible old Sources and replace with student's assignment Sources
  rm -rf Sources
  mv assignment/Sources .
  # Delete and create the assignment directory from scratch
  mv assignment/.git/refs/heads assignment_git_heads # localci workaround
  rm -rf assignment
  mkdir assignment
  mkdir -p assignment/.git/refs # localci workaround
  mv assignment_git_heads/ assignment/.git/refs/heads/ # localci workaround
  cp -R Sources assignment
  # copy test files
  cp -R Tests assignment
  cp Package.swift assignment
  # In order to get the correct console output we need to execute the command within the assignment directory
  # swift build
  cd assignment
  swift build || error=true
  if [ ! $error ]
  then
      # swift test
      swift test || true
  fi
  # The used docker container is calling 'swift build' which creates files as root (e.g. tests.xml),
  # so we need to allow everyone to access these files
  cd ..
  chmod -R 777 .
}

runstaticcodeanalysis () {
  echo '⚙️ executing run_static_code_analysis'
  mkdir -p /var/tmp/aeolus-results
  shopt -s extglob
  local _sources="assignment/tests.xml"
  local _directory
  _directory=$(dirname "${_sources}")
  mkdir -p /var/tmp/aeolus-results/"${_directory}"
  cp -a "${_sources}" /var/tmp/aeolus-results/assignment/tests.xml
  # Copy SwiftLint rules
  cp .swiftlint.yml assignment || true
  # create target directory for SCA Parser
  mkdir target
  cd assignment
  # Execute static code analysis
  swiftlint > ../target/swiftlint-result.xml
  mkdir -p /var/tmp/aeolus-results
  shopt -s extglob
  local _sources="target/swiftlint-result.xml"
  local _directory
  _directory=$(dirname "${_sources}")
  mkdir -p /var/tmp/aeolus-results/"${_directory}"
  cp -a "${_sources}" /var/tmp/aeolus-results/target/swiftlint-result.xml
}

final_aeolus_post_action () {
  set +e # from now on, we don't exit on errors
  echo '⚙️ executing final_aeolus_post_action'
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  run_static_code_analysis "${_current_lifecycle}"
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
  bash -c "source ${_script_name} aeolus_sourcing;buildandtestthecode ${_current_lifecycle}"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
}

main "${@}"
