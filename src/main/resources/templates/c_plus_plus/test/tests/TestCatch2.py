from os.path import join
from typing import List, Optional
from xml.etree import ElementTree as Et

from testUtils.AbstractProgramTest import AbstractProgramTest
from testUtils.Utils import printTester


class TestCatch2(AbstractProgramTest):
    def __init__(self, location: str, executable: str, requirements: List[str] = None, name: Optional[str] = None):
        super(TestCatch2, self).__init__(name or f"TestCatch2({executable})", location, executable, requirements, timeoutSec=10)

    def _run(self):
        # Start the program:
        outputFilename = f"result-{self.executable}.xml"
        self.pWrap = self._createPWrap([join(".", self.executable), "--success", "--reporter", f"JUnit::out={outputFilename}", "--reporter", "console::out=-::colour-mode=none"], self.executionDirectory)
        self._startPWrap(self.pWrap)
        self.pWrap.waitUntilTerminationReading()

        retCode: int = self.pWrap.getReturnCode()
        # parse XML output and append it to the results
        try:
            catchXmlRoot: Et.Element = Et.parse(join(self.executionDirectory, outputFilename))
            catchXmlSuite: Et.Element = catchXmlRoot.find("testsuite")
            self.additionalSuites.append(catchXmlSuite)
            printTester(f"Appended {catchXmlSuite}")
        except Exception as E:
            printTester(f"Exception {str(E)}")
            pass

        if retCode != 0:
            self._failWith(
                f"Test for {self.executable} failed."
            )

        # Always cleanup to make sure all threads get joined:
        self.pWrap.cleanup()
