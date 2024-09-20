from os import chmod, makedirs, path
from typing import Tuple, List
from xml.etree import ElementTree as Et

from testUtils.junit.TestSuite import TestSuite


# JUnit format: https://github.com/junit-team/junit5/blob/master/platform-tests/src/test/resources/jenkins-junit.xsd
class Junit:
    suite: TestSuite
    additionalSuites: List[Et.Element]

    def __init__(self, suite: TestSuite, additionalSuites: List[Et.Element]):
        self.suite = suite
        self.additionalSuites = additionalSuites

    def toXml(self, outputPath: str):
        suiteXml: Et.Element = self.suite.toXml()
        root: Et.Element = Et.Element("testsuites")
        root.append(suiteXml)
        root.extend(self.additionalSuites)
        tree: Et.ElementTree = Et.ElementTree(root)
        self.createOutputPath(outputPath)
        tree.write(outputPath, xml_declaration=True)
        # Ensure nobody can edit our results:
        chmod(outputPath, 0o744)

    @staticmethod
    def createOutputPath(outputPath: str):
        paths: Tuple[str, str] = path.split(outputPath)
        if paths[0] and not path.exists(paths[0]):
            # Prevent others from writing in this folder:
            makedirs(paths[0], mode=0o755)
