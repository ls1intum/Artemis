#!/usr/bin/env bash
set -e

build_and_test_the_code () {
  echo '⚙️ executing build_and_test_the_code'
  # Compile the code
  python3 -m compileall . -q
  COMPILATION_EXIT_CODE=$?

  if [ $COMPILATION_EXIT_CODE -ne 0 ]; then
      exit 1
  fi

  # Run the tests
  pytest --junitxml=test-reports/results.xml || true
}

main () {
  build_and_test_the_code
}

main "${@}"
