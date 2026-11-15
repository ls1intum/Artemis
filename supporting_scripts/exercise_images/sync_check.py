"""Compares the dependencies the exercise templates pin with the ones the build images cache.

An image holds the dependencies of a template project that lives in its own repository. When a template here pins a
version the image does not hold, every build of that exercise downloads it again. The image tag is also a git tag of
the image repository, so the template project the image was built from can be read without pulling the image, and
the versions compared without resolving a single dependency.
"""

import re
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

from .images import IMAGE_SOURCES, TEMPLATES_DIR, configured_images, split_image

Versions = dict[str, set[str]]
Fetch = Callable[[str], str]

MAVEN_PLUGIN_GROUP = "org.apache.maven.plugins"
PROPERTY_REFERENCE = re.compile(r"\$\{([^}]+)}|\$([A-Za-z_]\w*)")


@dataclass(frozen=True)
class Source:
    """A build file of the templates and the file of the image's template project it has to agree with."""

    template_file: str
    image_file: str
    # The image's Gradle project keeps some versions as properties in this file
    image_properties: str | None = None


# Template file below ``templates/`` and the file of the image repository's template project, per language.
SOURCES = {
    "java": [
        Source("java/test/maven/projectTemplate/pom.xml", "pom.xml"),
        Source("java/maven_maven/test/projectTemplate/pom.xml", "pom.xml"),
        Source("java/test/stagePom.xml", "pom.xml"),
        Source("java/test/gradle/projectTemplate/build.gradle", "build.gradle", image_properties="gradle.properties"),
        Source("java/test/gradle/projectTemplate/settings.gradle", "settings.gradle", image_properties="gradle.properties"),
        Source("java/test/gradle/projectTemplate/gradle/wrapper/gradle-wrapper.properties", "gradle/wrapper/gradle-wrapper.properties"),
    ],
    "kotlin": [
        Source("kotlin/test/maven/projectTemplate/pom.xml", "pom.xml"),
        Source("kotlin/test/stagePom.xml", "pom.xml"),
    ],
}


def _resolve(value: str, properties: dict[str, str]) -> str:
    for _ in range(5):
        resolved = PROPERTY_REFERENCE.sub(lambda match: properties.get(match.group(1) or match.group(2), match.group(0)), value)
        if resolved == value:
            break
        value = resolved
    return value


def maven_versions(text: str) -> Versions:
    """Versions of the dependencies, plugins and plugin artifacts a pom declares, by ``group:artifact``."""
    if "<!DOCTYPE" in text or "<!ENTITY" in text:
        # The standard library parser expands entities. A build file has no use for them, so refuse instead of expanding.
        raise ValueError("A build file with a DOCTYPE or entity declaration is not read")
    root = ET.fromstring(text)  # noqa: S314 - no DOCTYPE or entity declaration, checked above
    for element in root.iter():
        element.tag = element.tag.rpartition("}")[2]
    properties = {child.tag: (child.text or "").strip() for properties in root.iter("properties") for child in properties}
    versions: Versions = {}
    for element in root.iter():
        if element.tag not in ("dependency", "plugin", "artifactItem", "extension"):
            continue
        fields = {child.tag: (child.text or "").strip() for child in element}
        if "artifactId" not in fields or not fields.get("version"):
            continue
        group = fields.get("groupId") or (MAVEN_PLUGIN_GROUP if element.tag == "plugin" else "")
        versions.setdefault(f"{group}:{fields['artifactId']}", set()).add(_resolve(fields["version"], properties))
    return versions


def gradle_properties(text: str) -> dict[str, str]:
    """The ``name=value`` pairs of a ``gradle.properties`` file."""
    return {key.strip(): value.strip().strip("'\"") for key, _, value in (line.partition("=") for line in text.splitlines()) if value and not key.lstrip().startswith("#")}


# The Gradle plugins of the static code analysis tools resolve the tool as a dependency of its own. The template pins it with
# `toolVersion` in the tool's block, an image may declare the same artifact as a dependency of the tool's configuration.
GRADLE_TOOL_COORDINATES = {"checkstyle": "com.puppycrawl.tools:checkstyle", "pmd": "net.sourceforge.pmd:pmd-java", "spotbugs": "com.github.spotbugs:spotbugs"}


def _blocks(code: str, name: str) -> list[str]:
    """The bodies of the named ``name { ... }`` blocks of a Gradle file, nested braces included."""
    bodies = []
    for start in re.finditer(rf"(?m)^[ \t]*{name}\s*\{{", code):
        depth, position = 1, start.end()
        while depth and position < len(code):
            depth += {"{": 1, "}": -1}.get(code[position], 0)
            position += 1
        bodies.append(code[start.end() : position - 1])
    return bodies


def gradle_versions(text: str, properties: dict[str, str] | None = None) -> Versions:
    """Plugin and dependency versions of a Gradle build or settings file, by ``group:artifact`` or ``plugin:id``."""
    code = "\n".join(re.sub(r"(^|\s)//.*$", "", line) for line in text.splitlines())
    properties = {**(properties or {}), **dict(re.findall(r"\b(\w+)\s*=\s*['\"]([^'\"$]+)['\"]", code))}
    versions: Versions = {}
    for plugin, version in re.findall(r"""\bid\s*\(?\s*['"]([\w.\-]+)['"]\s*\)?\s*version\s*\(?\s*['"]([^'"]+)['"]""", code):
        versions.setdefault(f"plugin:{plugin}", set()).add(version)
    for group, artifact, version in re.findall(r"""['"]([\w.\-]+):([\w.\-]+):([^'":\s]+)['"]""", code):
        versions.setdefault(f"{group}:{artifact}", set()).add(_resolve(version, properties))
    for tool, coordinate in GRADLE_TOOL_COORDINATES.items():
        for body in _blocks(code, tool):
            for version in re.findall(r"""\btoolVersion\s*=\s*['"]([^'"]+)['"]""", body):
                versions.setdefault(coordinate, set()).add(_resolve(version, properties))
    return versions


def wrapper_versions(text: str) -> Versions:
    found = re.search(r"gradle-([\d.]+(?:-[a-z]+-\d+)?)-(?:bin|all)\.zip", text)
    return {"gradle-wrapper": {found.group(1)}} if found else {}


def versions_of(file_name: str, text: str, properties: dict[str, str] | None = None) -> Versions:
    if file_name.endswith(".xml"):
        return maven_versions(text)
    if file_name.endswith(".properties"):
        return wrapper_versions(text)
    return gradle_versions(text, properties)


def compare(template: Versions, image: Versions) -> list[str]:
    """Describes every version the template pins that the image does not hold."""
    problems = []
    for coordinate, wanted in sorted(template.items()):
        held = image.get(coordinate)
        for version in sorted(wanted):
            if held is None:
                problems.append(f"{coordinate} {version} is not in the image")
            elif version not in held:
                problems.append(f"{coordinate} {version} differs from the image ({', '.join(sorted(held))})")
    return problems


def fetch_raw_github(url: str, attempts: int = 4) -> str:
    """Reads a public repository file, retrying network failures, rate limits and server errors with a growing pause."""
    for attempt in range(attempts):
        try:
            with urllib.request.urlopen(url, timeout=30) as response:
                return response.read().decode("utf-8")
        except (urllib.error.URLError, TimeoutError) as error:
            if isinstance(error, urllib.error.HTTPError):
                error.close()
                if error.code != 429 and not 500 <= error.code < 600:
                    raise
            if attempt == attempts - 1:
                raise
            time.sleep(3 * 2**attempt)
    raise AssertionError("unreachable")


def check_language(language: str, image: str, templates_dir: Path = TEMPLATES_DIR, fetch: Fetch = fetch_raw_github) -> list[str]:
    """Problems of one language's templates against its image, empty when they agree."""
    try:
        name, tag = split_image(image)
    except ValueError as error:
        return [str(error)]
    source = IMAGE_SOURCES.get(name)
    if source is None:
        return [f"No source repository is configured for image {image}"]
    base_url = f"https://raw.githubusercontent.com/{source.github_repository}/{tag}/{source.template_directory}"
    image_files: dict[str, Versions] = {}
    problems = []
    try:
        for entry in SOURCES[language]:
            if entry.image_file not in image_files:
                properties = gradle_properties(fetch(f"{base_url}/{entry.image_properties}")) if entry.image_properties else None
                image_files[entry.image_file] = versions_of(entry.image_file, fetch(f"{base_url}/{entry.image_file}"), properties)
            template = versions_of(entry.template_file, (templates_dir / entry.template_file).read_text(encoding="utf-8"))
            problems += [f"{entry.template_file}: {problem}" for problem in compare(template, image_files[entry.image_file])]
    except (urllib.error.URLError, TimeoutError) as error:
        url = error.geturl() if isinstance(error, urllib.error.HTTPError) else base_url
        if isinstance(error, urllib.error.HTTPError):
            error.close()
        problems.append(f"Could not read image {image} at git tag {tag} from {url}: {error}")
    return problems


def check(templates_dir: Path = TEMPLATES_DIR, application_yml: Path | None = None, fetch: Fetch = fetch_raw_github) -> dict[str, tuple[str, list[str]]]:
    """Checks every language with a dedicated image. Returns language -> (image, problems)."""
    images = configured_images(application_yml) if application_yml else configured_images()
    return {language: (images[language]["default"], check_language(language, images[language]["default"], templates_dir, fetch)) for language in SOURCES}
