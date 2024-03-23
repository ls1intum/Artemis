#!/usr/bin/env bash
set -e

build_and_test_the_code () {
  echo '⚙️ executing build_and_test_the_code'
  python3 -m compileall . -q || error=true
  if [ ! $error ]
  then
      pytest --junitxml=test-reports/results.xml
  fi
}

main () {
  build_and_test_the_code
}

main "${@}"
