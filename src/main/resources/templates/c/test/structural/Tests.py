from testUtils.Tester import Tester
from tests.TestCompile import TestCompile
from sys import argv

def main():
    # Makefile location:
    # Artemis expects it to be located in ../assignment/
    makefileLocation: str = "../assignment/"


    # Create a new instace of the tester:
    tester: Tester = Tester()

    # Register all test cases:
    testCompile: TestCompile = TestCompile(makefileLocation)
    tester.addTest(testCompile)


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