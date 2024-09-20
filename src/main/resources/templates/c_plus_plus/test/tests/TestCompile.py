from typing import List

from testUtils.AbstractProgramTest import AbstractProgramTest


class TestCompile(AbstractProgramTest):
    """
    Test case that tries to compile the given program with any compiler optimization disabled.
    Most compiler warnings are enabled but aren't treated as errors.
    """

    target: str

    def __init__(
        self,
        buildDir: str,
        target: str = "all",
        requirements: List[str] = None,
        name: str = "TestCompile",
    ):
        super(TestCompile, self).__init__(
            name, buildDir, "cmake", requirements, timeoutSec=10
        )
        self.target = target

    def _run(self):
        # Build all targets:
        self.pWrap = self._createPWrap([self.executable, "--build", self.executionDirectory, "--target", self.target])
        self._startPWrap(self.pWrap)

        self.pWrap.waitUntilTerminationReading()

        retCode: int = self.pWrap.getReturnCode()
        if retCode != 0:
            self._failWith(
                f"Build for directory {str(self.executionDirectory)} failed. Returncode is {retCode}."
            )

        # Always cleanup to make sure all threads get joined:
        self.pWrap.cleanup()
