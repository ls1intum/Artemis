"""Unit tests of the tooling that keeps the exercise build images and the templates in sync.

Run: python3 -m unittest discover -s supporting_scripts/tests -p 'test_exercise_images.py'
"""

import re
import subprocess
import sys
import tempfile
import textwrap
import unittest
from contextlib import redirect_stderr, redirect_stdout
from io import BytesIO, StringIO
from pathlib import Path
from unittest.mock import call, patch
from urllib.error import HTTPError, URLError

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.dont_write_bytecode = True

import check_exercise_image_sync as sync_cli  # noqa: E402
import verify_exercise_images_offline as offline_cli  # noqa: E402
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

    def test_a_missing_child_is_not_answered_by_a_sibling_block(self):
        configuration = SAMPLE_CONFIGURATION.replace("            images:\n", "            no-images:\n", 1)
        with self.assertRaises(ValueError):
            read_mapping(configuration, ("artemis", "continuous-integration", "build", "images"))

    def test_a_deeper_key_with_the_name_is_not_a_child(self):
        configuration = textwrap.dedent(
            """\
            artemis:
                build:
                    nested:
                        images:
                            java:
                                default: "wrong:1"
            """
        )
        with self.assertRaises(ValueError):
            read_mapping(configuration, ("artemis", "build", "images"))

    def test_missing_path_is_reported(self):
        with self.assertRaises(ValueError):
            read_mapping(SAMPLE_CONFIGURATION, ("artemis", "continuous-integration", "missing"))

    def test_split_image(self):
        self.assertEqual(split_image("ghcr.io/ls1intum/artemis-kotlin-docker:v1.0.1"), ("ghcr.io/ls1intum/artemis-kotlin-docker", "v1.0.1"))
        with self.assertRaises(ValueError):
            split_image("localhost:5000/image")

    def test_digest_and_empty_image_tags_are_rejected(self):
        for image in ("repo@sha256:abcdef", "repo:tag@sha256:abcdef", "localhost:5000/image:", ":tag"):
            with self.subTest(image=image), self.assertRaises(ValueError):
                split_image(image)

    def test_default_images_of_the_checked_languages_have_a_source_repository(self):
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

    def test_folded_and_kept_block_scalars_are_refused(self):
        for style in (">", ">-", "|+"):
            with self.subTest(style=style), self.assertRaises(ValueError):
                phases.parse_phases(f"- name: compile\n  script: {style}\n    mvn -B\n    test\n")

    def test_every_variant_has_phases(self):
        for variant in assembly.VARIANTS:
            with self.subTest(variant=variant.name):
                self.assertTrue(phases.load_phases(TEMPLATES_DIR, variant.language, variant.phases_file))

    def test_build_script_runs_force_run_phases_after_a_failure(self):
        script = offline.build_script(phases.load_phases(TEMPLATES_DIR, "kotlin", "default_static.yaml"))
        self.assertIn("trap final_force_run_post_action EXIT", script)
        self.assertIn("--input \"assignment\"", script)
        self.assertNotIn("${studentParentWorkingDirectoryName}", script)


class OfflineTest(unittest.TestCase):
    def _run(self, *phase_definitions: phases.Phase) -> int:
        """Runs the generated build script with bash in a temporary directory and returns its exit status."""
        with tempfile.TemporaryDirectory() as directory:
            script = offline.build_script(list(phase_definitions)).replace(f"cd {offline.WORKING_DIRECTORY}", f"cd {directory}")
            path = Path(directory) / "build.sh"
            path.write_text(script, encoding="utf-8")
            return subprocess.run(["bash", str(path)], capture_output=True, check=False).returncode

    def test_a_failing_force_run_phase_fails_the_script(self):
        ok = phases.Phase("compile", "true", False)
        self.assertEqual(self._run(ok, phases.Phase("static_code_analysis", "false", True)), 1)

    def test_a_force_run_phase_fails_when_any_of_its_commands_fails(self):
        ok = phases.Phase("compile", "true", False)
        self.assertEqual(self._run(ok, phases.Phase("static_code_analysis", "false\ntrue", True)), 1)

    def test_a_succeeding_force_run_phase_keeps_the_script_green(self):
        ok = phases.Phase("compile", "true", False)
        self.assertEqual(self._run(ok, phases.Phase("static_code_analysis", "true\ntrue", True)), 0)

    def test_an_earlier_failure_keeps_its_exit_status_and_the_force_run_phase_still_runs(self):
        failing = phases.Phase("compile", "exit 7", False)
        with tempfile.TemporaryDirectory() as directory:
            marker = Path(directory) / "analysis-ran"
            self.assertEqual(self._run(failing, phases.Phase("static_code_analysis", f"touch {marker}", True)), 7)
            self.assertTrue(marker.exists())

    def test_mount_sources_are_absolute_for_a_relative_work_directory(self):
        command = offline.docker_command(Path("results/java/testing-dir"), Path("results/java/build.sh"), "image:1")
        sources = [command[index + 1].split(":")[0] for index, argument in enumerate(command) if argument == "-v"]
        self.assertEqual(len(sources), 2)
        self.assertTrue(all(source.startswith("/") for source in sources), sources)
        self.assertIn("--network", command)


class VerifyLogTest(unittest.TestCase):
    """What ``offline.verify`` reports from the log of a build, without running Docker."""

    def _verify(self, log_text: str, returncode: int) -> offline.Result:
        def run(command, stdout, **_):
            stdout.write(log_text)
            return subprocess.CompletedProcess(command, returncode)

        with tempfile.TemporaryDirectory() as directory, patch.object(offline.subprocess, "run", run):
            return offline.verify(TEMPLATES_DIR, assembly.Variant("rust", None), "image:1", Path(directory))

    def test_a_cargo_download_failure_is_named(self):
        result = self._verify("   Updating crates.io index\nerror: failed to get `chrono` as a dependency of package `x`\n\nCaused by:\n  download failed\n", 101)
        self.assertFalse(result.passed)
        self.assertEqual(result.missing, ["failed to get `chrono` as a dependency of package `x`"])

    def test_a_failure_without_a_known_pattern_reports_the_log_tail(self):
        log = "\n".join(f"line {number}" for number in range(10)) + "\n"
        result = self._verify(log, 1)
        self.assertEqual(result.missing, [f"line {number}" for number in range(5, 10)])

    def _verify_attempts(self, *attempts: tuple[str, int]) -> tuple[offline.Result, int]:
        remaining = list(attempts)

        def run(command, stdout, **_):
            log_text, returncode = remaining.pop(0)
            stdout.write(log_text)
            return subprocess.CompletedProcess(command, returncode)

        with tempfile.TemporaryDirectory() as directory, patch.object(offline.subprocess, "run", run):
            return offline.verify(TEMPLATES_DIR, assembly.Variant("swift", "plain"), "image:1", Path(directory)), len(remaining)

    FLAKE = "<unknown>:0: error: module 'SwiftShims' is defined in both 'a.pcm' and 'b.pcm'\n"

    def test_a_known_intermittent_compiler_error_is_built_once_more(self):
        result, unused = self._verify_attempts((self.FLAKE, 1), ("Build complete\n", 0))
        self.assertTrue(result.passed)
        self.assertEqual((result.attempts, unused), (2, 0))

    def test_the_known_compiler_error_is_not_retried_forever(self):
        result, unused = self._verify_attempts((self.FLAKE, 1), (self.FLAKE, 1))
        self.assertFalse(result.passed)
        self.assertEqual((result.attempts, unused), (2, 0))

    def test_any_other_failure_is_not_retried(self):
        result, unused = self._verify_attempts(("error: failed to get `rand` as a dependency\n", 101), ("Build complete\n", 0))
        self.assertFalse(result.passed)
        self.assertEqual((result.attempts, unused), (1, 1))

    def test_a_passing_build_reports_nothing(self):
        result = self._verify("Compiling\n", 0)
        self.assertTrue(result.passed)
        self.assertEqual(result.missing, [])


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
                for file in target.rglob("*"):
                    self.assertNotIn("${", file.name, f"{file.relative_to(target)} has a leftover placeholder in its name")
                    if file.is_file() and file.suffix in (".xml", ".gradle", ".java", ".kt", ".json", ".yaml", ".properties", ".swift", ".rs"):
                        self.assertIsNone(marker.search(file.read_text(encoding="utf-8")), f"{file.relative_to(target)} has a leftover placeholder")
                if not variant.is_jvm:
                    self.assertTrue((target / "assignment").is_dir())
                    continue
                self.assertTrue((target / ("assignment/src") / package_folder).is_dir())
                if variant.sequential:
                    for stage in assembly.BUILD_STAGES:
                        self.assertTrue((target / stage / "test" / package_folder).is_dir())
                else:
                    self.assertTrue((target / "test" / package_folder).is_dir())
                self.assertEqual((target / "staticCodeAnalysisConfig").is_dir(), variant.static_code_analysis and not variant.sequential)

    def test_rust_and_swift_repositories_keep_the_template_layout(self):
        with tempfile.TemporaryDirectory() as directory:
            rust = Path(directory) / "rust"
            assembly.assemble(TEMPLATES_DIR, assembly.Variant("rust", None), rust)
            self.assertIn('path = "assignment"', (rust / "Cargo.toml").read_text(encoding="utf-8"))
            self.assertIn('"assignment/src"', (rust / "build.rs").read_text(encoding="utf-8"))
            self.assertTrue((rust / "assignment" / "Cargo.lock").is_file())
            swift = Path(directory) / "swift"
            assembly.assemble(TEMPLATES_DIR, assembly.Variant("swift", "plain"), swift)
            self.assertTrue((swift / "Tests" / "SortingExampleTests" / "ClassTest.swift").is_file())
            self.assertTrue((swift / "assignment" / "Sources" / "SortingExampleLib").is_dir())
            self.assertIn('name: "SortingExample"', (swift / "Package.swift").read_text(encoding="utf-8"))
            self.assertTrue((swift / ".swiftlint.yml").is_file())

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

    def test_gradle_tool_versions_are_compared_with_the_dependency_of_the_image(self):
        template = "checkstyle {\n    toolVersion = '14.4.0'\n    reports {\n        xml.required = true\n    }\n}\npmd { toolVersion = '7.28.0' }\n"
        image = 'dependencies {\n    checkstyle "com.puppycrawl.tools:checkstyle:${checkstyle_version}"\n}\npmd {\n    toolVersion = "7.28.0"\n}\n'
        template_versions = sync_check.gradle_versions(template)
        self.assertEqual(template_versions, {"com.puppycrawl.tools:checkstyle": {"14.4.0"}, "net.sourceforge.pmd:pmd-java": {"7.28.0"}})
        self.assertEqual(sync_check.compare(template_versions, sync_check.gradle_versions(image, {"checkstyle_version": "14.4.0"})), [])
        # changing only the toolVersion of one tool must be reported
        self.assertEqual(
            sync_check.compare(template_versions, sync_check.gradle_versions(image, {"checkstyle_version": "14.3.0"})),
            ["com.puppycrawl.tools:checkstyle 14.4.0 differs from the image (14.3.0)"],
        )

    def test_indented_gradle_tool_blocks_report_version_mismatches(self):
        for tool, coordinate in sync_check.GRADLE_TOOL_COORDINATES.items():
            for indentation in ("    ", "\t"):
                with self.subTest(tool=tool, indentation=indentation):
                    template = f"{indentation}{tool} {{\n{indentation}    reports {{ xml.required = true }}\n{indentation}    toolVersion = '2.0'\n{indentation}}}\n"
                    image = f"{tool} {{ toolVersion = '1.0' }}\n"
                    self.assertEqual(
                        sync_check.compare(sync_check.gradle_versions(template), sync_check.gradle_versions(image)),
                        [f"{coordinate} 2.0 differs from the image (1.0)"],
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
        current = next(iter(sync_check.maven_versions(template)["com.google.guava:guava"]))
        stale = template.replace(current, "0.0.0-stale")
        problems = sync_check.check_language("kotlin", "ghcr.io/ls1intum/artemis-kotlin-docker:v1.0.1", fetch=self._fetch(stale))
        expected = f"com.google.guava:guava {current} differs from the image (0.0.0-stale)"
        self.assertTrue(any(expected in problem for problem in problems), problems)


CARGO_LOCK = """\
version = 4

[[package]]
name = "rand"
version = "0.9.4"
source = "registry+https://github.com/rust-lang/crates.io-index"
checksum = "abc"

[[package]]
name = "rust-template-exercise"
version = "0.1.0"
dependencies = ["rand"]
"""


class CargoLockTest(unittest.TestCase):
    def test_only_crates_from_a_registry_are_compared(self):
        self.assertEqual(sync_check.versions_of("Cargo.lock", CARGO_LOCK), {"rand": {"0.9.4"}})

    def test_a_crate_from_a_repository_is_compared_with_its_locked_commit(self):
        lock = '[[package]]\nname = "tool"\nversion = "1.0.0"\nsource = "git+https://example.com/tool.git?tag=v1#{commit}"\n'
        template = sync_check.versions_of("Cargo.lock", lock.format(commit="a" * 40))
        image = sync_check.versions_of("Cargo.lock", lock.format(commit="b" * 40))
        problems = sync_check.compare(template, image)
        self.assertEqual(len(problems), 1, problems)
        self.assertTrue(problems[0].startswith("tool 1.0.0 (git+https://example.com/tool.git?tag=v1#aaaa"), problems)
        self.assertEqual(sync_check.compare(template, template), [])

    def _fetch(self, image_lock: str, requested: list[str]):
        def fetch(url: str) -> str:
            requested.append(url)
            return image_lock

        return fetch

    def test_every_lock_file_of_the_templates_is_read_from_the_image_tag(self):
        requested: list[str] = []
        for entry in sync_check.SOURCES["rust"]:
            template = sync_check.cargo_lock_versions((TEMPLATES_DIR / entry.template_file).read_text(encoding="utf-8"))
            self.assertTrue(template, f"{entry.template_file} has no crate from a registry")
        image_lock = "\n".join(
            f'[[package]]\nname = "{name}"\nversion = "{version}"\nsource = "registry+x"\n'
            for entry in sync_check.SOURCES["rust"]
            for name, versions in sync_check.cargo_lock_versions((TEMPLATES_DIR / entry.template_file).read_text(encoding="utf-8")).items()
            for version in versions
        )
        problems = sync_check.check_language("rust", "ghcr.io/ls1intum/artemis-rust-docker:v1.2.0", fetch=self._fetch(image_lock, requested))
        self.assertEqual(problems, [])
        self.assertIn("https://raw.githubusercontent.com/ls1intum/artemis-rust-docker/v1.2.0/test/assignment/Cargo.lock", requested)
        self.assertIn("https://raw.githubusercontent.com/ls1intum/artemis-rust-docker/v1.2.0/test/Cargo.lock", requested)

    def test_a_crate_version_the_image_does_not_hold_is_reported(self):
        stale = '[[package]]\nname = "rand"\nversion = "0.8.5"\nsource = "registry+x"\n'
        problems = sync_check.check_language("rust", "ghcr.io/ls1intum/artemis-rust-docker:v1.2.0", fetch=self._fetch(stale, []))
        self.assertTrue(any(problem.startswith("rust/solution/Cargo.lock: rand ") and "differs from the image (0.8.5)" in problem for problem in problems), problems)


NPM_LOCK = """\
{"lockfileVersion": 3, "packages": {
  "": {"name": "artemis-test"},
  "assignment": {"name": "assignment", "version": "1.0.0", "devDependencies": {"@types/node": "^20"}},
  "node_modules/assignment": {"resolved": "assignment", "link": true},
  "node_modules/@types/node": {"version": "20.19.43"},
  "node_modules/a/node_modules/minimatch": {"version": "3.1.5"},
  "node_modules/minimatch": {"version": "10.2.6"}
}}
"""

SWIFT_PACKAGE = """\
// swift-tools-version:5.2
let package = Package(
    dependencies: [
        // .package(url: "https://example.com/commented.git", from: "9.0.0"),
        .package(name: "SwiftTestReporter", url: "https://github.com/allegro/swift-junit.git", from: "2.0.0"),
        .package(url: "https://github.com/yanagiba/swift-ast.git",
                 exact: "0.19.9"),
    ],
    targets: [.target(name: "Lib")]
)
"""


class NpmAndSwiftTest(unittest.TestCase):
    def test_npm_lock_lists_installed_packages_without_workspaces_and_links(self):
        self.assertEqual(
            sync_check.versions_of("package-lock.json", NPM_LOCK),
            {"@types/node": {"20.19.43"}, "minimatch": {"3.1.5", "10.2.6"}},
        )

    def test_a_package_from_a_repository_is_compared_with_its_locked_commit(self):
        lock = '{{"packages": {{"node_modules/tool": {{"version": "1.0.0", "resolved": "git+ssh://git@example.com/tool.git#{commit}"}}}}}}'
        template = sync_check.versions_of("package-lock.json", lock.format(commit="a" * 40))
        image = sync_check.versions_of("package-lock.json", lock.format(commit="b" * 40))
        self.assertEqual(len(sync_check.compare(template, image)), 1)
        self.assertEqual(sync_check.compare(template, template), [])
        unversioned = '{{"packages": {{"node_modules/tool": {{"resolved": "git+ssh://git@example.com/tool.git#{commit}"}}}}}}'
        self.assertEqual(len(sync_check.compare(sync_check.versions_of("package-lock.json", unversioned.format(commit="a" * 40)), sync_check.versions_of("package-lock.json", unversioned.format(commit="b" * 40)))), 1)
        self.assertEqual(sync_check.versions_of("package-lock.json", '{"packages": {"node_modules/tool": {"resolved": "https://registry.npmjs.org/tool.tgz"}}}'), {})
        registry = '{"packages": {"node_modules/tool": {"version": "1.0.0", "resolved": "https://registry.npmjs.org/tool/-/tool-1.0.0.tgz"}}}'
        self.assertEqual(sync_check.versions_of("package-lock.json", registry), {"tool": {"1.0.0"}})

    def test_a_typescript_package_the_image_lacks_is_reported(self):
        stale = '{"packages": {"node_modules/minimatch": {"version": "10.2.6"}}}'
        problems = sync_check.compare(sync_check.versions_of("package-lock.json", NPM_LOCK), sync_check.versions_of("package-lock.json", stale))
        self.assertEqual(problems, ["@types/node 20.19.43 is not in the image", "minimatch 3.1.5 differs from the image (10.2.6)"])

    def test_swift_dependencies_are_compared_by_repository_and_requirement(self):
        declared = sync_check.versions_of("Package.swift", SWIFT_PACKAGE)
        self.assertEqual(declared, {"https://github.com/allegro/swift-junit": {'from: "2.0.0"'}, "https://github.com/yanagiba/swift-ast": {'exact: "0.19.9"'}})
        raised = SWIFT_PACKAGE.replace('from: "2.0.0"', 'from: "2.1.0"')
        self.assertEqual(
            sync_check.compare(sync_check.versions_of("Package.swift", raised), declared),
            ['https://github.com/allegro/swift-junit from: "2.1.0" differs from the image (from: "2.0.0")'],
        )

    def test_image_files_of_repositories_with_several_projects_are_read_from_the_repository_root(self):
        requested: list[str] = []

        def fetch(url: str) -> str:
            requested.append(url)
            return '{"packages": {}}'

        sync_check.check_language("javascript", "ghcr.io/ls1intum/artemis-javascript-docker:v1.2.0", fetch=fetch)
        self.assertIn("https://raw.githubusercontent.com/ls1intum/artemis-javascript-docker/v1.2.0/vitest/javascript/package-lock.json", requested)


class FetchFailureTest(unittest.TestCase):
    URL = "https://raw.githubusercontent.com/example/image/v9.9.9/pom.xml"

    def test_permanent_http_errors_fail_without_retrying(self):
        for status in (400, 403, 404, 410, 422):
            with self.subTest(status=status):
                error = HTTPError(self.URL, status, "permanent failure", None, None)
                with patch.object(sync_check.urllib.request, "urlopen", side_effect=error) as fetch, patch.object(sync_check.time, "sleep") as sleep:
                    with self.assertRaises(HTTPError) as raised:
                        sync_check.fetch_raw_github(self.URL)
                    self.assertIs(raised.exception, error)
                    self.assertEqual(fetch.call_count, 1)
                    sleep.assert_not_called()

    def test_transient_http_errors_retry_and_can_recover(self):
        for status in (429, 500, 502, 503):
            with self.subTest(status=status):
                error = HTTPError(self.URL, status, "temporary failure", None, None)
                with patch.object(sync_check.urllib.request, "urlopen", side_effect=[error, BytesIO(b"versions")]) as fetch, patch.object(sync_check.time, "sleep") as sleep:
                    self.assertEqual(sync_check.fetch_raw_github(self.URL), "versions")
                    self.assertEqual(fetch.call_count, 2)
                    sleep.assert_called_once_with(3)

    def test_transient_failures_have_bounded_retries(self):
        errors = [HTTPError(self.URL, 429, "rate limit", None, None), HTTPError(self.URL, 503, "unavailable", None, None), URLError("connection lost"), TimeoutError("timed out")]
        for error in errors:
            with self.subTest(error=error):
                with patch.object(sync_check.urllib.request, "urlopen", side_effect=error) as fetch, patch.object(sync_check.time, "sleep") as sleep:
                    with self.assertRaises(type(error)):
                        sync_check.fetch_raw_github(self.URL)
                    self.assertEqual(fetch.call_count, 4)
                    self.assertEqual(sleep.call_args_list, [call(3), call(6), call(12)])

    def test_missing_tag_is_reported_with_image_tag_and_url(self):
        def missing(url):
            raise HTTPError(url, 404, "Not Found", None, None)

        image = "ghcr.io/ls1intum/artemis-kotlin-docker:v9.9.9"
        problems = sync_check.check_language("kotlin", image, fetch=missing)
        self.assertEqual(len(problems), 1)
        self.assertIn(image, problems[0])
        self.assertIn("https://raw.githubusercontent.com/ls1intum/artemis-kotlin-docker/v9.9.9/", problems[0])
        self.assertIn("404", problems[0])
        output = StringIO()
        with patch.object(sync_cli, "check", return_value={"kotlin": (image, problems)}), redirect_stdout(output):
            self.assertEqual(sync_cli.main(), 1)
        self.assertIn("::error", output.getvalue())
        self.assertIn(problems[0], output.getvalue())


    def test_unverifiable_image_references_report_problems_without_fetching(self):
        for image in ("ghcr.io/ls1intum/artemis-kotlin-docker@sha256:abcdef", "unknown/image:tag", "ghcr.io/ls1intum/artemis-kotlin-docker:"):
            with self.subTest(image=image), patch.object(sync_check, "fetch_raw_github") as fetch:
                problems = sync_check.check_language("kotlin", image, fetch=fetch)
                self.assertEqual(len(problems), 1)
                self.assertIn(image, problems[0])
                fetch.assert_not_called()

    def test_fetch_diagnostics_are_escaped_in_workflow_annotations(self):
        problem = "failed url%20path\n::warning::injected\rcontext"
        output = StringIO()
        with patch.object(sync_cli, "check", return_value={"kotlin": ("image:tag", [problem])}), redirect_stdout(output):
            self.assertEqual(sync_cli.main(), 1)
        self.assertIn("failed url%2520path%0A::warning::injected%0Dcontext", output.getvalue())
        self.assertNotIn("\n::warning::", output.getvalue())


class OfflineCliTest(unittest.TestCase):
    @staticmethod
    def successful_verification(templates, variant, image, workspace, platform):
        return offline.Result(variant, image, True, workspace / "test.log", [])

    def test_invalid_image_overrides_are_rejected_before_verification(self):
        for value in ("Java=image", "jva=image", "java", "java=", "java=   ", "=image"):
            with self.subTest(value=value), tempfile.TemporaryDirectory() as temporary:
                arguments = ["verify_exercise_images_offline.py", "--image", value, "--variant", assembly.VARIANTS[0].name, "--work-dir", temporary]
                error = StringIO()
                with patch.object(sys, "argv", arguments), patch.object(offline_cli, "verify", side_effect=self.successful_verification) as verify, redirect_stderr(error), redirect_stdout(StringIO()):
                    with self.assertRaises(SystemExit) as raised:
                        offline_cli.main()
                    self.assertEqual(raised.exception.code, 2)
                    self.assertIn("--image", error.getvalue())
                    verify.assert_not_called()

    def test_valid_image_override_reaches_the_verifier(self):
        variant = assembly.VARIANTS[0]
        with tempfile.TemporaryDirectory() as temporary:
            arguments = ["verify_exercise_images_offline.py", "--image", f"{variant.language}=custom-image:test", "--variant", variant.name, "--work-dir", temporary]
            with patch.object(sys, "argv", arguments), patch.object(offline_cli, "verify", side_effect=self.successful_verification) as verify, redirect_stdout(StringIO()):
                self.assertEqual(offline_cli.main(), 0)
                verify.assert_called_once()
                self.assertEqual(verify.call_args.args[1], variant)
                self.assertEqual(verify.call_args.args[2], "custom-image:test")

    def test_conflicting_filters_cannot_report_success_without_verifying_anything(self):
        variant = assembly.VARIANTS[0]
        other_language = next(item.language for item in assembly.VARIANTS if item.language != variant.language)
        arguments = ["verify_exercise_images_offline.py", "--language", other_language, "--variant", variant.name]
        with patch.object(sys, "argv", arguments), patch.object(offline_cli, "verify") as verify, redirect_stderr(StringIO()), redirect_stdout(StringIO()):
            with self.assertRaises(SystemExit) as raised:
                offline_cli.main()
            self.assertEqual(raised.exception.code, 2)
            verify.assert_not_called()


if __name__ == "__main__":
    unittest.main()
