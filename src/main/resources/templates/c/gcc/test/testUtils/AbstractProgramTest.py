from typing import List, Optional

from testUtils.AbstractTest import AbstractTest
from testUtils.Utils import PWrap


class AbstractProgramTest(AbstractTest):
    """
    A abstract test that every test executing an external program has to inherit from.
    How to:
    1. Inherit from AbstractProgramTest
    2. Override the "_run()" method.
    3. Done
    """

    # Our process wrapper instance:
    pWrap: Optional[PWrap]
    # The location of the executable:
    executionDirectory: str
    # The name of the executable that should get executed:
    executable: str

    def __init__(self, name: str, executionDirectory: str, executable: str, requirements: List[str] = None, timeoutSec: int = -1):
        super(AbstractProgramTest, self).__init__(name, requirements, timeoutSec)
        self.executionDirectory: str = executionDirectory
        self.executable: str = executable
        self.pWrap: Optional[PWrap] = None

    def _onTimeout(self):
        self._terminateProgramm()

    def _onFailed(self):
        self._terminateProgramm()

    def _terminateProgramm(self):
        if self.pWrap:
            if not self.pWrap.hasTerminated():
                self.pWrap.kill()
            self.pWrap.cleanup()

    def _progTerminatedUnexpectedly(self):
        self._failWith("Program terminated unexpectedly.")
