"""Exercise the trusted PR coverage init script against a small, real Gradle project.

Run: python3 -m unittest discover -s supporting_scripts/tests -p 'test_sonar_coverage.py'
Requires the repository's Gradle distribution to have been cached already.
"""

import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET


REPOSITORY = Path(__file__).resolve().parents[2]
INIT_SCRIPT = REPOSITORY / ".github/scripts/sonar-pr-coverage.gradle"
REPORT = "build/reports/jacoco/sonar-pr/jacocoTestReport.xml"


class SonarCoverageTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        properties = (REPOSITORY / "gradle/wrapper/gradle-wrapper.properties").read_text()
        version = re.search(r"gradle-([\d.]+)-bin.zip", properties)[1]
        cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "wrapper/dists"
        binaries = list(cache.glob(f"gradle-{version}-bin/*/gradle-{version}/bin/gradle"))
        if not binaries:
            raise unittest.SkipTest(f"Coverage check requires cached Gradle {version}")
        cls.gradle = binaries[0]

    def gradle_run(self, root, *arguments, with_init=True):
        command = [str(self.gradle), "--console=plain", "-p", str(root)]
        if with_init:
            command += ["--init-script", str(INIT_SCRIPT)]
        result = subprocess.run(command + list(arguments), capture_output=True, text=True, check=False, timeout=120)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return result.stdout

    def test_partial_xml_keeps_unexecuted_classes_without_running_other_coverage_tasks(self):
        with tempfile.TemporaryDirectory(prefix="artemis-sonar-coverage-") as directory:
            root = Path(directory)
            (root / "settings.gradle").write_text("rootProject.name = 'partial-coverage-check'\n")
            (root / "build.gradle").write_text("""
plugins { id 'java'; id 'jacoco' }
repositories { mavenCentral() }
jacoco { toolVersion = '0.8.16' }
if (providers.gradleProperty('excludeGenerated').isPresent()) {
    ext.ignoredDirectories = ['**/Ignored_*.class']
}
tasks.register('forbiddenModuleCoverage') { doLast { throw new GradleException('Module coverage ran') } }
tasks.named('jacocoTestReport') { finalizedBy 'forbiddenModuleCoverage' }
tasks.named('jacocoTestCoverageVerification') { doLast { throw new GradleException('Coverage gate ran') } }
tasks.named('test') { doLast { throw new GradleException('Tests ran') } }
def seed = tasks.register('seedCoverage', JavaExec) {
    dependsOn 'compileJava'
    mainClass = 'fixture.Covered'
    classpath = sourceSets.main.runtimeClasspath
}
jacoco.applyTo(seed.get())
seed.configure { jacoco.destinationFile = layout.buildDirectory.file('jacoco/selected.exec').get().asFile }
tasks.register('checkReportFormats') {
    doLast {
        def report = tasks.named('sonarPrCoverage').get()
        assert report.reports.xml.required.get()
        assert !report.reports.html.required.get()
        assert !report.reports.csv.required.get()
    }
}
""")
            sources = root / "src/main/java/fixture"
            sources.mkdir(parents=True)
            (sources / "Covered.java").write_text(
                'package fixture; public class Covered { public static void main(String[] args) { System.out.println("selected"); } }\n'
            )
            (sources / "Untouched.java").write_text("package fixture; public class Untouched { public int value() { return 42; } }\n")
            (sources / "Ignored_Model.java").write_text("package fixture; public class Ignored_Model { public int value() { return 7; } }\n")
            self.gradle_run(root, "seedCoverage", with_init=False)
            execution = root / "build/jacoco/selected.exec"
            self.assertGreater(execution.stat().st_size, 0)
            execution_before = execution.read_bytes()

            dry_run = self.gradle_run(root, "sonarPrCoverage", "--dry-run")
            self.assertEqual(re.findall(r"^:(\S+) SKIPPED$", dry_run, re.MULTILINE), ["compileJava", "sonarPrCoverage"])
            for excluded in (True, False):
                with self.subTest(ignored_directories_available=excluded):
                    arguments = ["sonarPrCoverage", "checkReportFormats"]
                    if excluded:
                        arguments.append("-PexcludeGenerated=true")
                    self.gradle_run(root, *arguments)
                    xml = ET.parse(root / REPORT).getroot()
                    classes = {element.attrib["name"]: element for element in xml.findall(".//class")}
                    covered = classes["fixture/Covered"].find("counter[@type='INSTRUCTION']")
                    untouched = classes["fixture/Untouched"].find("counter[@type='INSTRUCTION']")
                    self.assertGreater(int(covered.attrib["covered"]), 0)
                    self.assertGreater(int(untouched.attrib["missed"]), 0)
                    self.assertEqual(int(untouched.attrib["covered"]), 0)
                    self.assertEqual("fixture/Ignored_Model" in classes, not excluded)
                    self.assertFalse(list((root / "build/reports").rglob("*.html")))
            self.assertEqual(execution.read_bytes(), execution_before)


if __name__ == "__main__":
    unittest.main(verbosity=2)
