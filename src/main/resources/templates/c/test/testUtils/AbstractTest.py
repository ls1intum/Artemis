from abc import ABC, abstractmethod
from traceback import print_exc
from signal import SIGALRM, SIG_IGN, alarm, signal
from datetime import datetime, timedelta
from contextlib import contextmanager
from typing import Optional, List, Dict
from os import path, makedirs
from io import TextIOWrapper
from testUtils.junit.TestSuite import TestSuite
from testUtils.junit.TestCase import TestCase, Result
from testUtils.Utils import printTester,PWrap
from testUtils.TestFailedError import TestFailedError

# Timeout handler based on: https://www.jujens.eu/posts/en/2018/Jun/02/python-timeout-function/
class AbstractTest(ABC):
    """
    A abstract test that every test has to inherit from.
    How to:
    1. Inherit from AbstractTest
    2. Override the "_run()" method.
    3. Override the "_onTimeout()" method.
    4. Override the "_onFailed()" method.
    5. Done
    """

    name: str = ""
    requirements: List[str] = list()
    timeoutSec: int = -1
    case: Optional[TestCase] = None
    suite: Optional[TestSuite] = None

    def __init__(self, name: str, requirements: List[str] = list(), timeoutSec: int = -1):
        """
        name: str
            An unique test case name.

        requirements: List[str]
            A list of test cases names that have to finish successfully for this test to run.
            Usually an execution test should have the compile test as it's requirement.

        timeoutSec: int
            The test case timeout in seconds,
        """

        self.name = name
        self.timeoutSec = timeoutSec
        self.requirements = requirements

    def start(self, testResults: Dict[str, Result], suite: TestSuite):
        """
        Starts the test run.

        ---

        testResults: Dict[str, Result]
            All test results up to this point.

        suite: TestSuite
            The test suite where this test should get added to.
        """

        self.suite = suite
        self.case = TestCase(self.name)
        self.suite.addCase(self.case)

    # Check if all test requirements (other tests) are fulfilled:
        if not self.__checkTestRequirements(testResults):
            printTester("Skipping test case '{}' not all requirements ({}) are fulfilled".format(self.name, str(self.requirements)))
            self.case.message = "Test requires other test cases to succeed first ({})".format(str(self.requirements))
            self.case.result = Result.SKIPPED
            self.case.stdout = ""
            self.case.stderr = ""
            self.case.time = timedelta()
            return

        startTime: datetime = datetime.now()

        self._initOutputDirectory()

        if self.timeoutSec > 0:
            # Run with timeout:
            with self.__timeout(self.timeoutSec):
                try:
                    self._run()
                except TestFailedError:
                    printTester("'{}' failed.".format(self.name))
                except TimeoutError:
                    self._timeout()
                except Exception as e:
                    printTester("'{}' had an internal error. {}.\nPlease report this!".format(self.name, str(e)))
                    print_exc()
                    self._onFailed()
        else:
            # Run without timeout:
            try:
                self._run()
            except TestFailedError:
                printTester("'{}' failed.".format(self.name))
            except Exception:
                printTester("'{}' had an internal error. Please report this!".format(self.name))
                print_exc()
                self._onFailed()

        self.case.time = datetime.now() - startTime

    def __checkTestRequirements(self, testResults: Dict[str, Result]):
        """
        Checks if all requirements (i.e. other test cases were successfull) are fulfilled.
        """

        for req in self.requirements:
            if (not req in testResults) or (testResults[req] != Result.SUCCESS):
                return False
        return True

    @contextmanager
    def __timeout(self, timeoutSec: int):
        # Register a function to raise a TimeoutError on the signal.
        signal(SIGALRM, self.__raiseTimeout)
        # Schedule the signal to be sent after ``time``.
        alarm(timeoutSec)

        try:
            yield
        except TimeoutError:
            pass
        finally:
            # Unregister the signal so it won't be triggered
            # if the timeout is not reached.
            signal(SIGALRM, SIG_IGN)

    def __raiseTimeout(self, sigNum: int, frame):
        self._onTimeout()
        raise TimeoutError

    def _failWith(self, msg: str):
        """
        Marks the current test as failed with the given message.
        Stores the complete stderr and stdout output from the run.
        """

        self.case.message = msg
        self.case.result = Result.FAILURE
        self.case.stdout = self._loadFullStdout()
        self.case.stderr = self._loadFullStderr()
        self._onFailed()
        printTester("Test {} failed with: {}".format(self.name, msg))
        raise TestFailedError("{} failed.".format(self.name))

    def _timeout(self, msg: str = ""):
        """
        Marks the current test as failed with the given optional message.
        Stores the complete stderr and stdout output from the run.
        Should be called once a test timeout occurred.
        """

        self.case.message = msg
        self.case.result = Result.FAILURE
        self.case.stdout = self._loadFullStdout()
        self.case.stderr = self._loadFullStderr()
        if msg:
            printTester("'{}' triggered a timeout with message: {}".format(self.name, msg))
        else:
            printTester("'{}' triggered a timeout.".format(self.name))

    def _loadFullStdout(self):
        """
        Returns the stout output of the executable.
        """

        filePath: str = self._getStdoutFilePath()
        if path.exists(filePath) and path.isfile(filePath):
            file: TextIOWrapper = open(filePath, "r")
            content: str = file.read()
            file.close()
            return content
        return ""

    def _loadFullStderr(self):
        """
        Returns the stderr output of the executable.
        """

        filePath: str = self._getStderrFilePath()
        if path.exists(filePath) and path.isfile(filePath):
            file: TextIOWrapper = open(filePath, "r")
            content: str = file.read()
            file.close()
            return content
        return ""

    def _initOutputDirectory(self):
        """
        Prepares the output directory for the stderr and stdout files.
        """
        outDir: str = self._getOutputPath()
        if path.exists(outDir) and path.isdir(outDir):
            return
        makedirs(outDir)

    def _getOutputPath(self):
        """
        Returns the output path for temporary stuff like the stderr and stdout files.
        """

        return path.join("/tmp", self.suite.name, self.name)

    def _getStdoutFilePath(self):
        """
        Returns the path of the stdout cache file.
        """

        return path.join(self._getOutputPath(), "stdout.txt")

    def _getStderrFilePath(self):
        """
        Returns the path of the stderr cache file.
        """

        return path.join(self._getOutputPath(), "stderr.txt")

    def _createPWrap(self, cmd: List[str]):
        """
        Crates a new PWrap instance from the given command.
        """

        return PWrap(cmd, self._getStdoutFilePath(), self._getStderrFilePath())

    def _startPWrap(self, pWrap: PWrap):
        """
        Starts the PWrap execution.
        Handels FileNotFoundError if for example the executable was not found or does not exist.
        """

        try:
            pWrap.start()
        except FileNotFoundError as e:
            printTester(str(e))
            self._failWith("File not found for execution. Did compiling fail?")

    @abstractmethod
    def _run(self):
        """
        Implement your test run here.
        """
        pass

    @abstractmethod
    def _onTimeout(self):
        """
        Called once a timeout occurres.
        Should cancel all outstanding actions and free all resources.
        """
        pass

    @abstractmethod
    def _onFailed(self):
        """
        Called once the test failed via "_failWith(msg: str)".
        Should cancel all outstanding actions and free all allocated resources.
        """
        pass