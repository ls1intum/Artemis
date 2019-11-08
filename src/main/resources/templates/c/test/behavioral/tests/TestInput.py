from testUtils.AbstractTest import AbstractTest
from time import sleep
from psutil import Process, NoSuchProcess
from typing import List, Optional
from re import search
from os.path import join
from testUtils.Utils import PWrap, printTester, studSaveStrComp

class TestInput(AbstractTest):
    # Our process wrapper instance:
    pWrap: Optional[PWrap]
    # The location of the makefile:
    makefileLocation: str
    # The name of the executable that should get executed:
    executable: str
    # How many threads should be created:
    count: int

    def __init__(self, makefileLocation: str, count: int, requirements: List[str] = list(), name: str = "TestInput", executable: str = "main.out"):
        super(TestInput, self).__init__(name, requirements, timeoutSec=20)
        self.makefileLocation = makefileLocation
        self.count = count
        self.executable: str = executable
        self.pWrap = None

    def _run(self):
        # Start the program:
        self.pWrap = self._createPWrap([join(".", self.makefileLocation, self.executable)])
        self._startPWrap(self.pWrap)

        # Wait to make sure the child processes has started:
        sleep(0.5)
        if self.pWrap.hasTerminated():
            self.__progTerminatedUnexpectedly()

        # Wait for child beeing ready:
        printTester("Waiting for: 'Enter process count:'")
        while True:
            if self.pWrap.hasTerminated():
                self.__progTerminatedUnexpectedly()
            if studSaveStrComp("Enter process count:", self.pWrap.readLineStdout()):
                self.pWrap.writeStdin("{}\n".format(self.count))
                break

        # Give the programm some time to start it's child processes:
        sleep(0.25)
        printTester("Waiting for: \"I'm your child! PID: %i, PPID: %i\\n\"")
        for i in range(0, self.count):
            while True:
                if self.pWrap.hasTerminated():
                    self.__progTerminatedUnexpectedly()
                if self.__matchChildHello(self.pWrap.readLineStdout()):
                    break

        # Get all child process PIDs:
        pids: List[int] = self.__getChildPids()
        # Send 'END' to terminate processes:
        printTester("Sending 'END's...")
        for i in range(0, self.count):
            if self.pWrap.hasTerminated():
                self.__progTerminatedUnexpectedly()

            # Check child process count:
            self.__testChildProcessCount(self.count - i)

            self.pWrap.writeStdin("END\n")
            sleep(0.25)

            printTester("Waiting for: \"Child with PID %i terminated.\\n\"")
            while True:
                if self.pWrap.hasTerminated() and not self.pWrap.canReadLineStdout():
                    self.__progTerminatedUnexpectedly()
                if self.__matchChildBye(self.pWrap.readLineStdout(), pids):
                    break

        # Check if all children terminated:
        self.__testChildProcessCount(0)

        # Wait reading until the programm terminates:
        printTester("Waiting for the programm to terminate...")
        if(not self.pWrap.waitUntilTerminationReading(3)):
            printTester("Programm did not terminate - killing it!")
            self.pWrap.kill()

        if not self.pWrap.hasTerminated():
            self._failWith("Programm did not terminate at the end.")

        # Allways cleanup to make sure all threads get joined:
        self.pWrap.cleanup()

    def _onTimeout(self):
        self._terminateProgramm()

    def _onFailed(self):
        self._terminateProgramm()

    def _terminateProgramm(self):
        if self.pWrap:
            if not self.pWrap.hasTerminated():
                self.pWrap.kill()
            self.pWrap.cleanup()

    def __getChildrenCount(self):
        pid = self.pWrap.getPID()
        # If the process has terminated, the process creation will fail:
        try:
            process: Process = Process(pid)
        except NoSuchProcess:
            return 0
        count: int = len(process.children())
        printTester("Parent process has PID: " + str(pid) + " and " + str(count) + " child processes.")
        return count

    def __testChildProcessCount(self, expected: int):
        count: int = self.__getChildrenCount()
        if count != expected:
            self._failWith("Expected " + str(expected) +" child processes but there are only " + str(count))

    def __matchChildHello(self, line: str):
        # Get PPID:
        ppid = self.pWrap.getPID()
        try:
            process: Process = Process(ppid)
        except NoSuchProcess:
            self.__progTerminatedUnexpectedly()

        # Get child PID:
        pidRegex = r"\D*(\d+)\D*(" + str(ppid) + r")"
        m = search(pidRegex, line)
        if m is None:
            printTester("No PID found in string: '{}'".format(line))
            return False
        childPid = int(m.group(1)) # Child PID

        # Check if string read matches:
        expected: str = "I'm your child! PID: {}, PPID: {}".format(childPid, ppid)
        if not studSaveStrComp(expected, line):
            printTester("Expected: '{}' but received: '{}'".format(expected, line))
            return False

        # Check if process exists for the child PID:
        if not self.__existsChildWithPid(childPid):
            printTester("No child process with PID {} exists".format(childPid))
            return False
        return True

    def __existsChildWithPid(self, childPid: int):
        pid = self.pWrap.getPID()
        try:
            process: Process = Process(pid)
        except NoSuchProcess:
            self.__progTerminatedUnexpectedly()
        for p in process.children():
            if p.pid == childPid:
                return True
        return False

    def __getChildPids(self):
        pids: List[int] = list()
        pid = self.pWrap.getPID()
        try:
            process: Process = Process(pid)
        except NoSuchProcess:
            self.__progTerminatedUnexpectedly()
        for p in process.children():
            pids.append(p.pid)
        return pids

    def __matchChildBye(self, line: str, pids: List[int]):
        # Get child PID:
        pidRegex = r"\D*(\d+)\D*"
        m = search(pidRegex, line)
        if m is None:
            printTester("No PID found in string: '{}'".format(line))
            return False
        childPid = int(m.group(1)) # Child PID

        # Check if the processes PID is one of those that said hello:
        if not childPid in pids:
            self._failWith("PID {} unknown. We are only aware of: {}".format(childPid, str(pids)))
        pids.remove(childPid)

        # Check if string read matches:
        expected: str = "Child with PID {} terminated.".format(childPid)
        if not studSaveStrComp(expected, line):
            printTester("Expected: '{}' but received: '{}'".format(expected, line))
            return False
        return True

    def __progTerminatedUnexpectedly(self):
        self._failWith("Program terminated unexpectedly.")