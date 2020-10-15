from testUtils.AbstractProgramTest import AbstractProgramTest
from os.path import join
from typing import List
from testUtils.Utils import printTester, studSaveStrComp


class TestOutput(AbstractProgramTest):
    files: List[str]

    def __init__(self, makefileLocation: str, requirements: List[str] = None, files: List[str] = list(), name: str = "TestOutput", executable: str = "hexdump.out"):
        super(TestOutput, self).__init__(name, makefileLocation,
                                         executable, requirements, timeoutSec=-1)
        self.files: List[str] = files

    def _run(self):
        # Start the program:
        self.pWrap = self._createPWrap(
            [join(".", self.executionDirectory, self.executable)] + self.files)
        self._startPWrap(self.pWrap)

        i: int = 0
        expected: List[str] = self._getExpected()
        while True:
            if self.pWrap.hasTerminated():
                if len(self.files) == 0:
                    if self.pWrap.canReadLineStdout():
                        self._failWith(
                            "Programm generated output although no parameter was given")
                    else:
                        break
                elif not self.pWrap.canReadLineStdout():
                    self.__progTerminatedUnexpectedly()

            # Read a single line form the programm output:
            line: str = self.pWrap.readLineStdout()
            if not line and self.pWrap.hasTerminated() and (len(self.files) <= 0):
                break

            # exact match required for full lines (i.e. 16 bytes in buffer to be dumped)
            if expected[i] == line:
                i += 1
                if i > len(expected) - 1:
                    break
                continue
            # trailing whitespace is okay for non-full lines only
            elif len(expected[i]) < 76:
                if line.rstrip() == expected[i].rstrip():
                    i += 1
                    if i > len(expected) - 1:
                        break
                    continue
                else:
                    printTester("Expected '{}' with length {} but received '{}' with length {}".format(
                        expected[i], len(expected[i]), line, len(line)))
                    self._failWith("Output did not match")
            else:
                printTester("Expected '{}' but received '{}'".format(
                    expected[i], line))
                self._failWith("Output did not match")

        # Wait reading until the programm terminates:
        printTester("Waiting for the programm to terminate...")
        if not self.pWrap.waitUntilTerminationReading(3):
            printTester("Programm did not terminate - killing it!")
            self.pWrap.kill()
            self._failWith("Programm did not terminate at the end.")

        # Always cleanup to make sure all threads get joined:
        self.pWrap.cleanup()

    def _getExpected(self):
        expected: List[str] = list()
        for file in self.files:
            with open(file, "rb") as f:
                offset = 0
                buf = f.read(16)
                while len(buf) > 0:
                    line = "{:06x} :".format(offset)
                    for char in buf:
                        line += " {:02x}".format(char)
                    line += "   " * (16 - len(buf) + 1)
                    for char in buf:
                        if 0x20 <= char <= 0x7e:
                            line += "{:c}".format(char)
                        else:
                            line += "."
                    line += "\n"
                    offset += 16
                    expected.append(line)
                    buf = f.read(16)
            expected.append("\n")
        return expected[:-1]
