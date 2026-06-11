#!/bin/bash
# Arguments:
# -s: run tests safely; in particular, remove solution and test repositories before executing the tests

safe=false
only=all

# check passed flags
while [ $# -gt 0 ]; do
  case "$1" in
    -s)
      safe=true
      shift
      ;;
    --only)
      if [ -z "${2:-}" ]; then
        echo "Missing value for --only (expected: all|compile)" >&2
        exit 1
      fi
      only="$2"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done

if [ "$only" != "all" ] && [ "$only" != "compile" ]; then
    echo "Unknown value for --only: $only" >&2
    exit 1
fi

# check for symlinks as they might be abused to link to the sample solution
$safe && find ${studentParentWorkingDirectoryName}/ -type l | grep -q . && echo "Cannot build with symlinks in submission." && exit 1

# check for unsafe OPTIONS and OPTIONS_GHC pragma as they allow to overwrite command line arguments
$safe && \
while IFS= read file; do
  cat $file | tr -d '\n' | grep -qim 1 "{-#[[:space:]]*options" && \
    echo "Cannot build with \"{-# OPTIONS..\" pragma in source." && exit 1
done < <(find ${studentParentWorkingDirectoryName}/src -type f)

# build the libraries - do not forget to set the right compilation flag (Prod)
stack build --allow-different-user --flag test:Prod || exit 1

if [ "$only" = "compile" ]; then
    exit 0
fi

# delete the solution and tests (so that students cannot access it) when in safe mode
if $safe; then
    rm -rf ${solutionWorkingDirectory}
    rm -rf test
fi

# run the test executable and return 0
# Note: as a convention, a failed haskell tasty test suite returns 1, but this stops the JUnit Parser from running.
stack exec test --allow-different-user || exit 0
