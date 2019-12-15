#!/usr/bin/env bash

# Create folder structur for tests
cd src/main/resources/templates/python || exit 1
mkdir -p github-actions/assignment

# Copy tests and template to run test against
cp -r "$1"/* github-actions/assignment/
cp -r test/behavior github-actions/
cp -r test/structural github-actions/
mv github-actions/behavior/*_test.py github-actions/
mv github-actions/structural/*_test.py github-actions/

cd github-actions || exit 1
python3 -m pytest --junitxml=test-reports/results.xml
