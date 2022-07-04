from datetime import timedelta
from typing import Dict
from xml.etree import ElementTree as Et

from testUtils.junit.TestCase import Result, TestCase


class TestSuite:
    __cases: Dict[str, TestCase]

    name: str
    tests: int
    failures: int
    errors: int
    skipped: int
    successful: int
    time: timedelta

    def __init__(self, name: str):
        self.name = name

        self.__cases: Dict[str, TestCase] = dict()
        self.tests: int = 0
        self.failures: int = 0
        self.errors: int = 0
        self.skipped: int = 0
        self.successful: int = 0
        self.time: timedelta = timedelta()

    def addCase(self, case: TestCase):
        self.__cases[case.name] = case
        self.tests += 1
        self.time += case.time

        if case.result == Result.ERROR:
            self.errors += 1
        elif case.result == Result.FAILURE:
            self.failures += 1
        elif case.result == Result.SKIPPED:
            self.skipped += 1
        else:
            self.successful += 1

    def toXml(self):
        suite: Et.Element = Et.Element("testsuite")
        suite.set("name", self.name)
        suite.set("tests", str(self.tests))
        suite.set("failures", str(self.failures))
        suite.set("errors", str(self.errors))
        suite.set("skipped", str(self.skipped))
        suite.set("time", str(self.time.total_seconds()))

        for _name, case in self.__cases.items():
            case.toXml(suite)
        return suite
