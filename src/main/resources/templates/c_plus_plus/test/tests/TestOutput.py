from os.path import join
from typing import List

from testUtils.AbstractProgramTest import AbstractProgramTest
from testUtils.Utils import printTester, studSaveStrComp


class TestOutput(AbstractProgramTest):
    def __init__(self, makefileLocation: str, requirements: List[str] = None, name: str = "TestOutput", executable: str = "helloWorld.out"):
        super(TestOutput, self).__init__(name, makefileLocation, executable, requirements, timeoutSec=10)

    def _run(self):
        # Start the program:
        self.pWrap = self._createPWrap([join(".", self.executionDirectory, self.executable)])
        self._startPWrap(self.pWrap)

        # Wait for child being ready:
        printTester("Waiting for: 'Hello world!'")
        expected: str = "Hello world!"
        while True:
            if self.pWrap.hasTerminated() and not self.pWrap.canReadLineStdout():
                self._progTerminatedUnexpectedly()
            # Read a single line form the program output:
            line: str = self.pWrap.readLineStdout()
            # Perform a "student save" compare:
            if studSaveStrComp(expected, line):
                break
            else:
                printTester(f"Expected '{expected}' but received read '{line}'")

        # Wait reading until the program terminates:
        printTester("Waiting for the program to terminate...")
        if not self.pWrap.waitUntilTerminationReading(3):
            printTester("Program did not terminate - killing it!")
            self.pWrap.kill()
            self._failWith("Program did not terminate at the end.")

        # Always cleanup to make sure all threads get joined:
        self.pWrap.cleanup()
