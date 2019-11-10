from testUtils.AbstractTest import AbstractTest
from testUtils.Utils import PWrap
from time import sleep
from subprocess import call
from os import path
from typing import List

class TestLSan(AbstractTest):
    """
    Test case that tries to compile the given program with leak sanitizer enabled.
    All warnings will be treated as errors and compilation will fail.
    Requires "liblsan" to be installed on your system.
    """

    makefileLocation: str
    makeTarget: str
    pWrap: PWrap

    def __init__(self, makefileLocation: str, makeTarget: str = "lsan", requirements: List[str] = list(), name: str = "TestCompileLeak"):
        super(TestLSan, self).__init__(name, requirements, timeoutSec=5)
        self.makefileLocation = makefileLocation
        self.makeTarget = makeTarget

    def _run(self):
        # Start the program:
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