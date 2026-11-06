## C tests with FACT

This template uses [FACT](https://fact.readthedocs.io/en/latest/), a Python framework that compiles and tests C programs. Python runs the tests; students still write C.

- `Makefile` builds `exercise.c` with GCC, C11, and warning flags. Artemis copies this Makefile into the assignment directory before compiling, so students cannot replace the build configuration with their own Makefile.
- `Tests.py` loads `exercise.yml`, runs its tests, and exports the results. It is the entry point, rather than the place to add individual test cases.
- `exercise.yml` declares the translation unit, test names, dependencies, and structural requirements. The initial tests are `Compile`, `CodeStructure`, and `InputOutput`.
- `exercise_io.txt` contains the input/output cases. The supplied example also substitutes values of `x` and `y` in the student's source before checking its output.

The default FACT build image, `ls1tum/artemis-fact-minimal-docker:1.1.0`, supplies Python, FACT, and its compiler dependencies. Custom build images must supply these dependencies too.

For a local run, arrange the solution or student checkout beside this test repository:

```text
exercise/
  assignment/       # solution or student sources
  tests/            # this repository
  test-reports/
```

FACT expects the assignment at `../assignment` relative to the test directory. From the test directory in an environment with the dependencies above, copy the test-controlled Makefile into the assignment as the build does, then run the tests:

```sh
cp Makefile ../assignment/Makefile
python3 Tests.py
```

Copying only this test directory to a machine with Python is insufficient: FACT, its compiler dependencies, and the assignment checkout must also be available.

Adapt the Makefile and translation unit when renaming or adding C source files. Define new tests in `exercise.yml`, add their data or Python implementations as required by the test type, and update test dependencies. Test names must match those referenced in the exercise problem statement and grading configuration.

Artemis collects the JUnit XML report at `test-reports/tests-results.xml`. Check the build log when compilation or test setup fails; a failure before report generation cannot be diagnosed from the score alone. Verify the solution and template repositories after changing the tests.

See the [FACT test definition reference](https://fact.readthedocs.io/en/latest/test_definition_file.html) for configuration syntax and supported test types.
