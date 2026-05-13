#!/bin/sh

set -e

artemis_path="$(readlink -f "$(dirname "$0")/../..")"

echo "Installing Playwright and dependencies"

cd "$artemis_path/src/test/playwright"

pnpm install --frozen-lockfile

pnpm run playwright:setup-local || true

echo "Run all playwright tests"
pnpm run playwright:test
