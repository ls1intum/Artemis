#!/usr/bin/env bash
set -e

build_and_test_the_code () {
  echo '⚙️ executing build_and_test_the_code'
  # the build process is specified in `run.sh` in the test repository
  # -s enables the safe testing mode
  chmod +x run.sh

  # Run the build and tests
  # run.sh exits with 1 on compilation failure, 0 otherwise
  ./run.sh -s
  COMPILATION_EXIT_CODE=$?

  if [ $COMPILATION_EXIT_CODE -ne 0 ]; then
      exit 1
  fi
}

main () {
  build_and_test_the_code
}

main "${@}"
