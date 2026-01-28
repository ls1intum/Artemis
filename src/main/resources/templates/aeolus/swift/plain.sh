#!/usr/bin/env bash
set -e

build_and_test_the_code () {
  echo '⚙️ executing build_and_test_the_code'
  # Copy test files and package manifest to student directory
  cp -R Tests ${studentParentWorkingDirectoryName}
  cp Package.swift ${studentParentWorkingDirectoryName}

  cd ${studentParentWorkingDirectoryName}

  # Build the project
  if swift build; then
      # Run tests with native xUnit output
      # Note: --parallel is required due to Swift 6 bug where --xunit-output
      # does not work correctly without it
      swift test --parallel --xunit-output tests.xml || true
  fi

  # Ensure files created by Docker (running as root) are accessible
  cd ..
  chmod -R 777 .
}

main () {
  build_and_test_the_code
}

main "${@}"
