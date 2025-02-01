#!/usr/bin/env bash
set -e

test () {
  echo '⚙️ executing test'
  cd "${testWorkingDirectory}"
  bundler exec rake ci:test
}

main () {
  test
}

main "${@}"
