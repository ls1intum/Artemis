#!/usr/bin/env bash
set -e

maven () {
  echo '⚙️ executing maven'
  mvn clean test
}

main () {
  maven
}

main "${@}"
