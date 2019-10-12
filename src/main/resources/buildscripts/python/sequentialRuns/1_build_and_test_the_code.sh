#!/usr/bin/env bash

pytest structural/* --junitxml=test-reports/structural-results.xml

# Only continue if structural tests have been successful
if ! grep -P '(errors|failures)="[1-9]+' test-reports/structural-results.xml ; then
  pytest behavior/* --junitxml=test-reports/behavior-results.xml
fi

exit 0