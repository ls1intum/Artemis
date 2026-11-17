"""Reads the exercise build images from ``application.yml`` and maps them to the repository they are built from."""

import re
from dataclasses import dataclass
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
TEMPLATES_DIR = REPO_ROOT / "src" / "main" / "resources" / "templates"
APPLICATION_YML = REPO_ROOT / "src" / "main" / "resources" / "config" / "application.yml"

IMAGES_PATH = ("artemis", "continuous-integration", "build", "images")


@dataclass(frozen=True)
class ImageSource:
    """The repository an image is built from. The image tag is also a git tag of that repository."""

    github_repository: str
    # The directory of the image repository that holds the cached project, empty when the repository has several
    template_directory: str = ""


# Image repository (without the tag) -> where its dependency cache is defined.
IMAGE_SOURCES = {
    "ls1tum/artemis-maven-template": ImageSource("ls1intum/artemis-maven-docker", "artemis-java-template"),
    "ghcr.io/ls1intum/artemis-kotlin-docker": ImageSource("ls1intum/artemis-kotlin-docker", "artemis-kotlin-template"),
    "ghcr.io/ls1intum/artemis-rust-docker": ImageSource("ls1intum/artemis-rust-docker", "test"),
    "ghcr.io/ls1intum/artemis-javascript-docker": ImageSource("ls1intum/artemis-javascript-docker"),
    "ghcr.io/ls1intum/artemis-swift-swiftlint-docker": ImageSource("ls1intum/artemis-swift-swiftlint-docker", "test"),
}


def split_image(image: str) -> tuple[str, str]:
    """Splits ``registry/name:tag`` into the name and the tag."""
    if "@" in image:
        raise ValueError(f"The image {image} is pinned by digest and has no Git tag to read")
    name, separator, tag = image.rpartition(":")
    if not separator or not name or not tag or "/" in tag:
        raise ValueError(f"The image {image} has no tag")
    return name, tag


def _indent(line: str) -> int:
    return len(line) - len(line.lstrip(" "))


def _strip_comment(line: str) -> str:
    return re.sub(r"\s+#.*$", "", line).rstrip()


def read_mapping(text: str, path: tuple[str, ...]) -> dict[str, dict[str, str]]:
    """Reads ``key: value`` pairs nested two levels below ``path``, e.g. ``images`` -> language -> variant -> image.

    The file is not parsed as a whole: it holds lists and anchors this script never needs, and a YAML library is not
    available on every machine that runs the checks. Only the block below the dotted path is read, by indentation.
    """
    lines = text.splitlines()
    position = 0
    indent = -1
    for key in path:
        # Only a direct child of the block found before counts, and the search ends where that block ends: otherwise a missing
        # `build.images` would be answered by the `images` of a sibling such as `continuous-integration.other`.
        child_indent: int | None = None
        found = False
        while position < len(lines):
            line = lines[position]
            position += 1
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            if _indent(line) <= indent:
                break
            if child_indent is None:
                child_indent = _indent(line)
            if _indent(line) == child_indent and _strip_comment(line).strip() == f"{key}:":
                indent = _indent(line)
                found = True
                break
        if not found:
            raise ValueError(f"{'.'.join(path)} not found in the configuration")

    result: dict[str, dict[str, str]] = {}
    language: str | None = None
    language_indent = -1
    while position < len(lines):
        line = _strip_comment(lines[position])
        position += 1
        if not line.strip() or lines[position - 1].lstrip().startswith("#"):
            continue
        if _indent(line) <= indent:
            break
        key, _, value = line.strip().partition(":")
        value = value.strip().strip("\"'")
        if not value:
            language, language_indent = key, _indent(line)
            result[language] = {}
        elif language is not None and _indent(line) > language_indent:
            result[language][key] = value
    return result


def configured_images(application_yml: Path = APPLICATION_YML) -> dict[str, dict[str, str]]:
    return read_mapping(application_yml.read_text(encoding="utf-8"), IMAGES_PATH)
