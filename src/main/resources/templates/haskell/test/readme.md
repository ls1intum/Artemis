# Test Repository Instructions

## Requirements

Tests are run using [stack](https://docs.haskellstack.org/en/stable/README/).

## Setup

The executables specified in `test.cabal` expect the solution repository checked out in the `solution` subdirectory and the submission checked out in the `assignment` subdirectory.
Moreover, it provides an executable to test the template repository locally by placing it in the `template` subdirectory.

You can use `../setup_exercise.sh` to conveniently setup this folder structure when checking out a new exercise.


## Running Tests

Refer to `test.cabal` for detailed information about the targets and flags provided.

### Locally

You can run executables specified in `test.cabal` using `stack run <executableName>`.

### On Artemis

You can use the following command to safely test a student's submission on Artemis:

```bash
# Build the libraries, delete the solution and tests (so that students cannot access it), and run the test executable.
# Do not forget to set the right compilation flags (Prod).
# Finally, return 0: as a convention, a failed haskell tasty test suite returns 1, but this stops the JUnit Parser from running.
stack build --allow-different-user --flag test:Prod && rm -rf solution && rm -rf test && (stack exec test --allow-different-user || exit 0)
```

## Updating Dependencies

If you plan to update the stack resolver including its GHC version, you also have to create a new docker image and update the corresponding Bamboo configuration.
