from typing import Dict, List

from testUtils.AbstractTest import AbstractTest
from testUtils.junit.Junit import Junit
from testUtils.junit.TestCase import Result
from testUtils.junit.TestSuite import TestSuite
from xml.etree import ElementTree as Et
from testUtils.Utils import clearTesterOutputCache, getTesterOutput, printTester, resetStdoutLimit, setStdoutLimitEnabled


class Tester:
    name: str
    suite: TestSuite
    additionalSuites: List[Et.Element]
    tests: Dict[str, AbstractTest]

    def __init__(self, name: str = "GBS-Tester-1.36") -> None:
        self.name = name
        self.suite = TestSuite(name)
        self.additionalSuites = []
        self.tests = dict()

    def run(self) -> None:
        """
        Starts the tester and runs all tests added via "addTest(test: AbstractTest)".
        """

        setStdoutLimitEnabled(False)
        printTester(f"Running: {self.name}")

        # A dictionary of test results:
        # Test name -> result
        testResults: Dict[str, Result] = dict()

        for name, test in self.tests.items():
            if test.timeoutSec >= 0:
                printTester(f"Running test case '{name}' with a {test.timeoutSec} second timeout...")
            else:
                printTester(f"Running test case '{name}' with no timeout...")

            # Reset the tester output cache:
            resetStdoutLimit()
            setStdoutLimitEnabled(True)
            clearTesterOutputCache()

            test.start(testResults, self.suite, self.additionalSuites)

            setStdoutLimitEnabled(False)
            printTester(f"Finished test case '{name}' in {test.case.time.total_seconds()} seconds.")

            # Store the tester output in the test case:
            test.case.testerOutput = self.name + "\n" + getTesterOutput()
            # Update test results:
            testResults[name] = test.case.result
        self.__printResult()

    def addTest(self, test: AbstractTest) -> None:
        """
        Adds a new test that will be run once "run()" is invoked.
        """

        if test.name in self.tests:
            raise NameError(f"Test '{test.name}' already registered. Test names should be unique!")
        self.tests[test.name] = test

    def __printResult(self) -> None:
        print("Result".center(50, "="))
        print(f"{self.name} finished {len(self.tests)} test cases in {self.suite.time.total_seconds()} seconds.")
        print(f"SUCCESS: {self.suite.successful}")
        print(f"FAILED: {self.suite.failures}")
        print(f"ERROR: {self.suite.errors}")
        print(f"SKIPPED: {self.suite.skipped}")
        print("".center(50, "="))

    def exportResult(self, outputPath: str) -> None:
        """
        Exports the test results into a JUnit format and stores it at the given outputPath.
        """

        junit: Junit = Junit(self.suite, self.additionalSuites)
        junit.toXml(outputPath)
