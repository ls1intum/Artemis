"""Compares the dependencies the exercise templates pin with the ones the build images cache.

An image holds the dependencies of a template project that lives in its own repository. When a template here pins a
version the image does not hold, every build of that exercise downloads it again. The image tag is also a git tag of
the image repository, so the template project the image was built from can be read without pulling the image, and
the versions compared without resolving a single dependency.
"""

import json
import re
import tomllib
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
    # The templates pin the dependencies in their lock files. The test crate builds the assignment as a path dependency, so the
    # assignment's own lock file only matters for the static code analysis, which runs cargo in the assignment directory.
    "rust": [
        Source("rust/test/Cargo.lock", "Cargo.lock"),
        Source("rust/test/rust_template_test_macros/Cargo.lock", "rust_template_test_macros/Cargo.lock"),
        Source("rust/exercise/Cargo.lock", "assignment/Cargo.lock"),
        Source("rust/solution/Cargo.lock", "assignment/Cargo.lock"),
    ],
    # The image caches the npm packages of the test projects (regular and with static code analysis) of both languages. The test
    # project builds the assignment as an npm workspace, so its lock file also pins the dependencies of the assignment.
    "javascript": [
        Source("javascript/test/package-lock.json", "vitest/javascript/package-lock.json"),
        Source("javascript/staticCodeAnalysis/test/package-lock.json", "vitest/javascript/staticCodeAnalysis/package-lock.json"),
    ],
    "typescript": [
        Source("typescript/test/package-lock.json", "vitest/typescript/package-lock.json"),
        Source("typescript/staticCodeAnalysis/test/package-lock.json", "vitest/typescript/staticCodeAnalysis/package-lock.json"),
    ],
    # The template pins no Package.resolved. The image holds the clones of the dependencies in the cache of SwiftPM, which finds any
    # version a requirement allows in them, so the declared packages and requirements have to be the same.
    "swift": [Source("swift/test/Package.swift", "Package.swift")],
    # `bundler install` runs in the test directory with the lock file of the template. The image holds the gems of the lock file of its
    # own project, which has to contain every gem of every template lock file (the regular and the static code analysis test project).
    "ruby": [
        Source("ruby/test/Gemfile.lock", "Gemfile.lock"),
        Source("ruby/staticCodeAnalysis/test/Gemfile.lock", "Gemfile.lock"),
        Source("ruby/exercise/Gemfile.lock", "Gemfile.lock"),
        Source("ruby/solution/Gemfile.lock", "Gemfile.lock"),
        Source("ruby/staticCodeAnalysis/exercise/Gemfile.lock", "Gemfile.lock"),
        Source("ruby/staticCodeAnalysis/solution/Gemfile.lock", "Gemfile.lock"),
    ],
    # `dart pub get` runs in the test and assignment directories with the lock files of the templates. The test project depends on the
    # assignment through a path, which is not downloaded.
    "dart": [
        Source("dart/test/pubspec.lock", "pubspec.lock"),
        Source("dart/exercise/pubspec.lock", "pubspec.lock"),
        Source("dart/solution/pubspec.lock", "pubspec.lock"),
    ],
    # The image restores the NuGet packages of its test project. The assignment is a project reference, so its target framework has
    # to be one the packages of the test project (and the runtime packs of the SDK) were restored for.
    "c_sharp": [
        Source("c_sharp/test/test.csproj", "test.csproj"),
        Source("c_sharp/exercise/assignment.csproj", "test.csproj"),
        Source("c_sharp/solution/assignment.csproj", "test.csproj"),
    ],
    # The image is a Go toolchain without cached modules and sets GOTOOLCHAIN=local. A `go` directive newer than its toolchain makes every
    # build fail ("requires go >= ..."), so the templates must not ask for more than the image has. A `toolchain` line is only a suggestion
    # that Go ignores with GOTOOLCHAIN=local, so it is not compared.
    "go": [
        Source("go/test/go.mod", "Dockerfile"),
        Source("go/exercise/go.mod", "Dockerfile"),
        Source("go/solution/go.mod", "Dockerfile"),
    ],
    # The image caches the Stackage snapshot (global project) and the test libraries of the test project. The resolver of the templates
    # has to be the snapshot of the image, and the test project may only use packages the image cached (the local libraries and `base`
    # excepted, they are not downloaded).
    "haskell": [
        Source("haskell/test/stack.yaml", "stack-config/global-project/stack.yaml"),
        Source("haskell/exercise/stack.yaml", "stack-config/global-project/stack.yaml"),
        Source("haskell/solution/stack.yaml", "stack-config/global-project/stack.yaml"),
        Source("haskell/test/test.cabal", "Dockerfile"),
        Source("haskell/exercise/exercise.cabal", "Dockerfile"),
        Source("haskell/solution/solution.cabal", "Dockerfile"),
    ],
}
# The coordinate of the Go version; it has its own name so that no dependency (an npm package called `go`) can be mistaken for it
GO_TOOLCHAIN = "go toolchain"
# Coordinates whose template version may be lower than the one of the image (a toolchain that is at least as new as requested)
AT_MOST_COORDINATES = {GO_TOOLCHAIN}
# Packages every GHC installation has without a download
HASKELL_BOOT_PACKAGES = {"base"}


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


def cargo_lock_versions(text: str) -> Versions:
    """Versions of the crates a ``Cargo.lock`` resolves from a registry or a repository, by crate name. Path crates are built, not downloaded.

    The same version of a crate from a repository can come from another commit, so its source with the locked commit is part of the value.
    """
    versions: Versions = {}
    for package in tomllib.loads(text).get("package", []):
        source = package.get("source")
        if source:
            versions.setdefault(package["name"], set()).add(f"{package['version']} ({source})" if source.startswith("git+") else package["version"])
    return versions


def swift_package_versions(text: str) -> Versions:
    """The package dependencies a ``Package.swift`` declares with their requirement, by repository URL."""
    code = "\n".join(re.sub(r"(^|\s)//.*$", "", line) for line in text.splitlines())
    versions: Versions = {}
    for start in re.finditer(r"\.package\(", code):
        depth, position = 1, start.end()
        while depth and position < len(code):
            depth += {"(": 1, ")": -1}.get(code[position], 0)
            position += 1
        declaration = code[start.end() : position - 1]
        url = re.search(r"""url:\s*"([^"]+)"\s*,?""", declaration)
        if url:
            requirement = " ".join(declaration[url.end() :].split())
            versions.setdefault(url.group(1).removesuffix(".git"), set()).add(requirement or "any version")
    return versions


def npm_lock_versions(text: str) -> Versions:
    """Versions of the packages an npm ``package-lock.json`` installs, by package name. Workspaces and links are not downloaded.

    The same version of a package from a repository can come from another commit, so its source with the locked commit is part of the value.
    """
    versions: Versions = {}
    for path, package in json.loads(text).get("packages", {}).items():
        if "node_modules" not in path.split("/") or package.get("link"):
            continue
        resolved = package.get("resolved", "")
        if resolved.startswith("git+"):
            # the manifest of a package from a repository does not have to declare a version
            version = f"{package.get('version', '')} ({resolved})".strip()
        elif "version" in package:
            version = package["version"]
        else:
            continue
        versions.setdefault(path.rpartition("node_modules/")[2], set()).add(version)
    return versions


def gemfile_lock_versions(text: str) -> Versions:
    """Versions of the gems a ``Gemfile.lock`` resolves from a registry or a repository, by gem name. Path gems are not downloaded.

    The same version of a gem from a repository can come from another commit, so its repository with the locked revision is part of the value.
    """
    versions: Versions = {}
    section: str | None = None
    source = ""
    in_specs = False
    for line in text.splitlines():
        if line and not line.startswith(" "):
            section, source, in_specs = line.strip(), "", False
            continue
        field = re.fullmatch(r"  (remote|revision): (.+)", line)
        if field and section == "GIT":
            source += f" {field.group(2)}" if field.group(1) == "remote" else f"@{field.group(2)}"
        elif line == "  specs:":
            in_specs = True
        elif in_specs and section in ("GEM", "GIT"):
            gem = re.fullmatch(r"    (\S+) \(([^)]+)\)", line)
            if gem:
                versions.setdefault(gem.group(1), set()).add(f"{gem.group(2)} (git {source.strip()})" if section == "GIT" else gem.group(2))
    return versions


def pubspec_lock_versions(text: str) -> Versions:
    """Versions of the packages a ``pubspec.lock`` downloads, by package name. Path and SDK packages are not downloaded.

    A package from a repository is identified by its repository and the locked commit as well as by its version.
    """
    versions: Versions = {}
    name: str | None = None
    fields: dict[str, str] = {}
    description: dict[str, str] = {}
    in_description = False

    def finish() -> None:
        if name is None or "version" not in fields:
            return
        source = fields.get("source")
        if source == "hosted":
            versions.setdefault(name, set()).add(fields["version"])
        elif source == "git":
            versions.setdefault(name, set()).add(f"{fields['version']} ({description.get('url', '')}@{description.get('resolved-ref', '')})")

    in_packages = False
    for line in text.splitlines():
        if re.fullmatch(r"\S.*", line):
            finish()
            name, fields, description, in_description = None, {}, {}, False
            in_packages = line.strip() == "packages:"
            continue
        if not in_packages:
            continue
        package = re.fullmatch(r"  (\S+):", line)
        if package:
            finish()
            name, fields, description, in_description = package.group(1), {}, {}, False
            continue
        field = re.fullmatch(r"    ([\w-]+):(?: (.*))?", line)
        if field:
            value = (field.group(2) or "").strip().strip("\"'")
            in_description = field.group(1) == "description" and not value
            if value:
                fields[field.group(1)] = value
            continue
        nested = re.fullmatch(r"      ([\w-]+): (.*)", line)
        if nested and in_description:
            description[nested.group(1)] = nested.group(2).strip().strip("\"'")
    finish()
    return versions


def _nuget_version(version: str) -> str:
    """A version in the notation of an exact version (``[3.14.0]``) restores the same package as ``3.14.0``, whose lowest applicable version is that one.

    Other intervals and floating versions are kept as they are: they can restore different packages and are not compared as equal to a plain version.
    """
    exact = re.fullmatch(r"\[\s*([^\s,\[\]()]+)\s*\]", version.strip())
    return exact.group(1) if exact else version.strip()


def csproj_versions(text: str) -> Versions:
    """The NuGet packages a project file references, by lower-case package name, and its target frameworks."""
    if "<!DOCTYPE" in text or "<!ENTITY" in text:
        raise ValueError("A build file with a DOCTYPE or entity declaration is not read")
    root = ET.fromstring(text.lstrip("\ufeff"))  # noqa: S314 - no DOCTYPE or entity declaration, checked above
    for element in root.iter():
        element.tag = element.tag.rpartition("}")[2]
    versions: Versions = {}
    for element in root.iter():
        if element.tag in ("TargetFramework", "TargetFrameworks"):
            versions.setdefault("target framework", set()).update(framework.strip() for framework in (element.text or "").split(";") if framework.strip())
        elif element.tag in ("PackageReference", "PackageVersion"):
            name = element.get("Include") or element.get("Update")
            version = element.get("Version") or (element.findtext("Version") or "").strip() or element.get("VersionOverride")
            if name and version:
                versions.setdefault(name.lower(), set()).add(_nuget_version(version))
    return versions


GO_VERSION = re.compile(r"(\d+)\.(\d+)(?:\.(\d+))?(?:(beta|rc)(\d+))?")


def _release(version: str) -> tuple[int, ...]:
    """A Go version as a comparable tuple, in the order of Go: ``1.24`` (language version) < ``1.24rc1`` < ``1.24.0`` < ``1.24.2``.

    Raises ``ValueError`` for a version that is not in the notation of Go, which a comparison could not rely on.
    """
    match = GO_VERSION.fullmatch(version)
    if not match:
        raise ValueError(f"{version} is not a Go version")
    major, minor, patch, stage, stage_number = match.groups()
    if patch is None and stage is None and int(major) == 1 and int(minor) >= 21:
        # Since Go 1.21 `1.24` is the language version, which any toolchain of that release accepts, the release candidates included
        # (``gover.Compare``: 1.24 < 1.24rc1 < 1.24.0). Before that, `1.20` was the release itself.
        return int(major), int(minor), 0, -1, 0
    return int(major), int(minor), int(patch or 0), {"beta": 0, "rc": 1, None: 2}[stage], int(stage_number or 0)


def go_mod_versions(text: str) -> Versions:
    """The Go version a ``go.mod`` requires (its ``go`` directive).

    The ``toolchain`` line is not read: it only suggests a toolchain to switch to, and the image sets ``GOTOOLCHAIN=local``, so Go builds with
    its own toolchain as long as that satisfies the ``go`` directive (``go 1.23`` with ``toolchain go1.24.0`` builds with Go 1.23.3).
    """
    versions: Versions = {}
    for line in text.splitlines():
        # The version ends at whitespace or at a comment, which may follow it directly (``go 1.23.2//pinned``)
        match = re.fullmatch(r"\s*go\s+([^\s/]+)\s*(?://.*)?", line)
        if match:
            versions.setdefault(GO_TOOLCHAIN, set()).add(match.group(1))
    return versions


def stack_resolver(text: str) -> Versions:
    """The Stackage snapshot (``resolver``, or its alias ``snapshot``) of a ``stack.yaml``, with or without quotes."""
    match = re.search(r"^(?:resolver|snapshot):\s*[\"']?([^\s\"'#]+)", text, re.MULTILINE)
    return {"resolver": {match.group(1)}} if match else {}


def _top_level_commas(text: str) -> list[str]:
    """``text`` split at the commas outside of braces: ``pkg:{lib1,lib2}`` names several libraries of one package."""
    parts: list[str] = []
    depth = 0
    start = 0
    for index, character in enumerate(text):
        if character == "{":
            depth += 1
        elif character == "}":
            depth = max(depth - 1, 0)
        elif character == "," and depth == 0:
            parts.append(text[start:index])
            start = index + 1
    parts.append(text[start:])
    return parts


def cabal_packages(text: str) -> Versions:
    """The packages a cabal file depends on without its own libraries and ``base``.

    Executables and test suites are no dependencies, so a package that has the name of one of them is still compared.

    Only the names are compared, not the version bounds (``tasty == 1.*``): the image records a cached package without a version. A bound that
    the snapshot of the image cannot satisfy is not found by the pull request check but by the nightly build without network access.
    """
    local = {match.group(1) for match in re.finditer(r"^library\s+(\S+)", text, re.MULTILINE)}
    local |= {match.group(1) for match in re.finditer(r"^name:\s*(\S+)", text, re.MULTILINE)}
    packages: set[str] = set()
    lines = [line for line in text.splitlines() if not line.lstrip().startswith("--")]
    for number, line in enumerate(lines):
        field = re.fullmatch(r"(\s*)build-depends:\s*(.*)", line)
        if not field:
            continue
        entries = [field.group(2)]
        for following in lines[number + 1 :]:
            if following.strip() and len(following) - len(following.lstrip()) <= len(field.group(1)):
                break
            entries.append(following)
        for entry in _top_level_commas(" ".join(entries)):
            match = re.match(r"\s*([A-Za-z][\w-]*)", entry)
            if match:
                packages.add(match.group(1))
    return {f"package {name}": {"cached"} for name in sorted(packages - local - HASKELL_BOOT_PACKAGES)}


def dockerfile_versions(text: str) -> Versions:
    """The Go toolchain of the image that is published (the last ``FROM``) and the Haskell packages (``--package``) it builds ahead."""
    # Dockerfile comments are whole lines, also between the lines of a command
    lines = [line for line in text.splitlines() if not line.lstrip().startswith("#")]
    versions: Versions = {}
    stages = [match.group(1) for line in lines if (match := re.match(r"\s*FROM\s+(?:--\S+\s+)*(\S+)", line, re.IGNORECASE))]
    published = re.fullmatch(r"(?:\S*/)?golang:(\d+\.\d+(?:\.\d+)?(?:(?:beta|rc)\d+)?)(?:[-@]\S*)?", stages[-1]) if stages else None
    if published:
        tag = published.group(1)
        # `golang:1.24` is a rolling tag, which holds the latest patch release of its minor version at the time of the build. The tag cannot
        # tell which one, so it names no version.
        versions[GO_TOOLCHAIN] = {tag if GO_VERSION.fullmatch(tag) and (tag.count(".") == 2 or "rc" in tag or "beta" in tag) else f"{tag} (rolling tag)"}
    # Only the arguments of a `stack` command of a RUN instruction build the cache, not the same text in an `echo` or another command
    for instruction in re.sub(r"\\\s*\n", " ", "\n".join(lines)).splitlines():
        if not re.match(r"\s*RUN\s", instruction, re.IGNORECASE):
            continue
        for command in re.split(r"&&|\|\||[;|]", instruction):
            if re.match(r"\s*(?:RUN\s+)?(?:\w+=\S*\s+)*stack\s", command, re.IGNORECASE):
                # Behind a standalone `--` the arguments belong to the program that stack runs (`stack exec echo -- --package x`)
                stack_options = re.split(r"\s--(?:\s|$)", command, maxsplit=1)[0]
                for match in re.finditer(r"--package\s+([A-Za-z][\w-]*)", stack_options):
                    versions[f"package {match.group(1)}"] = {"cached"}
    return versions


def versions_of(file_name: str, text: str, properties: dict[str, str] | None = None) -> Versions:
    if file_name.endswith("go.mod"):
        return go_mod_versions(text)
    if file_name.endswith("stack.yaml"):
        return stack_resolver(text)
    if file_name.endswith(".cabal"):
        return cabal_packages(text)
    if file_name.endswith("Dockerfile"):
        return dockerfile_versions(text)
    if file_name.endswith("Cargo.lock"):
        return cargo_lock_versions(text)
    if file_name.endswith("Gemfile.lock"):
        return gemfile_lock_versions(text)
    if file_name.endswith("pubspec.lock"):
        return pubspec_lock_versions(text)
    if file_name.endswith(".csproj"):
        return csproj_versions(text)
    if file_name.endswith("package-lock.json"):
        return npm_lock_versions(text)
    if file_name.endswith("Package.swift"):
        return swift_package_versions(text)
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
            # A package the image caches has no version to name (``cached``)
            label = coordinate if version == "cached" else f"{coordinate} {version}"
            if held is None:
                problems.append(f"{label} is not in the image")
            elif coordinate in AT_MOST_COORDINATES:
                try:
                    wanted_release = _release(version)
                except ValueError as error:
                    problems.append(f"{label} cannot be compared with the image ({', '.join(sorted(held))}): {error}")
                    continue
                try:
                    if any(wanted_release > _release(newest) for newest in held):
                        problems.append(f"{label} is newer than the image ({', '.join(sorted(held))})")
                except ValueError:
                    problems.append(f"{label} cannot be compared: the image does not name a full Go version ({', '.join(sorted(held))}), pin a patch release such as golang:1.23.3")
            elif version not in held:
                problems.append(f"{label} differs from the image ({', '.join(sorted(held))})")
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
    base_url = "/".join(part for part in ("https://raw.githubusercontent.com", source.github_repository, tag, source.template_directory) if part)
    image_files: dict[str, Versions] = {}
    problems = []
    try:
        for entry in SOURCES[language]:
            if entry.image_file not in image_files:
                properties = gradle_properties(fetch(f"{base_url}/{entry.image_properties}")) if entry.image_properties else None
                image_files[entry.image_file] = versions_of(entry.image_file, fetch(f"{base_url}/{entry.image_file}"), properties)
            template = versions_of(entry.template_file, (templates_dir / entry.template_file).read_text(encoding="utf-8"))
            if not template and entry.template_file.endswith(("go.mod", "stack.yaml")):
                # Nothing to compare would pass for any image
                problems.append(f"{entry.template_file}: no Go version or Stack snapshot found, the file has to name one")
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
