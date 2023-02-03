#!/bin/sh

cd src/main/docker/cypress

# Update the flaky dependencies to latest versions during active development
echo "Updating flaky dependencies"
sed -i 's/npm ci/npm i -D @heddendorp\/coverage-git-compare@latest @heddendorp\/cypress-plugin-multilanguage-coverage@latest/g' cypress-E2E-tests-coverage-override.yml
