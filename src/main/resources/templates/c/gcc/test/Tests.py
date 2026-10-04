import os

from tests.TestASan import TestASan
from tests.TestCompile import TestCompile
from tests.TestLSan import TestLSan
from tests.TestOutput import TestOutput
from tests.TestUBSan import TestUBSan
from testUtils.Tester import Tester


def configureSanitizerRuntime():
    """
    Disables AddressSanitizer's exit-time leak scan for the programs started by this tester.

    GCC's AddressSanitizer runs LeakSanitizer when an instrumented program exits. That leak scan
    stops the world via ptrace, which Docker's default seccomp profile denies (the build containers
    intentionally do not get CAP_SYS_PTRACE, because they execute untrusted student code). Without
    ptrace the scan does not fail fast: it stalls, so a correct `asan.out` never reaches exit and the
    test is reported as a timeout. That is why `TestOutputASan` was flaky while `TestOutputUBSan` --
    same instrumentation cost, no leak scan -- always passed.

    Leak coverage is not lost: leaks are checked by the dedicated `-fsanitize=leak` test, which reads
    LSAN_OPTIONS rather than ASAN_OPTIONS and is unaffected by this setting. `setdefault` keeps an
    explicit ASAN_OPTIONS from the environment authoritative.
    """
    os.environ.setdefault("ASAN_OPTIONS", "detect_leaks=0")


def main():
    configureSanitizerRuntime()

    # Makefile location:
    # Artemis expects it to be located in ../assignment
    makefileLocation: str = "../assignment"

    # Create a new instance of the tester:
    tester: Tester = Tester()

    # Register all test cases:

    # Basic compile test:
    # Run after the sanitizer so we run the tests without any sanitizer enabled
    testCompile: TestCompile = TestCompile(makefileLocation, "helloWorld")
    tester.addTest(testCompile)

    # Test Hello World:
    # This test requires the "TestCompile" to finish with "SUCCESS" to be run:
    tester.addTest(TestOutput(makefileLocation, [testCompile.name]))

    # Sanitizer:

    # Address Sanitizer:
    # This test requires the "TestCompile" to finish with "SUCCESS" to be run:
    testASan: TestASan = TestASan(makefileLocation, requirements=[testCompile.name])
    tester.addTest(testASan)
    # This test requires the "TestASan" to finish with "SUCCESS" to be run:
    tester.addTest(TestOutput(makefileLocation, requirements=[testASan.name], name="TestOutputASan", executable="asan.out"))

    # Undefined Behavior Sanitizer:
    # This test requires the "TestCompile" to finish with "SUCCESS" to be run:
    testUBSan: TestUBSan = TestUBSan(makefileLocation, requirements=[testCompile.name])
    tester.addTest(testUBSan)
    # This test requires the "TestUBSan" to finish with "SUCCESS" to be run:
    tester.addTest(TestOutput(makefileLocation, requirements=[testUBSan.name], name="TestOutputUBSan", executable="ubsan.out"))

    # Leak Sanitizer:
    # This test requires the "TestCompile" to finish with "SUCCESS" to be run:
    testLSan: TestLSan = TestLSan(makefileLocation, requirements=[testCompile.name])
    tester.addTest(testLSan)
    # This test requires the "TestLSan" to finish with "SUCCESS" to be run:
    tester.addTest(TestOutput(makefileLocation, requirements=[testLSan.name], name="TestOutputLSan", executable="lsan.out"))

    # Run the actual tests:
    tester.run()
    # Export the results into the JUnit XML format:
    tester.exportResult("../test-reports/tests-results.xml")


if __name__ == "__main__":
    main()
