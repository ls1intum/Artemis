from testUtils.AbstractTest import AbstractTest
from testUtils.Utils import PWrap
from time import sleep
from subprocess import call
from os import path
from typing import List

class TestCompile(AbstractTest):
    """
    Test case that tries to compile the given program with any compiler optimization disabled.
    Most compiler warnings are enabled but aren't treated as errors.
    """

    makefileLocation: str
    makeTarget: str
    pWrap: PWrap


    def __init__(self, makefileLocation: str, makeTarget: str = "main", requirements: List[str] = list(), name: str = "TestCompile"):
        super(TestCompile, self).__init__(name, requirements, timeoutSec=5)
        self.makefileLocation = makefileLocation
        self.makeTarget = makeTarget

    def _run(self):
        # Call the makefile with target "main":
        self.pWrap = self._createPWrap(["make", "-C", self.makefileLocation, self.makeTarget])
        self._startPWrap(self.pWrap)

        self.pWrap.waitUntilTerminationReading()

        retCode: int = self.pWrap.getReturnCode()
        if retCode != 0:
            self._failWith("Make for directory {} failed. Returncode is {}.".format(str(self.makefileLocation), retCode))

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