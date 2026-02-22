#!/usr/bin/env bash
set -e

maven () {
  echo '⚙️ executing maven'
  # Compile the code
  mvn clean compile test-compile
  COMPILATION_EXIT_CODE=$?

  if [ $COMPILATION_EXIT_CODE -ne 0 ]; then
      exit 1
  fi

  # Run the tests
  mvn test || true
}

main () {
  maven
}

main "${@}"
