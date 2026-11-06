## C tests with GCC and Python

Python runs this template's tests against compiled C programs. The runner and supporting framework are included in the test repository; this template does not use FACT.

- `Tests.py` creates a `Tester`, registers the compilation, output, and sanitizer tests, runs them, and exports JUnit XML to `../test-reports/tests-results.xml`.
- `tests/` contains the individual test classes. `TestCompile` builds the assignment; `TestOutput` checks its behavior; the sanitizer tests rebuild it with AddressSanitizer, UndefinedBehaviorSanitizer, or LeakSanitizer. A test's `requirements` list names tests that must pass first.
- `testUtils/Tester.py` coordinates test execution. `AbstractTest.py` and `AbstractProgramTest.py` provide the shared test behavior, while `testUtils/junit/` writes the report Artemis reads.
- `Makefile` defines the build and sanitizer targets. Artemis copies it into the assignment checkout and preserves the assignment's `SOURCE` and `INCLUDEDIRS` settings. The runner expects that checkout at `../assignment`.
- `requirements.txt` lists the Python dependencies. `Converter.py` is an optional helper for converting supported test data; the build runner does not invoke it.

Arrange your local checkout like this:

```text
exercise/
  assignment/       # solution or student sources
  tests/            # this repository
  test-reports/
```

Use the exercise's build image, or an environment with Python, GNU make, GCC, and the sanitizer runtimes. Install the dependencies and run from the test directory:

```sh
python3 -m pip install -r requirements.txt
python3 Tests.py
```

Use the test-controlled Makefile in `assignment/` as the build plan does, retaining the assignment's source and include settings. Keep the relative path to `testUtils/c/shadow_exec.c` intact; it provides the execution wrapper used by the tests.

To add a test, implement a class under `tests/` and register it in `Tests.py` with a unique name and any prerequisites. Keep test names consistent with the exercise problem statement and grading configuration. Check the generated XML and verify both the solution and template builds before publishing the exercise.
