#!/usr/bin/env bash
set -e

gradle () {
  echo '⚙️ executing gradle'
  chmod +x ./gradlew

  # Compile the code
  ./gradlew clean testClasses
  COMPILATION_EXIT_CODE=$?

  if [ $COMPILATION_EXIT_CODE -ne 0 ]; then
      exit 1
  fi

  # Run the tests
  ./gradlew test || true
}

main () {
  gradle
}

main "${@}"
