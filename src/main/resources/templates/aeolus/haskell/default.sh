#!/usr/bin/env bash
set -e

build_and_test_the_code () {
  echo '⚙️ executing build_and_test_the_code'
  # the build process is specified in `run.sh` in the test repository
  # -s enables the safe testing mode
  chmod +x run.sh

  # Run the build and tests
  ./run.sh -s || true
}

main () {
  build_and_test_the_code
}

main "${@}"
