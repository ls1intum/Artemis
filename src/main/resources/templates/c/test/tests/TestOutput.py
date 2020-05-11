from testUtils.AbstractTest import AbstractTest
from time import sleep
from os.path import join
from typing import List, Optional
from testUtils.Utils import PWrap, printTester, studSaveStrComp

class TestOutput(AbstractTest):
    # Our process wrapper instance:
    pWrap: Optional[PWrap]
    # The location of the makefile:
    makefileLocation: str
    # The name of the executable that should get executed:
    executable: str

    def __init__(self, makefileLocation: str, rot: int, input: str, requirements: List[str] = list(), name: str = "TestOutput", executable: str = "rotX.out"):
        super(TestOutput, self).__init__(name, requirements, timeoutSec=10)
        self.makefileLocation = makefileLocation
        self.rot = rot
        self.input = input
        self.executable: str = executable
        self.pWrap = None
    
    def _run(self):
        # Start the program:
        self.pWrap = self._createPWrap([join(".", self.makefileLocation, self.executable)])
        self._startPWrap(self.pWrap)

        # Wait for child beeing ready:
        self.__waitForInput("Enter Rot:")

        # Send rot:
        self.pWrap.writeStdin("{}\n".format(self.rot))
        sleep(0.25)
        self.__waitForInput("Enter text:")

        # Send input text:
        self.pWrap.writeStdin("{}\n".format(self.input))

        # Compare result:
        self.__waitForInput(self.__getExpectedRotX(self.rot, self.input))

        # Wait reading until the programm terminates:
        printTester("Waiting for the programm to terminate...")
        if(not self.pWrap.waitUntilTerminationReading(3)):
            printTester("Programm did not terminate - killing it!")
            pWrap.kill()
            self._failWith("Programm did not terminate at the end.")
        
        # Allways cleanup to make sure all threads get joined:
        self.pWrap.cleanup()

    def __getExpectedRotX(self, rot: int, input: str):
        output: str = ""
        for i in range(0, len(input)):
            if input[i].isalpha():
                if input[i].isupper():
                    output += chr(ord('A') + ((ord(input[i]) - ord('A')) + rot) % 26);
                else:
                    output += chr(ord('a') + ((ord(input[i]) - ord('a')) + rot) % 26);
            else:
                output += input[i]
        return output
    
    def __waitForInput(self, expected: str):
        printTester("Waiting for: '{}'".format(expected))
        while True:
            if self.pWrap.hasTerminated() and not self.pWrap.canReadLineStdout():
                self.__progTerminatedUnexpectedly()
            # Read a single line form the programm output:
            line: str = self.pWrap.readLineStdout()
            # Perform a "student save" compare:
            if studSaveStrComp(expected, line):
                break
            else:
                printTester("Expected '{}' but received '{}'".format(expected, line))
    
    def _onTimeout(self):
        self._terminateProgramm()
    
    def _onFailed(self):
        self._terminateProgramm()
    
    def _terminateProgramm(self):
        if self.pWrap:
            if not self.pWrap.hasTerminated():
                self.pWrap.kill()
            self.pWrap.cleanup()

    def __progTerminatedUnexpectedly(self):
        self._failWith("Program terminated unexpectedly.")