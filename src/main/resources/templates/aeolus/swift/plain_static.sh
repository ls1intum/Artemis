#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=$(pwd)

build_and_test_the_code () {
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

run_static_code_analysis () {
  echo '⚙️ executing run_static_code_analysis'
  # Copy SwiftLint rules
  cp .swiftlint.yml assignment || true
  # create target directory for SCA Parser
  mkdir target
  cd assignment
  # Execute static code analysis
  swiftlint > ../target/swiftlint-result.xml
}

final_aeolus_post_action () {
  set +e # from now on, we don't exit on errors
  echo '⚙️ executing final_aeolus_post_action'
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  run_static_code_analysis
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
    return 0 # just source to use the methods in the subshell, no execution
  fi
  local _script_name
  _script_name=${BASH_SOURCE[0]:-$0}
  trap final_aeolus_post_action EXIT
  bash -c "source ${_script_name} aeolus_sourcing;build_and_test_the_code"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
}

main "${@}"
