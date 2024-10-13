#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
build_and_test_the_code () {
  echo '⚙️ executing build_and_test_the_code'
  cp -R Sources ${studentParentWorkingDirectoryName}
  # copy test files
  cp -R Tests ${studentParentWorkingDirectoryName}
  cp Package.swift ${studentParentWorkingDirectoryName}

  # In order to get the correct console output we need to execute the command within the assignment directory
  # swift build
  cd ${studentParentWorkingDirectoryName}
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
  cp .swiftlint.yml ${studentParentWorkingDirectoryName} || true
  # create target directory for SCA Parser
  mkdir target
  cd ${studentParentWorkingDirectoryName}
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

  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; build_and_test_the_code"
}

main "${@}"
