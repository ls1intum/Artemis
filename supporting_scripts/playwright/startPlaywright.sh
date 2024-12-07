#!/bin/sh

set -e

cd ../..

echo "Installing Playwright and dependencies"

cd src/test/playwright

npm install

npm run playwright:setup-local || true

echo "Starting Playwright in UI mode"
npm run playwright:open
