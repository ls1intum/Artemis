from testUtils.Tester import Tester
from tests.TestCompile import TestCompile
from tests.TestASan import TestASan
from tests.TestUBSan import TestUBSan
from tests.TestLSan import TestLSan
from tests.TestOutput import TestOutput

from string import ascii_letters, digits
from random import choices, randint

def main():
    # Makefile location:
    # Artemis expects it to be located in ../assignment
    makefileLocation: str = "../assignment/"

    # Create a new instace of the tester:
    tester: Tester = Tester()
    
    # Register all test cases:

    # Basic compile test:
    # Run after the sanitizer so we run the tests without any sanitizer enabled
    testCompile: TestCompile = TestCompile(makefileLocation, "rotX")
    tester.addTest(testCompile)  

    # Sanitizer:
    
    # Address Sanitizer:
    # This test requires the "TestCompile" to finish with "SUCCESS" to be run:
    testASan: TestASan = TestASan(makefileLocation, requirements=[testCompile.name])
    tester.addTest(testASan)

    # Undefined Behavior Sanitizer:
    # This test requires the "TestCompile" to finish with "SUCCESS" to be run:
    testUBSan: TestUBSan = TestUBSan(makefileLocation, requirements=[testCompile.name])
    tester.addTest(testUBSan)

    # Leak Sanitizer:
    # This test requires the "TestCompile" to finish with "SUCCESS" to be run:
    testLSan: TestLSan = TestLSan(makefileLocation, requirements=[testCompile.name])
    tester.addTest(testLSan)

    # Run the actual tests:
    tester.run()
    # Export the results into the JUnit XML format:
    tester.exportResult("../test-reports/tests-results.xml")

def __getRandomSting(len: int):
    return ''.join(choices(ascii_letters + digits, k=len))

if __name__ == '__main__':
    main()
