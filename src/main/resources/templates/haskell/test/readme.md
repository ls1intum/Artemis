# Test Repository Instructions

## Requirements

Tests are run using [stack](https://docs.haskellstack.org/en/stable/README/) in a docker container.

## Setup

The executables specified in `test.cabal` expect the solution repository checked out in the `${solutionWorkingDirectory}` subdirectory and
the submission checked out in the `${studentParentWorkingDirectoryName}` subdirectory.
Moreover, `test.cabal` provides an executable to test the template repository locally.
For this, it expects the template repository in the `template` subdirectory.

You can find a script to conveniently set up this folder structure when checking out a new exercise in
the [programming exercise setup documentation](https://docs.artemis.cit.tum.de/user/exercises/programming/#setup).

## Running Tests

Refer to `test.cabal` for detailed information about the targets and flags provided.

### Locally

You can run executables specified in `test.cabal` using `stack run <executableName>`.

### On Artemis

By default, Artemis runs `./run.sh -s` to execute the tests.
You can modify `run.sh` to adapt the build and test process.

## Updating Dependencies

If you plan to update the stack resolver including its GHC version, you also have to create a new docker image and
update the corresponding Build configuration.
