#!/usr/bin/env bash
set -e

build_and_test_the_code () {
  echo '⚙️ executing build_and_test_the_code'
  # the build process is specified in `run.sh` in the test repository
  # -s enables the safe testing mode
  chmod +x run.sh
  ./run.sh -s
}

main () {
  build_and_test_the_code
}

main "${@}"
