from datetime import timedelta
from enum import Enum
from xml.etree import ElementTree as Et

from testUtils.Utils import shortenText


class Result(Enum):
    SKIPPED = "skipped"
    ERROR = "error"
    FAILURE = "failure"
    SUCCESS = "success"


class TestCase:
    stdout: str
    stderr: str
    testerOutput: str

    name: str
    time: timedelta
    result: Result
    message: str

    def __init__(self, name: str):
        self.name = name

        self.stdout: str = ""
        self.stderr: str = ""
        self.testerOutput: str = ""
        self.time: timedelta = timedelta()
        self.result: Result = Result.SUCCESS
        self.message: str = ""

    def toXml(self, suite: Et.Element, maxCharsPerOutput=2500):
        case: Et.Element = Et.SubElement(suite, "testcase")
        case.set("name", self.name)
        case.set("time", str(self.time.total_seconds()))

        if self.result != Result.SUCCESS:
            result: Et.Element = Et.SubElement(case, self.result.value)
            result.set("message", self.message)
            result.text = self.genErrFailureMessage()

        if self.stdout:
            stdout: Et.Element = Et.SubElement(case, "system-out")
            stdout.text = shortenText(self.stdout, maxCharsPerOutput) + "\n"
        if self.stderr:
            stderr: Et.Element = Et.SubElement(case, "system-err")
            stderr.text = shortenText(self.stderr, maxCharsPerOutput) + "\n"

    def genErrFailureMessage(self, maxChars=5000):
        oneThird: int = int(maxChars / 3)

        # Limit the stderr output to one third of the available chars:
        stderrMsg: str = "\n" + "stderr".center(50, "=") + "\n"
        if self.stderr:
            stderrMsg += shortenText(self.stderr, oneThird) + "\n"
        else:
            stderrMsg += "No output on stderr found!\n"

        # Limit the stdout output to one third + the unused chars from the stderr output:
        stdoutMsg: str = "\n" + "stdout".center(50, "=") + "\n"
        if self.stdout:
            stdoutMsg += shortenText(self.stdout, oneThird + (oneThird - len(stderrMsg))) + "\n"
        else:
            stdoutMsg += "No output on stdout found!\n"

        # Limit the tester output to one third + the left overs from stderr and stdout:
        testerMsg: str = "\n" + "Tester".center(50, "=") + "\n"
        if self.testerOutput:
            testerMsg += shortenText(self.testerOutput, maxChars - len(testerMsg) - len(stderrMsg) - len(stdoutMsg)) + "\n"
        else:
            testerMsg += "No tester output found!\n"
        return self.message + stdoutMsg + stderrMsg + testerMsg
