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

        # ANSI-Farbcodes aus den Attributen und Texten entfernen
        for element in suiteXml.iter():
            for key, value in element.attrib.items():
                if isinstance(value, str):
                    element.attrib[key] = self.strip_ansi_codes(value)
            if element.text:
                element.text = self.strip_ansi_codes(element.text)

        root: Et.Element = Et.Element("testsuites")
        root.append(suiteXml)
        root.extend(self.additionalSuites)
        tree: Et.ElementTree = Et.ElementTree(root)
        self.createOutputPath(outputPath)
        tree.write(outputPath, xml_declaration=True)
        chmod(outputPath, 0o644)

    @staticmethod
    def strip_ansi_codes(text):
        """Entferne ANSI-Farbcodes aus dem Text"""
        ansi_escape = re.compile(r'\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])')
        return ansi_escape.sub('', text)

    @staticmethod
    def createOutputPath(outputPath: str) -> None:
        paths: Tuple[str, str] = path.split(outputPath)
        if paths[0] and not path.exists(paths[0]):
            # Prevent others from writing in this folder:
            makedirs(paths[0], mode=0o755)
