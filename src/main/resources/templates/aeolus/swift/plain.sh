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

main () {
  build_and_test_the_code
}

main "${@}"
