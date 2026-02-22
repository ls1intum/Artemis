#!/usr/bin/env bash
set -e

test () {
  echo '⚙️ executing test'
  cd "${testWorkingDirectory}"

  sudo mkdir test-results
  sudo chown matlab:matlab test-results
  sudo rm /etc/sudoers.d/matlab

  # Run the tests
  matlab -batch testRunner || true

}

main () {
  test
}

main "${@}"
