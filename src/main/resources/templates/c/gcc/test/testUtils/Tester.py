from typing import Dict
from testUtils.AbstractTest import AbstractTest
from testUtils.Utils import printTester, getTesterOutput, clearTesterOutputCache, resetStdoutLimit, setStdoutLimitEnabled
from testUtils.junit.Junit import Junit
from testUtils.junit.TestSuite import TestSuite


class Tester:
    name: str
    suite: TestSuite
    tests: Dict[str, AbstractTest]

    def __init__(self, name: str = "GBS-Tester-1.31"):
        self.name = name
        self.suite = TestSuite(name)
        self.tests = dict()

    def run(self):
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
                printTester("Running test case '{}' with a {} second timeout...".format(
                    name, test.timeoutSec))
            else:
                printTester(
                    f"Running test case '{name}' with no timeout...")

            # Reset the tester output cache:
            resetStdoutLimit()
            setStdoutLimitEnabled(True)
            clearTesterOutputCache()

            test.start(testResults, self.suite)

            setStdoutLimitEnabled(False)
            printTester("Finished test case '{}' in {} seconds.".format(
                name, test.case.time.total_seconds()))

            # Store the tester output in the test case:
            test.case.testerOutput = self.name + "\n" + getTesterOutput()
            # Update test results:
            testResults[name] = test.case.result
        self.__printResult()

    def addTest(self, test: AbstractTest):
        """
        Adds a new test that will be run once "run()" is invoked.
        """

        if test.name in self.tests:
            raise NameError(
                f"Test '{test.name}' already registered. Test names should be unique!")
        self.tests[test.name] = test

    def __printResult(self):
        print("Result".center(50, "="))
        print("{} finished {} test cases in {} seconds.".format(
            self.name, len(self.tests), self.suite.time.total_seconds()))
        print(f"SUCCESS: {self.suite.successful}")
        print(f"FAILED: {self.suite.failures}")
        print(f"ERROR: {self.suite.errors}")
        print(f"SKIPPED: {self.suite.skipped}")
        print("".center(50, "="))

    def exportResult(self, outputPath: str):
        """
        Exports the test results into a JUnit format and stores it at the given outputPath.
        """

        junit: Junit = Junit(self.suite)
        junit.toXml(outputPath)
