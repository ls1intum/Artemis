"""Unit tests of the tooling that keeps the exercise build images and the templates in sync.

Run: python3 -m unittest discover -s supporting_scripts/tests -p 'test_exercise_images.py'
"""

import re
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.dont_write_bytecode = True

from exercise_images import assembly, offline, phases, sync_check  # noqa: E402
from exercise_images.images import IMAGE_SOURCES, TEMPLATES_DIR, configured_images, read_mapping, split_image  # noqa: E402

SAMPLE_CONFIGURATION = """\
artemis:
    continuous-integration:
        build:
            default-docker-flags:
                - flag: --cpus
                  value: 2
            images:
                java:
                    # possible overrides: maven, gradle
                    default: "ls1tum/artemis-maven-template:java25-3"
                    maven_blackbox: "ghcr.io/uni-passau-artemis/artemis-dejagnu:25"  # trailing comment
                kotlin:
                    default: "ghcr.io/ls1intum/artemis-kotlin-docker:v1.0.1"
            other:
                images:
                    java:
                        default: "not-this-one:1"
"""


class ImagesTest(unittest.TestCase):
    def test_reads_only_the_build_agent_images(self):
        images = read_mapping(SAMPLE_CONFIGURATION, ("artemis", "continuous-integration", "build", "images"))
        self.assertEqual(
            images,
            {
                "java": {"default": "ls1tum/artemis-maven-template:java25-3", "maven_blackbox": "ghcr.io/uni-passau-artemis/artemis-dejagnu:25"},
                "kotlin": {"default": "ghcr.io/ls1intum/artemis-kotlin-docker:v1.0.1"},
            },
        )

    def test_missing_path_is_reported(self):
        with self.assertRaises(ValueError):
            read_mapping(SAMPLE_CONFIGURATION, ("artemis", "continuous-integration", "missing"))

    def test_split_image(self):
        self.assertEqual(split_image("ghcr.io/ls1intum/artemis-kotlin-docker:v1.0.1"), ("ghcr.io/ls1intum/artemis-kotlin-docker", "v1.0.1"))
        with self.assertRaises(ValueError):
            split_image("localhost:5000/image")

    def test_default_images_of_java_and_kotlin_have_a_source_repository(self):
        images = configured_images()
        for language in sync_check.SOURCES:
            name, _ = split_image(images[language]["default"])
            self.assertIn(name, IMAGE_SOURCES, f"the {language} default image has no entry in IMAGE_SOURCES")


class PhasesTest(unittest.TestCase):
    def test_parses_plain_quoted_and_block_scripts(self):
        parsed = phases.parse_phases(
            textwrap.dedent(
                """\
                - name: compile
                  script: 'mvn -B clean compile'
                - name: structural
                  script: |-
                    cd "structural"
                    mvn -B test
                  resultPaths:
                    - '**/target/surefire-reports/*.xml'
                - name: static_code_analysis
                  script: detekt --input "${studentParentWorkingDirectoryName}"
                  forceRun: true
                """
            )
        )
        self.assertEqual([phase.name for phase in parsed], ["compile", "structural", "static_code_analysis"])
        self.assertEqual(parsed[0].script, "mvn -B clean compile")
        self.assertEqual(parsed[1].script, 'cd "structural"\nmvn -B test')
        self.assertEqual([phase.force_run for phase in parsed], [False, False, True])

    def test_every_variant_has_phases(self):
        for variant in assembly.VARIANTS:
            with self.subTest(variant=variant.name):
                self.assertTrue(phases.load_phases(TEMPLATES_DIR, variant.language, variant.phases_file))

    def test_build_script_runs_force_run_phases_after_a_failure(self):
        script = offline.build_script(phases.load_phases(TEMPLATES_DIR, "kotlin", "default_static.yaml"))
        self.assertIn("trap final_force_run_post_action EXIT", script)
        self.assertIn("--input \"assignment\"", script)
        self.assertNotIn("${studentParentWorkingDirectoryName}", script)


class AssemblyTest(unittest.TestCase):
    def test_sections_are_kept_or_dropped(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "pom.xml"
            file.write_text(
                "a\n<!--%static-code-analysis-start%-->\nsca\n<!--%static-code-analysis-stop%-->\n"
                "<!--%maven-central-mirror-start%-->\nmirror\n<!--%maven-central-mirror-stop%-->\nb\n",
                encoding="utf-8",
            )
            assembly.replace_sections(file, {"static-code-analysis": True, "maven-central-mirror": False})
            self.assertEqual(file.read_text(encoding="utf-8"), "a\nsca\nb\n")

    def test_every_variant_is_assembled_without_leftover_markers_or_placeholders(self):
        marker = re.compile(r"%[a-z-]+-(start|stop)%|\$\{(packageName|packageNameFolder|exerciseName|exerciseNamePomXml|studentWorkingDirectory|"
                            r"studentParentWorkingDirectoryName|studentWorkingDirectoryNoSlash|packaging|mavenCentralMirrorUrl)\}")
        for variant in assembly.VARIANTS:
            with self.subTest(variant=variant.name), tempfile.TemporaryDirectory() as directory:
                target = Path(directory) / "testing-dir"
                assembly.assemble(TEMPLATES_DIR, variant, target)
                package_folder = Path(*assembly.PACKAGE_NAME.split("."))
                self.assertTrue((target / ("assignment/src") / package_folder).is_dir())
                for file in target.rglob("*"):
                    if file.is_file() and file.suffix in (".xml", ".gradle", ".java", ".kt", ".json", ".yaml", ".properties"):
                        self.assertIsNone(marker.search(file.read_text(encoding="utf-8")), f"{file.relative_to(target)} has a leftover placeholder")
                if variant.sequential:
                    for stage in assembly.BUILD_STAGES:
                        self.assertTrue((target / stage / "test" / package_folder).is_dir())
                else:
                    self.assertTrue((target / "test" / package_folder).is_dir())
                self.assertEqual((target / "staticCodeAnalysisConfig").is_dir(), variant.static_code_analysis and not variant.sequential)

    def test_variant_names_are_unique(self):
        names = [variant.name for variant in assembly.VARIANTS]
        self.assertEqual(len(names), len(set(names)))


POM = """\
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <properties><guava.version>33.8.0-jre</guava.version><kotlin.version>2.4.20</kotlin.version></properties>
  <dependencyManagement><dependencies>
    <dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>${guava.version}</version></dependency>
  </dependencies></dependencyManagement>
  <dependencies>
    <dependency><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-stdlib</artifactId><version>${kotlin.version}</version></dependency>
    <dependency><groupId>org.aspectj</groupId><artifactId>aspectjrt</artifactId></dependency>
  </dependencies>
  <build><plugins>
    <plugin><artifactId>maven-surefire-plugin</artifactId><version>3.6.0</version></plugin>
    <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-dependency-plugin</artifactId><version>3.11.0</version>
      <executions><execution><configuration><artifactItems>
        <artifactItem><groupId>de.tum.cit.ase</groupId><artifactId>ares</artifactId><version>2.2.1</version></artifactItem>
      </artifactItems></configuration></execution></executions>
    </plugin>
  </plugins></build>
</project>
"""


class SyncCheckTest(unittest.TestCase):
    def test_maven_versions_resolve_properties_and_default_plugin_group(self):
        self.assertEqual(
            sync_check.maven_versions(POM),
            {
                "com.google.guava:guava": {"33.8.0-jre"},
                "org.jetbrains.kotlin:kotlin-stdlib": {"2.4.20"},
                "org.apache.maven.plugins:maven-surefire-plugin": {"3.6.0"},
                "org.apache.maven.plugins:maven-dependency-plugin": {"3.11.0"},
                "de.tum.cit.ase:ares": {"2.2.1"},
            },
        )

    def test_a_doctype_is_refused(self):
        with self.assertRaises(ValueError):
            sync_check.maven_versions('<!DOCTYPE project [<!ENTITY x "y">]><project/>')

    def test_gradle_versions_resolve_properties_and_ignore_comments(self):
        build = """
            plugins {
                id 'com.github.spotbugs' version '6.5.12'
                // id 'ignored' version '1'
            }
            ext { aresVersion = '2.2.1' }
            dependencies {
                testImplementation "de.tum.cit.ase:ares:${aresVersion}"
                implementation "org.apache.commons:commons-lang3:${commons_lang3_version}"
            }
        """
        self.assertEqual(
            sync_check.gradle_versions(build, {"commons_lang3_version": "3.21.0"}),
            {"plugin:com.github.spotbugs": {"6.5.12"}, "de.tum.cit.ase:ares": {"2.2.1"}, "org.apache.commons:commons-lang3": {"3.21.0"}},
        )

    def test_wrapper_version(self):
        self.assertEqual(sync_check.wrapper_versions("distributionUrl=https\\://services.gradle.org/distributions/gradle-9.8.0-bin.zip"), {"gradle-wrapper": {"9.8.0"}})

    def test_compare_reports_missing_and_different_versions(self):
        problems = sync_check.compare({"a:b": {"2"}, "c:d": {"1"}, "e:f": {"1"}}, {"a:b": {"1"}, "e:f": {"1", "0"}})
        self.assertEqual(problems, ["a:b 2 differs from the image (1)", "c:d 1 is not in the image"])

    def _fetch(self, kotlin_pom: str):
        def fetch(url: str) -> str:
            self.assertIn("artemis-kotlin-docker/v1.0.1/artemis-kotlin-template/pom.xml", url)
            return kotlin_pom

        return fetch

    def test_the_kotlin_template_is_compared_with_the_pom_of_the_image_tag(self):
        template = (TEMPLATES_DIR / "kotlin/test/maven/projectTemplate/pom.xml").read_text(encoding="utf-8")
        self.assertEqual(sync_check.check_language("kotlin", "ghcr.io/ls1intum/artemis-kotlin-docker:v1.0.1", fetch=self._fetch(template)), [])

    def test_a_pin_the_image_does_not_hold_is_reported(self):
        template = (TEMPLATES_DIR / "kotlin/test/maven/projectTemplate/pom.xml").read_text(encoding="utf-8")
        stale = template.replace("33.8.0-jre", "33.6.0-jre")
        problems = sync_check.check_language("kotlin", "ghcr.io/ls1intum/artemis-kotlin-docker:v1.0.1", fetch=self._fetch(stale))
        self.assertTrue(any("com.google.guava:guava 33.8.0-jre differs from the image (33.6.0-jre)" in problem for problem in problems), problems)


if __name__ == "__main__":
    unittest.main()
