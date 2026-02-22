#!/usr/bin/env bash
set -e

build_and_test_the_code () {
  echo '⚙️ executing build_and_test_the_code'
  # copy test files
  cp -R Tests ${studentParentWorkingDirectoryName}
  cp Package.swift ${studentParentWorkingDirectoryName}

  # In order to get the correct console output we need to execute the command within the ${studentParentWorkingDirectoryName} directory
  # swift build
  cd ${studentParentWorkingDirectoryName}

  # Compile the code
  swift build
  COMPILATION_EXIT_CODE=$?

  if [ $COMPILATION_EXIT_CODE -ne 0 ]; then
      cd ..
      chmod -R 777 .
      exit 1
  fi

  # Run the tests
  swift test || true
  cd ..
  chmod -R 777 .
}

main () {
  build_and_test_the_code
}

main "${@}"
