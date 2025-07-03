from os import chmod, makedirs, path
from typing import Tuple, List
from xml.etree import ElementTree as Et
import re

from testUtils.junit.TestSuite import TestSuite


# JUnit format: https://github.com/junit-team/junit5/blob/master/platform-tests/src/test/resources/jenkins-junit.xsd
class Junit:
    suite: TestSuite
    additionalSuites: List[Et.Element]

    def __init__(self, suite: TestSuite, additionalSuites: List[Et.Element]) -> None:
        self.suite = suite
        self.additionalSuites = additionalSuites

    def toXml(self, outputPath: str) -> None:
        suiteXml: Et.Element = self.suite.toXml()

        # remove ANSI colour codes
        for element in suiteXml.iter():
            for key, value in element.attrib.items():
                if isinstance(value, str):
                    element.attrib[key] = self.strip_ansi_codes(value)
            if element.text:
                element.text = self.strip_ansi_codes(element.text)

        root: Et.Element = Et.Element("testsuites")
        root.append(suiteXml)
        root.extend(self.additionalSuites)
        # add another empty test suite
        # to make sure the tests have consistent names
        # independent of whether there are additional suites or not
        root.append(TestSuite("empty-suite").toXml())
        tree: Et.ElementTree = Et.ElementTree(root)
        self.createOutputPath(outputPath)
        tree.write(outputPath, xml_declaration=True)
        chmod(outputPath, 0o644)

    @staticmethod
    def strip_ansi_codes(text: str) -> str:
        """Removes ANSI colour codes from the text"""
        ansi_escape_pattern = re.compile(
            r'(?:\x1B|\x9B)'  # ESC or CSI
            r'[\[\]()#;?]*'  # Intermediate bytes
            r'(?:[0-9]{1,4}(?:;[0-9]{0,4})*)?'  # Parameter bytes
            r'[0-9A-ORZcf-nqry=><~]')  # Final byte

        # Filter any remaining non-ASCII characters that may be remaining
        # from escape patterns that were truncated due to stdout limits
        # from https://stackoverflow.com/questions/730133/what-are-invalid-characters-in-xml
        nonprintable_pattern = re.compile(
            r"[^\x09\x0A\x0D\x20-\uD7FF\uE000-\uFFFD\u10000-\u10FFFF]"
        )
        return nonprintable_pattern.sub("", ansi_escape_pattern.sub("", text))

    @staticmethod
    def createOutputPath(outputPath: str) -> None:
        paths: Tuple[str, str] = path.split(outputPath)
        if paths[0] and not path.exists(paths[0]):
            # Prevent others from writing in this folder:
            makedirs(paths[0], mode=0o755)
