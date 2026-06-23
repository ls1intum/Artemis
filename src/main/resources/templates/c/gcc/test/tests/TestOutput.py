from os.path import join
from typing import List

from testUtils.AbstractProgramTest import AbstractProgramTest
from testUtils.Utils import printTester, studSaveStrComp


class TestOutput(AbstractProgramTest):
    def __init__(self, makefileLocation: str, requirements: List[str] = None, name: str = "TestOutput", executable: str = "helloWorld.out"):
        # 30s overall (was 10s): sanitizer-instrumented binaries (asan.out / ubsan.out /
        # lsan.out) perform extra teardown at exit (leak scan, sanitizer cleanup). On a
        # contended CI host where several builds share the CPU, that teardown — and the
        # in-loop termination wait below — can run past the old 10s budget, killing a correct
        # program and dropping a single test (e.g. 87.5% -> 75%). The larger budget only
        # bounds a genuinely hung program; a correct one still exits long before it elapses.
        super(TestOutput, self).__init__(name, makefileLocation, executable, requirements, timeoutSec=30)

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

        # Wait reading until the program terminates. 15s (was 3s): once "Hello world!" has
        # been read the program is expected to exit promptly, but a sanitizer build's
        # exit-time leak scan on a CPU-starved CI host can take several seconds. 3s was too
        # tight under multi-node load and intermittently killed a correct sanitizer binary;
        # 15s comfortably covers the slow exit and still stays inside the 30s test budget.
        if not self.pWrap.waitUntilTerminationReading(15):
            printTester("Program did not terminate - killing it!")
            self.pWrap.kill()
            self._failWith("Program did not terminate at the end.")

        # Always cleanup to make sure all threads get joined:
        self.pWrap.cleanup()
