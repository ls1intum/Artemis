from typing import List
import shutil
import os.path

from testUtils.AbstractProgramTest import AbstractProgramTest


class TestConfigure(AbstractProgramTest):
    """
    Test case that runs CMake to configure the build
    """

    buildDir: str
    extraFlags: List[str]

    def __init__(
        self,
        location: str,
        buildDir: str,
        extraFlags: List[str] | None = None,
        requirements: List[str] | None = None,
        name: str = "TestConfigure",
    ) -> None:
        super().__init__(
            name, location, "cmake", requirements, timeoutSec=10
        )
        self.buildDir = buildDir
        self.extraFlags = extraFlags or []

    def _run(self) -> None:
        if os.path.exists(self.buildDir):
            shutil.rmtree(self.buildDir)
        # Call CMake to configure the project:
        self.pWrap = self._createPWrap(
            [self.executable, "-S", self.executionDirectory, "-B", self.buildDir,
             *self.extraFlags]
        )
        self._startPWrap(self.pWrap)

        self.pWrap.waitUntilTerminationReading()

        retCode: int = self.pWrap.getReturnCode()
        if retCode != 0:
            self._failWith(
                f"CMake for directory {self.executionDirectory} failed. Returncode is {retCode}."
            )

        # Always cleanup to make sure all threads get joined:
        self.pWrap.cleanup()
