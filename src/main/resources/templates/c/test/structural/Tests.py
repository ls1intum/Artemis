from testUtils.Tester import Tester
from tests.EmptyTest import EmptyTest
from sys import argv

def main():
    # Create a new instace of the tester:
    tester: Tester = Tester()

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