"""Assembles the test and assignment repositories of exercises from the templates.

This follows ``ProgrammingExerciseRepositoryService``: which template directories are copied and in which order, the
optional sections of the build files, the sequential build stages and the placeholders. Java and Kotlin have their own
test layout, every other language copies its ``test`` template as it is and its ``solution`` template as the ``assignment``
directory. The repositories are built with the default settings of an instance without a Maven Central mirror. When the
service changes, this has to follow; a layout that no longer matches makes the offline build fail, which is the signal to adjust it.
"""

import os
import re
import shutil
from dataclasses import dataclass
from pathlib import Path

PACKAGE_NAME = "edu.example.sorting"
EXERCISE_TITLE = "Sorting Example"
ASSIGNMENT_DIRECTORY = "assignment"
PACKAGE_FOLDER_PLACEHOLDER = "${packageNameFolder}"
BINARY_FILES = {"gradle-wrapper.jar"}
BUILD_STAGES = ("structural", "behavior")
# Languages whose test repository is assembled from `projectTemplate`, test files and build stages instead of being copied
JVM_LANGUAGES = ("java", "kotlin")
# Languages with a restricted package name; the others use a Java package name
PACKAGE_NAMES = {"swift": "SortingExample", "dart": "sorting_example", "go": "sorting"}
NAME_PLACEHOLDERS = ("${packageNameFolder}", "${packageNameFile}")
# Languages whose test repository is not checked out into the root of the working directory but next to the assignment
# (``RepositoryCheckoutService.RepositoryCheckoutPath.TEST``). Their build phases and project files refer to it as ``${testWorkingDirectory}``.
TEST_DIRECTORY = "tests"
TEST_DIRECTORY_LANGUAGES = ("ruby", "dart", "c_sharp", "c", "vhdl", "assembler", "ocaml", "go", "bash")
# Languages whose build also needs the solution repository, checked out into ``solution`` (``RepositoryCheckoutPath.SOLUTION``)
SOLUTION_DIRECTORY = "solution"
SOLUTION_DIRECTORY_LANGUAGES = ("haskell", "ocaml")
# Languages whose templates are grouped by project type below the language directory (``c/gcc``), not by ``test``/``solution`` directly
PROJECT_TYPE_DIRECTORY_LANGUAGES = ("c",)


@dataclass(frozen=True)
class Variant:
    """One way to configure an exercise that changes the build."""

    language: str
    project_type: str | None
    static_code_analysis: bool = False
    sequential: bool = False

    @property
    def name(self) -> str:
        parts = [self.language, self.project_type or "default"]
        if self.static_code_analysis:
            parts.append("static")
        if self.sequential:
            parts.append("sequential")
        return "-".join(parts).replace("_", "-")

    @property
    def is_jvm(self) -> bool:
        return self.language in JVM_LANGUAGES

    @property
    def test_directory(self) -> str:
        """The directory of the working directory the test repository is checked out to, empty for its root."""
        return TEST_DIRECTORY if self.language in TEST_DIRECTORY_LANGUAGES else ""

    @property
    def package_name(self) -> str:
        return PACKAGE_NAMES.get(self.language, PACKAGE_NAME)

    @property
    def is_gradle(self) -> bool:
        return self.project_type in ("plain_gradle", "gradle_gradle")

    @property
    def phases_file(self) -> str:
        """The file name below ``phases/<language>`` (see ``BuildScriptProviderService.buildTemplateName``)."""
        base = {"maven_maven": "plain_maven", "gradle_gradle": "plain_gradle"}.get(self.project_type or "", self.project_type or "default")
        suffix = ("_static" if self.static_code_analysis else "") + ("_sequential" if self.sequential else "")
        return f"{base}{suffix}.yaml"


VARIANTS = [
    Variant("java", "plain_maven"),
    Variant("java", "plain_maven", static_code_analysis=True),
    Variant("java", "plain_maven", sequential=True),
    Variant("java", "maven_maven"),
    Variant("java", "plain_gradle"),
    Variant("java", "plain_gradle", static_code_analysis=True),
    Variant("java", "plain_gradle", sequential=True),
    Variant("kotlin", None),
    Variant("kotlin", None, static_code_analysis=True),
    Variant("kotlin", None, sequential=True),
    Variant("rust", None),
    Variant("rust", None, static_code_analysis=True),
    Variant("rust", None, sequential=True),
    Variant("swift", "plain"),
    Variant("swift", "plain", static_code_analysis=True),
    Variant("swift", "plain", sequential=True),
    Variant("javascript", None),
    Variant("javascript", None, static_code_analysis=True),
    Variant("javascript", None, sequential=True),
    Variant("typescript", None),
    Variant("typescript", None, static_code_analysis=True),
    Variant("typescript", None, sequential=True),
    Variant("ruby", None),
    Variant("ruby", None, static_code_analysis=True),
    Variant("ruby", None, sequential=True),
    Variant("dart", None),
    Variant("dart", None, static_code_analysis=True),
    Variant("dart", None, sequential=True),
    Variant("c_sharp", None),
    Variant("c_sharp", None, static_code_analysis=True),
    Variant("c_sharp", None, sequential=True),
    Variant("python", None),
    Variant("python", None, static_code_analysis=True),
    Variant("python", None, sequential=True),
    Variant("r", None),
    Variant("r", None, static_code_analysis=True),
    Variant("c", "gcc"),
    Variant("c", "fact"),
    Variant("c_plus_plus", None),
    Variant("c_plus_plus", None, static_code_analysis=True),
    Variant("go", None),
    Variant("go", None, static_code_analysis=True),
    Variant("go", None, sequential=True),
    Variant("ocaml", None),
    Variant("bash", None),
    Variant("vhdl", None),
    Variant("assembler", None),
    # Not built sequentially: `./run.sh -s` of both stages deletes the solution and the tests after the first stage, so the second stage can
    # never build (the template problem is independent of the image).
    Variant("haskell", None),
]


def replace_sections(path: Path, sections: dict[str, bool]) -> None:
    """Keeps or drops the lines between ``%name-start%`` and ``%name-stop%`` markers (``FileUtil.replacePlaceholderSections``)."""
    # Longest names first: "maven-central-mirror" must not claim the marker lines of a longer section name.
    patterns = [(re.compile(rf".*%{re.escape(name)}.*%.*"), keep) for name, keep in sorted(sections.items(), key=lambda item: -len(item[0]))]
    kept: list[str] = []
    open_section: bool | None = None
    for line in path.read_text(encoding="utf-8").splitlines():
        match = next((keep for pattern, keep in patterns if pattern.fullmatch(line)), None)
        if open_section is None:
            if match is not None:
                open_section = match
                continue
        elif match is not None:
            open_section = None
            continue
        if open_section is None or open_section:
            kept.append(line)
    path.write_text("\n".join(kept) + "\n", encoding="utf-8")


def _copy_tree(source: Path, target: Path) -> None:
    if source.is_dir():
        shutil.copytree(source, target, dirs_exist_ok=True)


def _copy_flat(source: Path, target: Path) -> None:
    """Copies all files below ``source`` into ``target`` without the directory structure."""
    target.mkdir(parents=True, exist_ok=True)
    for file in sorted(source.rglob("*")):
        if file.is_file():
            shutil.copy2(file, target / file.name)


def _rename_name_placeholders(root: Path, value: str) -> None:
    """Replaces the name placeholders in the names of files and directories, deepest first (``FileUtil.replaceVariablesInDirectoryName``)."""
    for directory, folders, files in os.walk(root, topdown=False):
        for name in folders + files:
            renamed = name
            for placeholder in NAME_PLACEHOLDERS:
                renamed = renamed.replace(placeholder, value)
            if renamed != name:
                os.rename(Path(directory) / name, Path(directory) / renamed)


def _rename_package_folders(root: Path) -> None:
    package_path = Path(*PACKAGE_NAME.split("."))
    for folder in sorted(root.rglob(PACKAGE_FOLDER_PLACEHOLDER), key=lambda path: -len(path.parts)):
        if folder.is_dir():
            target = folder.parent / package_path
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copytree(folder, target, dirs_exist_ok=True)
            shutil.rmtree(folder)


def replace_placeholders(root: Path, variant: Variant) -> None:
    if variant.is_jvm:
        _rename_package_folders(root)
    else:
        _rename_name_placeholders(root, variant.package_name)
    replacements = {
        "${packageName}": variant.package_name,
        "${exerciseNamePomXml}": EXERCISE_TITLE.replace(" ", "-"),
        "${exerciseName}": EXERCISE_TITLE,
        "${packaging}": "pom" if variant.sequential else "jar",
        "${studentParentWorkingDirectoryName}": ASSIGNMENT_DIRECTORY,
        "${studentWorkingDirectory}": f"/{ASSIGNMENT_DIRECTORY}/src",
        "${testWorkingDirectory}": "tests",
        "${solutionWorkingDirectory}": "solution",
    }
    if variant.is_gradle or variant.language == "rust":
        replacements["${studentWorkingDirectoryNoSlash}"] = f"{ASSIGNMENT_DIRECTORY}/src"
    for file in root.rglob("*"):
        if not file.is_file() or file.name in BINARY_FILES:
            continue
        try:
            text = file.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        replaced = text
        for placeholder, value in replacements.items():
            replaced = replaced.replace(placeholder, value)
        if replaced != text:
            file.write_text(replaced, encoding="utf-8")


def _assemble_copied(templates_dir: Path, variant: Variant, target: Path) -> None:
    """A language without a special test structure: the test template as it is, the solution next to it."""
    language_dir = templates_dir / variant.language
    if variant.language in PROJECT_TYPE_DIRECTORY_LANGUAGES and variant.project_type:
        language_dir = language_dir / variant.project_type
    test_repository = target / variant.test_directory
    _copy_tree(language_dir / "test", test_repository)
    if variant.static_code_analysis:
        # Copied afterwards, so that it overwrites the files of the regular test template (`hasSeparateStaticCodeAnalysisTemplateFiles`)
        _copy_tree(language_dir / "staticCodeAnalysis" / "test", test_repository)
    _copy_tree(language_dir / "solution", target / ASSIGNMENT_DIRECTORY)
    if variant.language in SOLUTION_DIRECTORY_LANGUAGES:
        _copy_tree(language_dir / "solution", target / SOLUTION_DIRECTORY)
    if variant.static_code_analysis:
        # Ruby and Dart also overwrite files of the assignment (the build file with the analysis tool and its configuration)
        _copy_tree(language_dir / "staticCodeAnalysis" / "solution", target / ASSIGNMENT_DIRECTORY)
    replace_placeholders(target, variant)


def assemble(templates_dir: Path, variant: Variant, target: Path) -> None:
    """Writes the working directory of the build to ``target``: the test repository (in its checkout directory) and the solution as ``assignment``."""
    if not variant.is_jvm:
        shutil.rmtree(target, ignore_errors=True)
        target.mkdir(parents=True)
        _assemble_copied(templates_dir, variant, target)
        return
    language_dir = templates_dir / variant.language
    test_dir = language_dir / "test"
    project_type_dir = language_dir / variant.project_type if variant.project_type else None
    shutil.rmtree(target, ignore_errors=True)
    target.mkdir(parents=True)

    _copy_tree(test_dir / ("gradle" if variant.is_gradle else "maven") / "projectTemplate", target)
    if project_type_dir is not None:
        _copy_tree(project_type_dir / "test" / "projectTemplate", target)

    sections = {
        "static-code-analysis": variant.static_code_analysis,
        "maven-central-mirror": False,
        "maven-central-fallback": True,
        "non-sequential": not variant.sequential,
        "sequential": variant.sequential,
    }
    for build_file in ("build.gradle" if variant.is_gradle else "pom.xml", "settings.gradle"):
        if (target / build_file).exists():
            replace_sections(target / build_file, sections)

    package_folder = target / "test" / PACKAGE_FOLDER_PLACEHOLDER
    if variant.sequential:
        stage_pom = test_dir / "stagePom.xml"
        for stage in BUILD_STAGES:
            stage_dir = target / stage
            if not variant.is_gradle and stage_pom.exists():
                stage_dir.mkdir(parents=True, exist_ok=True)
                shutil.copy2(stage_pom, stage_dir / "pom.xml")
            _copy_flat(test_dir / "testFiles" / stage, stage_dir / "test" / PACKAGE_FOLDER_PLACEHOLDER)
    else:
        _copy_flat(test_dir / "testFiles", package_folder)
        if variant.static_code_analysis:
            _copy_tree(test_dir / "staticCodeAnalysisConfig", target / "staticCodeAnalysisConfig")

    assignment = target / ASSIGNMENT_DIRECTORY
    _copy_tree(language_dir / "solution", assignment)
    if project_type_dir is not None:
        _copy_tree(project_type_dir / "solution", assignment)

    replace_placeholders(target, variant)
