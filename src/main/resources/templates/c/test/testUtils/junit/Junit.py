from testUtils.junit.TestSuite import TestSuite
from xml.etree import ElementTree as Et
from os import makedirs, path, chmod
from typing import Tuple


# JUnit format: https://github.com/junit-team/junit5/blob/master/platform-tests/src/test/resources/jenkins-junit.xsd
class Junit:
    suite: TestSuite

    def __init__(self, suite: TestSuite):
        self.suite = suite

    def toXml(self, outputPath: str):
        suiteXml: Et.Element = self.suite.toXml()
        tree: Et.ElementTree = Et.ElementTree(suiteXml)
        self.createOutputPath(outputPath)
        tree.write(outputPath, xml_declaration=True)
        # Ensure nobody can edit our results:
        chmod(outputPath, 0o744)

    def createOutputPath(self, outputPath: str):
        paths: Tuple[str, str] = path.split(outputPath)
        if paths[0] and not path.exists(paths[0]):
            # Prevent others from writing in this folder:
            makedirs(paths[0], mode=0o755)
