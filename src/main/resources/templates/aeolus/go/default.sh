#!/usr/bin/env bash
set -e

test () {
  echo '⚙️ executing test'
  cd "${testWorkingDirectory}"
  go test ./... -json 2>&1 | go-junit-report -parser gojson -out test-results.xml

}

main () {
  test
}

main "${@}"
