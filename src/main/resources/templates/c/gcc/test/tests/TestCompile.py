from typing import List

from testUtils.AbstractProgramTest import AbstractProgramTest


class TestCompile(AbstractProgramTest):
    """
    Test case that tries to compile the given program with any compiler optimization disabled.
    Most compiler warnings are enabled but aren't treated as errors.
    """

    makeTarget: str

    def __init__(self, makefileLocation: str, makeTarget: str = "main", requirements: List[str] = None, name: str = "TestCompile"):
        super(TestCompile, self).__init__(name, makefileLocation, "make", requirements, timeoutSec=5)
        self.makeTarget = makeTarget

    def _run(self):
        # Call the makefile with target "main":
        self.pWrap = self._createPWrap([self.executable, "-C", self.executionDirectory, self.makeTarget])
        self._startPWrap(self.pWrap)

        self.pWrap.waitUntilTerminationReading()

        retCode: int = self.pWrap.getReturnCode()
        if retCode != 0:
            self._failWith(f"Make for directory {str(self.executionDirectory)} failed. Returncode is {retCode}.")

        # Always cleanup to make sure all threads get joined:
        self.pWrap.cleanup()
