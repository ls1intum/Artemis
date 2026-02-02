#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
build_and_test_the_code () {
  echo '⚙️ executing build_and_test_the_code'
  # Copy source files, test files, and package manifest to student directory
  cp -R Sources ${studentParentWorkingDirectoryName}
  cp -R Tests ${studentParentWorkingDirectoryName}
  cp Package.swift ${studentParentWorkingDirectoryName}

  cd ${studentParentWorkingDirectoryName}

  # Build the project
  if swift build; then
      # Run tests with native xUnit output
      # Note: --parallel is required due to Swift 6 bug where --xunit-output
      # does not work correctly without it
      swift test --parallel --xunit-output tests.xml || true
      # Swift Testing writes results to tests-swift-testing.xml, copy to expected location
      if [ -f tests-swift-testing.xml ]; then
          cp tests-swift-testing.xml tests.xml
      fi
  fi

  # Ensure files created by Docker (running as root) are accessible
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
