"""Assembles the test and assignment repositories of Java and Kotlin exercises from the templates.

This follows ``ProgrammingExerciseRepositoryService``: which template directories are copied and in which order, the
optional sections of the build files, the sequential build stages and the placeholders. The repositories are built with
the default settings of an instance without a Maven Central mirror. When the service changes, this has to follow; a
layout that no longer matches makes the offline build fail, which is the signal to adjust it.
"""

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


@dataclass(frozen=True)
class Variant:
    """One way to configure a Java or Kotlin exercise that changes the build."""

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


def _rename_package_folders(root: Path) -> None:
    package_path = Path(*PACKAGE_NAME.split("."))
    for folder in sorted(root.rglob(PACKAGE_FOLDER_PLACEHOLDER), key=lambda path: -len(path.parts)):
        if folder.is_dir():
            target = folder.parent / package_path
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copytree(folder, target, dirs_exist_ok=True)
            shutil.rmtree(folder)


def replace_placeholders(root: Path, variant: Variant) -> None:
    _rename_package_folders(root)
    replacements = {
        "${packageName}": PACKAGE_NAME,
        "${exerciseNamePomXml}": EXERCISE_TITLE.replace(" ", "-"),
        "${exerciseName}": EXERCISE_TITLE,
        "${packaging}": "pom" if variant.sequential else "jar",
        "${studentParentWorkingDirectoryName}": ASSIGNMENT_DIRECTORY,
        "${studentWorkingDirectory}": f"/{ASSIGNMENT_DIRECTORY}/src",
        "${testWorkingDirectory}": "tests",
        "${solutionWorkingDirectory}": "solution",
    }
    if variant.is_gradle:
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


def assemble(templates_dir: Path, variant: Variant, target: Path) -> None:
    """Writes the test repository to ``target`` and the solution as its ``assignment`` directory."""
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
