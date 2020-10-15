from testUtils.Tester import Tester
from tests.TestCompile import TestCompile
from tests.TestASan import TestASan
from tests.TestUBSan import TestUBSan
from tests.TestLSan import TestLSan
from tests.TestOutput import TestOutput
from tests.TestGccStaticAnalysis import TestGccStaticAnalysis

from string import ascii_letters, digits
from random import choices, randint


def main():
    # Makefile location:
    # Artemis expects it to be located in ../assignment
    makefileLocation: str = "../assignment"

    # Create a new instace of the tester:
    tester: Tester = Tester()

    # Register all test cases:

    # Basic compile test:
    # Run after the sanitizer so we run the tests without any sanitizer enabled
    testCompile: TestCompile = TestCompile(makefileLocation, "rotX")
    tester.addTest(testCompile)

    # Test RotX:
    # This test requires the "TestCompile" to finish with "SUCCESS" to be run:
    tester.addTest(TestOutput(makefileLocation, 0,
                              "aAbByYzZ123!%&/()Oau", [testCompile.name], name="TestOutput_0"))
    tester.addTest(TestOutput(makefileLocation, 1,
                              "aAbByYzZ123!%&/()Oau", [testCompile.name], name="TestOutput_1"))
    tester.addTest(TestOutput(makefileLocation, 26,
                              "aAbByYzZ123!%&/()Oau", [testCompile.name], name="TestOutput_26"))

    # Random RotX tests:
    for i in range(0, 10):
        tester.addTest(TestOutput(makefileLocation, randint(26, 2626), __getRandomSting(
            randint(10, 20)), [testCompile.name], name="TestOutputRandom_" + str(i)))

    # Sanitizer:

    # Address Sanitizer:
    # This test requires the "TestCompile" to finish with "SUCCESS" to be run:
    testASan: TestASan = TestASan(
        makefileLocation, requirements=[testCompile.name])
    tester.addTest(testASan)
    # This test requires the "TestASan" to finish with "SUCCESS" to be run:
    tester.addTest(TestOutput(makefileLocation, 27, "aAbByYzZ123!%&/()Oau",
                              requirements=[testASan.name], name="TestOutputASan", executable="asan.out"))

    # Undefined Behavior Sanitizer:
    # This test requires the "TestCompile" to finish with "SUCCESS" to be run:
    testUBSan: TestUBSan = TestUBSan(
        makefileLocation, requirements=[testCompile.name])
    tester.addTest(testUBSan)
    # This test requires the "TestUBSan" to finish with "SUCCESS" to be run:
    tester.addTest(TestOutput(makefileLocation, 27, "aAbByYzZ123!%&/()Oau",
                              requirements=[testUBSan.name], name="TestOutputUBSan", executable="ubsan.out"))

    # Leak Sanitizer:
    # This test requires the "TestCompile" to finish with "SUCCESS" to be run:
    testLSan: TestLSan = TestLSan(
        makefileLocation, requirements=[testCompile.name])
    tester.addTest(testLSan)
    # This test requires the "TestLSan" to finish with "SUCCESS" to be run:
    tester.addTest(TestOutput(makefileLocation, 27, "aAbByYzZ123!%&/()Oau",
                              requirements=[testLSan.name], name="TestOutputLSan", executable="lsan.out"))

    # GCC static analysis:
    # This test requires the "TestCompile" to finish with "SUCCESS" to be run:
    testGccStaticAnalysis: TestGccStaticAnalysis = TestGccStaticAnalysis(
        makefileLocation, requirements=[testCompile.name])
    tester.addTest(testGccStaticAnalysis)

    # Run the actual tests:
    tester.run()
    # Export the results into the JUnit XML format:
    tester.exportResult("../test-reports/tests-results.xml")


def __getRandomSting(len: int):
    return ''.join(choices(ascii_letters + digits, k=len))


if __name__ == '__main__':
    main()
