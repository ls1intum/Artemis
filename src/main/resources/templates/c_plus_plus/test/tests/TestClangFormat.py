import difflib
from os.path import join
from typing import List

from testUtils.AbstractProgramTest import AbstractProgramTest


class TestClangFormat(AbstractProgramTest):
    """
    Test case that checks code formatting using clang-format
    """

    projectRoot: str
    filePath: str

    def __init__(
        self,
        projectRoot: str,
        filePath: str,
        requirements: List[str] | None = None,
        name: str = "TestClangFormat",
    ) -> None:
        super().__init__(
            name, projectRoot, "clang-format", requirements, timeoutSec=5,
        )
        self.projectRoot = projectRoot
        self.filePath = filePath

    def _run(self) -> None:
        self.pWrap = self._createPWrap(
            [self.executable, self.filePath], self.executionDirectory
        )
        self._startPWrap(self.pWrap)

        self.pWrap.waitUntilTerminationReading()

        retCode: int = self.pWrap.getReturnCode()
        if retCode != 0:
            self._failWith(
                f"clang-format for file {self.filePath} failed. Returncode is {retCode}."
            )

        formatted = self._loadFullStdout().splitlines()
        with open(join(self.projectRoot, self.filePath)) as file:
            original = file.read().splitlines()
        if original[-1] != "":
            original.append("")
        if formatted[-1] != "":
            formatted.append("")
        if "\n".join(formatted) != "\n".join(original):
            diffstr = "\n".join(difflib.unified_diff(original, formatted))
            self._failWith(
                f"File {self.filePath} should be formatted, diff is\n{diffstr}"
            )

        # Always cleanup to make sure all threads get joined:
        self.pWrap.cleanup()
