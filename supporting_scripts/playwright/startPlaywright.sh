#!/bin/sh

set -e

artemis_path="$(readlink -f "$(dirname "$0")/../..")"

echo "Installing Playwright and dependencies"

cd "$artemis_path/src/test/playwright"

npm install

npm run playwright:setup-local || true

echo "Run all playwright tests"
npm run playwright:test
