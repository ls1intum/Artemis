from xml.etree import ElementTree as Et
from datetime import timedelta
from enum import Enum
from testUtils.Utils import shortenText

class Result(Enum):
    SKIPPED = "skipped"
    ERROR = "error"
    FAILURE = "failure"
    SUCCESS = "success"

class TestCase:
    stdout: str = ""
    stderr: str = ""
    testerOutput: str = ""

    name: str = ""
    time: timedelta = timedelta()
    result: Result = Result.SUCCESS
    message: str = ""

    def __init__(self, name: str):
        self.name = name

    def toXml(self, suite: Et.Element, maxCharsPerOutput=2000):
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

    def genErrFailureMessage(self, maxCharsPerOutput=2000):
        msg: str = self.message

        msg += "\n"+"stdout".center(50, "=")+"\n"
        if self.stdout:
            msg += shortenText(self.stdout, maxCharsPerOutput) + "\n"
        else:
            msg += "No output on stdout found!\n"

        msg += "\n"+"stderr".center(50, "=")+"\n"
        if self.stderr:
            msg += shortenText(self.stderr, maxCharsPerOutput) + "\n"
        else:
            msg += "No output on stderr found!\n"

        msg += "\n"+"Tester".center(50, "=")+"\n"
        if self.testerOutput:
            msg += shortenText(self.testerOutput, maxCharsPerOutput * 2) + "\n"
        else:
            msg += "No tester output found!\n"
        return msg