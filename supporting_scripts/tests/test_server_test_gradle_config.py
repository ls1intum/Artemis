"""Check real Gradle test filtering without compiling Artemis or resolving dependencies.

Uses an already cached wrapper distribution only; otherwise skips the offline check.
Run: python3 -m unittest discover -s supporting_scripts/tests -p 'test_server_test_gradle_config.py'
"""

import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest


REPOSITORY = Path(__file__).resolve().parents[2]


def architecture_test_classes(test_root):
    """Follow inherited JUnit architecture tags, including intermediate test bases."""
    sources = {}
    for source in test_root.rglob("*.java"):
        name = str(source.relative_to(test_root).with_suffix("")).replace("/", ".")
        sources[name] = source.read_text()
    tagged = {name for name, code in sources.items() if re.search(r'@Tag\(\s*"ArchitectureTest"\s*\)', code)}
    parents = {}
    for name, code in sources.items():
        declaration = re.search(r"\bclass\s+\w+(?:\s*<[^{}]+>)?\s+extends\s+([\w.]+)", code)
        if not declaration:
            continue
        parent = declaration[1]
        imports = re.findall(r"\bimport\s+([\w.]+)\s*;", code)
        qualified = next((value for value in imports if value.endswith("." + parent)), name.rpartition(".")[0] + "." + parent)
        parents[name] = parent if parent in sources else qualified
    while True:
        inherited = {name for name, parent in parents.items() if parent in tagged} - tagged
        if not inherited:
            return sorted(tagged)
        tagged.update(inherited)


class ArchitectureInventoryTest(unittest.TestCase):
    def test_inherited_architecture_tags_cross_package_boundaries(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, code in {
                "core/TaggedBase.java": '@Tag("ArchitectureTest") class TaggedBase {}',
                "core/Intermediate.java": 'class Intermediate extends TaggedBase {}',
                "leaf/OutsideConventionTest.java": 'import core.Intermediate; class OutsideConventionTest extends Intermediate {}',
                "leaf/UnrelatedTest.java": 'class UnrelatedTest {}',
            }.items():
                destination = root / name
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_text(code)
            self.assertEqual(architecture_test_classes(root), ["core.Intermediate", "core.TaggedBase", "leaf.OutsideConventionTest"])


class ServerTestGradleConfigTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        properties = (REPOSITORY / "gradle/wrapper/gradle-wrapper.properties").read_text()
        version = re.search(r"gradle-([\d.]+)-bin.zip", properties)[1]
        cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "wrapper/dists"
        binaries = list(cache.glob(f"gradle-{version}-bin/*/gradle-{version}/bin/gradle"))
        if not binaries:
            raise unittest.SkipTest(f"Offline filtering check requires cached Gradle {version}")
        cls.gradle = binaries[0]

    def test_default_module_and_explicit_tag_filtering(self):
        with tempfile.TemporaryDirectory(prefix="artemis-gradle-selection-") as directory:
            root = Path(directory)
            (root / "settings.gradle").write_text("rootProject.name = 'server-selection-check'\n")
            (root / "gradle").mkdir()
            (root / "gradle/jacoco.gradle").write_text("")
            source = root / "src/test/java/de/tum/cit/aet/artemis/alpha/ChosenTest.java"
            source.parent.mkdir(parents=True)
            source.write_text("class ChosenTest {}")
            production_config = str(REPOSITORY / "gradle/test.gradle").replace("'", "\\'")
            # Guard future globally tagged tests as well as today's explicit scanner examples.
            architecture_classes = architecture_test_classes(REPOSITORY / "src/test/java")
            self.assertGreater(len(architecture_classes), 10)
            inventory = ",".join(f"'{name}'" for name in sorted(architecture_classes))
            (root / "build.gradle").write_text("""
plugins { id 'java' }
configurations { mockitoAgent }
ext.envProperties = [:]
apply from: 'PRODUCTION_CONFIG'

tasks.register('verifySelection') {
    doLast {
        def selected = tasks.named('test', Test).get()
        if (System.getProperty('includeTags')) {
            assert selected.options.includeTags.contains('nightly-lti')
            assert !selected.options.excludeTags.contains('nightly-lti')
        } else {
            assert selected.options.excludeTags.contains('nightly-lti')
            assert selected.excludes.contains('**/*IT*')
            assert selected.excludes.contains('**/*IntTest*')
        }
        if (System.getProperty('artemis.test.classes')) {
            assert !runAllTests
            assert includedModules.size() == 0 : 'Class selection must not pretend module coverage is complete'
            def matcher = new org.gradle.api.internal.tasks.testing.filter.TestSelectionMatcher(
                new org.gradle.api.internal.tasks.testing.filter.TestFilterSpec(
                    selected.filter.includePatterns, selected.filter.excludePatterns, [] as Set))
            assert matcher.matchesTest('de.tum.cit.aet.artemis.alpha.ChosenTest', 'method')
            assert matcher.matchesTest('de.tum.cit.aet.artemis.alpha.ChosenTest$Nested', 'method')
            assert !matcher.matchesTest('de.tum.cit.aet.artemis.alpha.UnrelatedTest', 'method')
            assert !matcher.matchesTest('de.tum.cit.aet.artemis.core.service.UnrelatedTest', 'method')
            [ARCHITECTURE_INVENTORY].each { name ->
                assert matcher.matchesTest(name, 'method') : "Missing tagged architecture check: $name"
            }
            for (name in ['core.authorization.AuthorizationGeneralAndIndependentEndpointTest',
                          'core.service.featureusage.FeatureUsageAnnotationTest',
                          'core.service.distributed.DistributedDataSurfaceTest',
                          'videosource.service.GocastLoggingSafetyTest']) {
                assert matcher.matchesTest("de.tum.cit.aet.artemis.$name", 'method') : "Missing global check: $name"
            }
        }
        if (System.getProperty('includeModules')) {
            assert includedModules.toList() == ['alpha'] : 'Global test filters must not expand the requested coverage modules'
            def matcher = new org.gradle.api.internal.tasks.testing.filter.TestSelectionMatcher(
                new org.gradle.api.internal.tasks.testing.filter.TestFilterSpec(
                    selected.filter.includePatterns, selected.filter.excludePatterns, [] as Set))
            assert matcher.matchesTest('de.tum.cit.aet.artemis.alpha.RootTest', 'method')
            assert matcher.matchesTest('de.tum.cit.aet.artemis.alpha.service.NestedTest', 'method')
            assert matcher.matchesTest('de.tum.cit.aet.artemis.shared.RootTest', 'method')
            assert !matcher.matchesTest('de.tum.cit.aet.artemis.beta.OtherTest', 'method')
            [ARCHITECTURE_INVENTORY].each { name ->
                assert matcher.matchesTest(name, 'method') : "Missing tagged architecture check: $name"
            }
            // A leaf controller is discovered by these scanners, without a Java import edge.
            for (name in [
                'core.authorization.AuthorizationArchitectureTest',
                'core.authorization.AuthorizationGeneralAndIndependentEndpointTest',
                'core.service.featureusage.FeatureUsageAnnotationTest',
                'core.service.featureusage.FeatureUsageInventoryTest',
                'core.service.distributed.DistributedDataSurfaceTest',
                'beta.architecture.BetaArchitectureTest',
                'localci.architecture.LocalCITestArchitectureTest',
                'videosource.service.GocastLoggingSafetyTest'
            ]) {
                assert matcher.matchesTest("de.tum.cit.aet.artemis.$name", 'method') : "Missing global check: $name"
            }

        }
        println('Verified server test filters')
    }
}
""".replace("PRODUCTION_CONFIG", production_config).replace("ARCHITECTURE_INVENTORY", inventory))
            for arguments in ([], ["-DincludeModules=alpha"], ["-DincludeTags=nightly-lti"],
                              ["-Dartemis.test.classes=de.tum.cit.aet.artemis.alpha.ChosenTest"]):
                with self.subTest(arguments=arguments):
                    result = subprocess.run(
                        [str(self.gradle), "--offline", "--no-daemon", "--console=plain",
                         "-p", str(root), "verifySelection", *arguments],
                        capture_output=True, text=True, check=False, timeout=90,
                    )
                    self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                    self.assertIn("Verified server test filters", result.stdout)
            invalid = (
                "", "*", "de.tum.cit.aet.artemis.alpha.*", "de.tum.cit.aet.artemis.alpha.MissingTest",
                "de.tum.cit.aet.artemis.alpha.ChosenTest,", "de.tum.cit.aet.artemis.alpha.ChosenTest.method",
            )
            conflicting = (["-DincludeModules=alpha"], ["-DincludeTags=ArchitectureTest"], ["-Dartemis.test.bucket=BucketIndependent"])
            for arguments in ([f"-Dartemis.test.classes={value}"] for value in invalid):
                with self.subTest(invalid=arguments):
                    result = subprocess.run([str(self.gradle), "--offline", "--no-daemon", "--console=plain",
                                             "-p", str(root), "verifySelection", *arguments],
                                            capture_output=True, text=True, check=False, timeout=90)
                    self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
                    self.assertIn("artemis.test.classes", result.stdout + result.stderr)
            for extra in conflicting:
                with self.subTest(conflicting=extra):
                    result = subprocess.run([str(self.gradle), "--offline", "--no-daemon", "--console=plain",
                                             "-p", str(root), "verifySelection",
                                             "-Dartemis.test.classes=de.tum.cit.aet.artemis.alpha.ChosenTest", *extra],
                                            capture_output=True, text=True, check=False, timeout=90)
                    self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
                    self.assertIn("artemis.test.classes", result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
