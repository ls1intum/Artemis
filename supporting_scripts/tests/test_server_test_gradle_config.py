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
            production_config = str(REPOSITORY / "gradle/test.gradle").replace("'", "\\'")
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
        if (System.getProperty('includeModules')) {
            def matcher = new org.gradle.api.internal.tasks.testing.filter.TestSelectionMatcher(
                new org.gradle.api.internal.tasks.testing.filter.TestFilterSpec(
                    selected.filter.includePatterns, selected.filter.excludePatterns, [] as Set))
            assert matcher.matchesTest('de.tum.cit.aet.artemis.alpha.RootTest', 'method')
            assert matcher.matchesTest('de.tum.cit.aet.artemis.alpha.service.NestedTest', 'method')
            assert matcher.matchesTest('de.tum.cit.aet.artemis.shared.RootTest', 'method')
            assert !matcher.matchesTest('de.tum.cit.aet.artemis.beta.OtherTest', 'method')
        }
        println('Verified server test filters')
    }
}
""".replace("PRODUCTION_CONFIG", production_config))
            for arguments in ([], ["-DincludeModules=alpha"], ["-DincludeTags=nightly-lti"]):
                with self.subTest(arguments=arguments):
                    result = subprocess.run(
                        [str(self.gradle), "--offline", "--no-daemon", "--console=plain",
                         "-p", str(root), "verifySelection", *arguments],
                        capture_output=True, text=True, check=False, timeout=90,
                    )
                    self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                    self.assertIn("Verified server test filters", result.stdout)


if __name__ == "__main__":
    unittest.main(verbosity=2)
