from testUtils.Tester import Tester
from tests.TestASan import TestASan
from tests.TestUBSan import TestUBSan
from tests.TestLSan import TestLSan
from tests.TestInput import TestInput
from random import randint
from sys import argv

def main():
    # Makefile location:
    # Artemis expects it to be located in ../assignment/
    makefileLocation: str = "../assignment/"

    # Create a new instace of the tester:
    tester: Tester = Tester()

    # Register all test cases:

    # Basic compile test:
    # Run after the sanitizer so we run the tests without any sanitizer enabled

    # IO Tests:
    tester.addTest(TestInput(makefileLocation, 0, name="TestInput_0"))
    tester.addTest(TestInput(makefileLocation, 1, name="TestInput_1"))
    tester.addTest(TestInput(makefileLocation, 5, name="TestInput_5"))
    tester.addTest(TestInput(makefileLocation, 7, name="TestInput_7"))
    tester.addTest(TestInput(makefileLocation, 10, name="TestInput_10"))

    # Random IO Tests:
    for i in range(0, 5):
        tester.addTest(TestInput(makefileLocation, randint(0, 15), name="TestInputRandom_" + str(i)))

    # Sanitizer:
    # Address Sanitizer:
    testASan: TestASan = TestASan(makefileLocation)
    tester.addTest(testASan)
    tester.addTest(TestInput(makefileLocation, 1, requirements=[testASan.name], name="TestInputASan_1", executable="asan.out"))
    tester.addTest(TestInput(makefileLocation, 5, requirements=[testASan.name], name="TestInputASan_5", executable="asan.out"))

    # Undefined Behavior Sanitizer:
    testUBSan: TestUBSan = TestUBSan(makefileLocation)
    tester.addTest(testUBSan)
    tester.addTest(TestInput(makefileLocation, 1, requirements=[testUBSan.name], name="TestInputUBSan_1", executable="ubsan.out"))
    tester.addTest(TestInput(makefileLocation, 5, requirements=[testUBSan.name], name="TestInputUBSan_5", executable="ubsan.out"))

    # Leak Sanitizer:
    testLSan: TestLSan = TestLSan(makefileLocation)
    tester.addTest(testLSan)
    tester.addTest(TestInput(makefileLocation, 1, requirements=[testLSan.name], name="TestInputLSan_1", executable="lsan.out"))
    tester.addTest(TestInput(makefileLocation, 5, requirements=[testLSan.name], name="TestInputLSan_5", executable="lsan.out"))

    # Run the actual tests:
    tester.run()
    # Export the results into the JUnit XML format:
    # Test run name
    if len(argv) is 1:
        run = ""
    else:
        run = argv[1] + "-"
    tester.exportResult(f"../test-reports/{run}results.xml")

if __name__ == '__main__':
    main()