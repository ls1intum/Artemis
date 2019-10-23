from xml.etree import ElementTree as Et
from datetime import timedelta
from enum import Enum

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
    messageInner: str = ""

    def __init__(self, name: str):
        self.name = name

    def toXml(self, suite: Et.Element):
        case: Et.Element = Et.SubElement(suite, "testcase")
        case.set("name", self.name)
        case.set("time", str(self.time.total_seconds()))

        if self.result != Result.SUCCESS:
            result: Et.Element = Et.SubElement(case, self.result.value)
            result.set("message", self.message)
            result.text = self.genErrFailureMessage()

        if self.stdout:
            stdout: Et.Element = Et.SubElement(case, "system-out")
            stdout.text = self.stdout
        if self.stderr:
            stderr: Et.Element = Et.SubElement(case, "system-err")
            stderr.text = self.stderr

    def genErrFailureMessage(self):
        msg: str = self.messageInner

        msg += "\n======================stdout======================\n"
        if self.stdout:
            msg += self.stdout + "\n"
        else:
            msg += "No output on stdout found!\n"

        msg += "\n======================stderr======================\n"
        if self.stderr:
            msg += self.stderr + "\n"
        else:
            msg += "No output on stderr found!\n"

        msg += "\n======================Tester======================\n"
        if self.testerOutput:
            msg += self.testerOutput + "\n"
        else:
            msg += "No tester output found!\n"
        return msg