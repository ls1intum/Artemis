from typing import List

from testUtils.AbstractProgramTest import AbstractProgramTest


class TestGccStaticAnalysis(AbstractProgramTest):
    """
    Test case that tries to compile the given program with gcc static analysis enabled.
    All warnings will be treated as errors and compilation will fail.
    Requires "gcc" >= 10.0 to be installed on your system.
    """

    makeTarget: str

    def __init__(self, executionDirectory: str, makeTarget: str = "staticAnalysis", requirements: List[str] = None,
                 name: str = "TestGccStaticAnalysis"):
        super(TestGccStaticAnalysis, self).__init__(
            name, executionDirectory, "make", requirements, timeoutSec=5)
        self.makeTarget = makeTarget

    def _run(self):
        # Start the program:
        self.pWrap = self._createPWrap(
            [self.executable, "-C", self.executionDirectory, self.makeTarget])
        self._startPWrap(self.pWrap)

        self.pWrap.waitUntilTerminationReading()

        retCode: int = self.pWrap.getReturnCode()
        if retCode != 0:
            self._failWith(
                f"Make for directory {str(self.executionDirectory)} failed. Returncode is {retCode}.")

        # Always cleanup to make sure all threads get joined:
        self.pWrap.cleanup()
