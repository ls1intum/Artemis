#!/bin/sh
# Makes the pnpm that package.json pins available inside the Playwright image of the E2E stack.
#
# The image used to ship corepack, which provides the pinned pnpm. The tag of the image is mutable, and a rebuild of it came without
# corepack. Every E2E run then stopped with `sh: 1: corepack: not found` before a single test ran, on develop and on every pull request.
# So corepack is used when the image has it, and the pinned version is installed with npm when it does not. Either way the version
# is the one in the packageManager field of package.json, so there is no second place that pins it.
set -eu

cd "$(dirname "$0")/../../.."

if command -v corepack >/dev/null 2>&1; then
    corepack enable
    exit 0
fi

# for example pnpm@12.15.0
pnpm_spec=$(node -p "require('./package.json').packageManager")
case "$pnpm_spec" in
    pnpm@*)
        npm install --global "$pnpm_spec"
        ;;
    *)
        echo "package.json does not pin pnpm in packageManager (found: $pnpm_spec)" >&2
        exit 1
        ;;
esac
