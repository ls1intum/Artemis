#!/usr/bin/env bash

# Actual build process:
cd tests || exit 1

# Structural tests
cp structural/Tests.py ./
cp -r structural/tests ./tests
python3 Tests.py structural

# Only continue if structural tests have been successful
if ! grep -P '(errors|failures)="[1-9]+' ../test-reports/structural-results.xml ; then
  # Cleanup structural tests
  rm Tests.py
  rm -rf ./tests

  exit 0
fi

exit 1