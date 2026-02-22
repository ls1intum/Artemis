# Aeolus Windfile Templates

This directory contains windfile templates for different programming languages used by Artemis to generate build scripts for programming exercises.

## Exit Code Convention

The build script's exit code determines whether compilation succeeded:

| Exit Code | Meaning |
|-----------|---------|
| **0** | Compilation successful (tests may have passed or failed) |
| **Non-zero** | Compilation failed |

## Test Result Masking

Test commands are masked with `|| true` to ensure test failures don't cause a non-zero exit code. This is because:

1. **Test results are determined from JUnit XML files**, not exit codes
2. We want to distinguish between "compilation failed" and "tests failed"
3. A compilation failure should signal that no tests could run

### Example Pattern

```yaml
script: |-
  # Compile the code
  ./gradlew clean testClasses
  COMPILATION_EXIT_CODE=$?

  if [ $COMPILATION_EXIT_CODE -ne 0 ]; then
      exit 1
  fi

  # Run the tests (masked to not affect exit code)
  ./gradlew test || true
```

## Template Categories

### Standard Templates
Most templates follow the pattern above: compile first, check for failure, then run tests with `|| true`.

### Script-Based Templates
Some languages (Haskell, OCaml, Assembler, VHDL) use custom `run.sh` or Python scripts that handle compilation internally. These scripts are run with `|| true`.

### Templates with File Operations
Some templates (Assembler, VHDL, C) have `|| exit 2` patterns for critical file operations (like copying Makefiles). These correctly signal compilation failure if the files can't be set up.
