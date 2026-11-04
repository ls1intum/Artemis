#!/usr/bin/env python3
"""Fail when the documentation states a fact that the repository contradicts.

check_docs_references.py proves that the names in the documentation exist. This script proves that
the numbers, commands and examples next to those names are still true. Every check reports only
what it can prove, and every exception has an entry with a reason below.

  * Versions. "Java 25", "Node 24", "Angular 22", "Spring Boot 4.1", "PostgreSQL 18" and "Gradle 9"
    in the prose have to agree with the build files that pin them (build.gradle, gradle.properties,
    pnpm-workspace.yaml, .env and the Gradle wrapper). A claim may name fewer segments than the pin:
    "Node 24" matches 24.21.0.
  * Defaults. A table row or a sentence that gives the default of an `artemis.` property has to
    agree with the value in the YAML configuration, when the YAML sets one.
  * Commands. `pnpm run <script>` has to name a script of a package.json, `./gradlew <task>` a task
    that a Gradle file or a plugin defines, `--spring.profiles.active=<profile>` a profile that
    exists, and a repository path in a shell example has to exist.
  * Routes. A client route such as `/course-management/<id>/exercises` or a server path such as
    `/api/core/courses` has to consist of segments that the route definitions or the mapping
    annotations contain.
  * Images. An image that no page references, an image without alt text and an image above the size
    budget are reported. WebP is deliberately not required: see the documentation guideline.
  * Examples. A fenced JSON example has to parse, a shell example has to pass `bash -n`, and a YAML
    example has to use spaces. Blocks with a placeholder (`<name>`, `...`) are skipped.
  * Removal markers. `REMOVE_IN_ARTEMIS_<n>` in the source, and "removed in Artemis <n>" in the
    prose, must be gone once the version in build.gradle reaches <n>.
  * Glossary. The "Avoid" column of the glossary lists words that the pages must not use for a
    glossary term. The glossary is the single source of that vocabulary.

Usage:
    python3 supporting_scripts/check_docs_consistency.py
    python3 supporting_scripts/check_docs_consistency.py --only versions,commands
    python3 supporting_scripts/check_docs_consistency.py --self-test
"""

from __future__ import annotations

import argparse
import json
import posixpath
import re
import subprocess
import sys
from functools import cache
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import check_docs_references as refs  # noqa: E402
from check_docs_references import DOCS_ROOT, FENCE, INLINE_CODE, REPO_ROOT, AREAS, doc_pages, extra_prose, prose_lines, rel  # noqa: E402

GLOSSARY = DOCS_ROOT / "developer" / "glossary.mdx"
IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg"}
# The biggest screenshot is 1.2 MB. A new image above this has to be reduced (crop, fewer colours) first.
IMAGE_BUDGET_BYTES = 1_500_000

# ---------------------------------------------------------------------------------------------
# exceptions, each with the reason that makes the claim true although it differs from the pin
# ---------------------------------------------------------------------------------------------

# "<page>: <claim>" -> reason
VERSION_ALLOWLIST: dict[str, str] = {
    "documentation/docs/developer/best-practices.mdx: Gradle 9.8.1": "records the environment of two dated reproducible builds, which is history, not the current pin",
    "documentation/docs/instructor/communication-support/iris.mdx: Java 21": "a sentence of an example prompt that an instructor gives Iris, not a statement about Artemis",
}

# "<page>: <property>" -> reason
DEFAULT_ALLOWLIST: dict[str, str] = {}

# Route or path segments that exist only at run time or outside the annotations (placeholders are always accepted)
ROUTE_SEGMENT_ALLOWLIST: dict[str, str] = {
    "documentation/docs/admin/deimos.mdx: completions": "path of the external OpenAI compatible API that Deimos calls",
    "documentation/docs/admin/hyperion.mdx: otel": "path of the external Langfuse OTLP receiver",
    "documentation/docs/instructor/integrations/coding-agents.mdx: agent": "path on the documentation site (docs.artemis.tum.de), not on an Artemis server",
    "documentation/docs/instructor/integrations/coding-agents.mdx: openapi.yaml": "file name on the documentation site, see above",
}

# "<page>: <command>" -> reason
COMMAND_ALLOWLIST: dict[str, str] = {
    "documentation/docs/admin/production-setup/additional-tips.mdx: deployment/docker/.docker-data/artemis-mysql-data": "runtime data directory of a deployment that the example fixes the owner of",
}

# JSON, shell and YAML examples that are not meant to parse, "<page>" -> reason
EXAMPLE_ALLOWLIST: dict[str, str] = {
    "documentation/docs/instructor/exercises/programming-exercise/configure-the-build.mdx": "sketches the JSON schema with type names instead of values",
}

# Routes name the client only where the audience uses the client. Admin and developer pages are full of file system paths and external endpoints.
ROUTE_AREAS = ("instructor", "student")

# Tasks that plugins or Gradle itself provide and no build file mentions
GRADLE_BUILTIN_TASKS = {
    "build", "clean", "test", "check", "assemble", "bootRun", "bootJar", "bootWar", "bootBuildImage", "classes", "testClasses", "jar", "war", "javadoc", "dependencies",
    "dependencyInsight", "tasks", "wrapper", "properties", "help", "projects", "spotlessCheck", "spotlessApply", "checkstyleMain", "checkstyleTest", "jacocoTestReport",
    "jacocoTestCoverageVerification", "compileJava", "compileTestJava", "processResources", "installDist", "dependencyUpdates", "cyclonedxBom", "publishToMavenLocal",
    "publish", "buildEnvironment", "outgoingVariants", "resolvableConfigurations", "javaToolchains", "model", "components", "dependentComponents", "init", "installGitHooks",
}

# Options of Gradle that take the next word as their value, which is therefore not a task
GRADLE_OPTIONS_WITH_VALUE = {"-x", "--exclude-task", "--tests", "-p", "--project-dir", "-P", "--init-script", "-I", "--console", "--max-workers", "-b", "--build-file", "-c",
                             "--settings-file", "-g", "--gradle-user-dir", "--project-cache-dir", "--priority", "--warning-mode", "--include-build", "--write-verification-metadata"}

PNPM_BUILTIN_COMMANDS = {
    "install", "i", "add", "remove", "rm", "update", "up", "upgrade", "exec", "dlx", "test", "t", "start", "store", "config", "env", "audit", "outdated", "why", "list", "ls",
    "dedupe", "rebuild", "prune", "patch", "patch-commit", "link", "unlink", "pack", "publish", "init", "create", "run", "switch", "setup", "self-update", "import", "fetch",
    "approve-builds", "licenses", "cache", "root", "bin", "deploy", "doctor", "server", "catalog", "sbom", "with", "cat-file", "cat-index", "find-hash", "ignored-builds",
}

KNOWN_PROFILES_EXTRA = {"dev", "prod", "test", "docker", "default", "jenkins", "localvc", "localci", "buildagent", "scheduling", "core", "bamboo", "gitlab", "athena", "iris", "aeolus",
                        "ldap", "saml2", "openapi", "theia", "lti", "apollon", "hyperion", "aiworker", "weaviate", "deimos", "local", "e2e", "arch", "cloud"}


# ---------------------------------------------------------------------------------------------
# facts of the repository
# ---------------------------------------------------------------------------------------------


def read(path: str) -> str:
    return (REPO_ROOT / path).read_text(encoding="utf-8")


@cache
def tracked_files() -> list[str]:
    out = subprocess.run(["git", "ls-files"], cwd=REPO_ROOT, check=True, capture_output=True, text=True).stdout
    return [line for line in out.splitlines() if (REPO_ROOT / line).is_file()]


@cache
def pinned_versions() -> dict[str, str]:
    """The version that each technology of the documentation is pinned to."""
    versions: dict[str, str] = {}
    m = re.search(r"JavaLanguageVersion\.of\((\d+)\)", read("build.gradle"))
    if m:
        versions["Java"] = m.group(1)
    props = read("gradle.properties")
    for key, name in (("node_version", "Node"), ("spring_boot_version", "Spring Boot")):
        m = re.search(r"^%s=(\S+)" % key, props, re.M)
        if m:
            versions[name] = m.group(1)
    m = re.search(r"^\s*'@angular/core':\s*['\"]?(\d+(?:\.\d+)*)", read("pnpm-workspace.yaml"), re.M)
    if m:
        versions["Angular"] = m.group(1)
    m = re.search(r"^POSTGRES_VERSION=(\S+)", read(".env"), re.M)
    if m:
        versions["PostgreSQL"] = m.group(1)
    m = re.search(r"gradle-(\d+(?:\.\d+)*)-bin", read("gradle/wrapper/gradle-wrapper.properties"))
    if m:
        versions["Gradle"] = m.group(1)
    return versions


def artemis_major() -> int:
    m = re.search(r'^version\s*=\s*"(\d+)\.', read("build.gradle"), re.M)
    return int(m.group(1)) if m else 0


def prose_files() -> list[Path]:
    pages = [page for area in AREAS for page in doc_pages(area)]
    return pages + extra_prose()


# ---------------------------------------------------------------------------------------------
# versions
# ---------------------------------------------------------------------------------------------

VERSION_CLAIM = re.compile(r"\b(Java|Node|Angular|Spring Boot|PostgreSQL|Gradle) (\d+(?:\.\d+)*)\b(?![.]?\d)")
# A sentence that talks about the past or about a range is not a claim about the pin.
HISTORY_WORDS = re.compile(r"\b(?:since|before|older|previous|formerly|earlier|prior|upgrade[ds]? from|migrat\w+ from|up to|down to|at least|minimum|or newer|or later|and later|and above|supports?|"
                           r"exercise|exercises|template|image|images|JDK|Java 17|from)\b", re.I)


def version_matches(claim: str, pin: str) -> bool:
    claimed, pinned = claim.split("."), pin.split(".")
    return pinned[: len(claimed)] == claimed


# The parts of a sentence that can talk about different versions: "Upgraded from Java 21; now requires Java 25" has a historical and a current claim.
CLAUSE_BREAK = re.compile(r"[;:]|\s[\u2013\u2014-]\s|,\s+(?:but|and|now|so)\b|\b(?:but|now|today|currently)\b|\.\s+(?=[A-Z])")


def version_claims(page: Path, text: str) -> list[str]:
    problems = []
    pins = pinned_versions()
    for number, line in prose_lines(text):
        for clause in CLAUSE_BREAK.split(line):
            # only the clause that holds the historical wording is exempt, a current claim next to it is still checked
            if HISTORY_WORDS.search(clause):
                continue
            for m in VERSION_CLAIM.finditer(clause):
                name, claim = m.groups()
                pin = pins.get(name)
                if pin is None or version_matches(claim, pin) or f"{rel(page)}: {name} {claim}" in VERSION_ALLOWLIST:
                    continue
                problems.append(f"{rel(page)}:{number}: `{name} {claim}` but the repository pins {name} {pin}")
    return problems


# ---------------------------------------------------------------------------------------------
# defaults
# ---------------------------------------------------------------------------------------------


def yaml_scalar(raw: str) -> str | None:
    raw = raw.split(" #")[0].strip()
    if not raw or raw in ("|", ">", "|-", ">-"):
        return None
    m = re.fullmatch(r"\$\{[^:}]+:(.*)\}", raw)
    if m:
        raw = m.group(1)
    elif raw.startswith("${"):
        return None
    return raw.strip("'\"")


@cache
def yaml_values() -> dict[str, str]:
    """Flat key -> value of the scalar settings in the YAML of src/main/resources/config. A key set in several files has no single default and is dropped."""
    seen: dict[str, set[str]] = {}
    for path in sorted((REPO_ROOT / "src/main/resources/config").glob("application*.yml")):
        stack: list[tuple[int, str]] = []
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line.strip() or line.lstrip().startswith("#") or line.lstrip().startswith("-") or ":" not in line:
                continue
            indent = len(line) - len(line.lstrip())
            key, _, value = line.strip().partition(":")
            while stack and stack[-1][0] >= indent:
                stack.pop()
            stack.append((indent, key.strip("'\"")))
            scalar = yaml_scalar(value)
            if scalar is not None:
                seen.setdefault(".".join(k for _, k in stack), set()).add(scalar)
    return {key: next(iter(values)) for key, values in seen.items() if len(values) == 1}


DEFAULT_ROW = re.compile(r"^\|\s*`?(artemis\.[a-z0-9.-]+)`?\s*\|(.*)\|\s*$")


def normalize_value(value: str) -> str:
    return value.strip().strip("`'\"").strip().lower()


def default_claims(page: Path, text: str) -> list[str]:
    """A table with a Default column whose rows start with an artemis. property, and a "`property` ... defaults to `x`" sentence."""
    problems = []
    values = yaml_values()
    default_column = None
    for number, line in enumerate(text.splitlines(), 1):
        if line.startswith("|") and re.search(r"\|\s*Default\s*\|", line + "|"):
            cells = [c.strip().lower() for c in line.strip().strip("|").split("|")]
            default_column = cells.index("default")
            continue
        if not line.startswith("|"):
            default_column = None
            continue
        m = DEFAULT_ROW.match(line)
        if m and default_column is not None:
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if default_column < len(cells):
                check_default(page, number, m.group(1), cells[default_column], values, problems)
    for number, line in prose_lines(text):
        for m in re.finditer(r"`(artemis\.[a-z0-9.-]+)`[^.|`]{0,60}?\bdefaults? (?:to|is|of) `([^`]+)`", line):
            check_default(page, number, m.group(1), m.group(2), values, problems)
    return problems


def check_default(page: Path, number: int, key: str, claimed: str, values: dict[str, str], problems: list[str]) -> None:
    actual = values.get(key)
    if actual is None or f"{rel(page)}: {key}" in DEFAULT_ALLOWLIST:
        return
    claimed_value = normalize_value(claimed)
    if claimed_value in ("", "-", "none", "empty", "required", "unset", "n/a", "see below") or "<" in claimed_value:
        return
    if normalize_value(actual) != claimed_value:
        problems.append(f"{rel(page)}:{number}: `{key}` is documented with the default `{claimed.strip()}` but the YAML sets `{actual}`")


# ---------------------------------------------------------------------------------------------
# commands
# ---------------------------------------------------------------------------------------------

SHELL_LANGUAGES = {"sh", "bash", "shell", "zsh", "console", "shell-session"}
# `pnpm --dir documentation run build`: options, some with a value, may precede the command. A word after an option is read as the value only
# when the rest still names a command, because the expression backtracks.
PNPM = re.compile(r"\bpnpm(?:\s+-[\w-]+(?:=\S+|\s+(?!run\b)[^-\s#]\S*)?)*\s+(?:run\s+)?([a-z][\w:.-]*)")
GRADLEW = re.compile(r"\./gradlew((?:\s+[^\s`|&;]+)+)")


@cache
def package_scripts() -> set[str]:
    scripts: set[str] = set()
    for path in tracked_files():
        if path.endswith("package.json") and "node_modules" not in path:
            try:
                scripts.update(json.loads(read(path)).get("scripts", {}))
            except (ValueError, OSError):
                continue
    return scripts


@cache
def gradle_text() -> str:
    parts = []
    for path in tracked_files():
        if path.endswith((".gradle", ".kts")) and path.startswith(("build.gradle", "gradle/", "settings.gradle")):
            parts.append(read(path))
    return "\n".join(parts)


@cache
def known_profiles() -> set[str]:
    profiles = set(KNOWN_PROFILES_EXTRA)
    profiles.update(re.findall(r'PROFILE_\w+\s*=\s*"([\w-]+)"', read("src/main/java/de/tum/cit/aet/artemis/core/config/Constants.java")))
    for path in (REPO_ROOT / "src/main/resources/config").glob("application-*.yml"):
        profiles.add(path.stem.removeprefix("application-"))
    return profiles


SHELL_COMMENT = re.compile(r"(?:^|\s)#.*$")


def code_segments(text: str):
    """(line number, text, is_fenced_shell) for inline code spans in the prose and for the lines of fenced shell examples."""
    fenced, language = False, ""
    for number, line in enumerate(text.splitlines(), 1):
        m = FENCE.match(line)
        if m:
            fenced = not fenced
            language = m.group(2).lower() if fenced else ""
            continue
        if fenced:
            if language in SHELL_LANGUAGES:
                # a comment is prose ("# Requires pnpm and Node 24"), not a command, and a trailing one is cut off as well
                command = SHELL_COMMENT.sub("", line)
                if command.strip():
                    yield number, command, True
        else:
            for span in INLINE_CODE.findall(line):
                yield number, span, False


def command_problems(page: Path, text: str) -> list[str]:
    problems = []
    scripts, gradle = package_scripts(), gradle_text()
    for number, segment, fenced in code_segments(text):
        for m in PNPM.finditer(segment):
            name = m.group(1)
            if name in scripts or name in PNPM_BUILTIN_COMMANDS or f"{rel(page)}: pnpm {name}" in COMMAND_ALLOWLIST:
                continue
            problems.append(f"{rel(page)}:{number}: `pnpm {name}` is neither a script of a package.json nor a pnpm command")
        for m in GRADLEW.finditer(segment):
            skip = False
            for word in m.group(1).split():
                if word == "#":
                    break  # a comment of the shell line
                if skip:
                    skip = False
                    continue
                if word in GRADLE_OPTIONS_WITH_VALUE:
                    skip = True
                    continue
                if word.startswith("-") or any(c in word for c in "<$=\"'") or word in ("\\", "..."):
                    continue
                task = word.split(":")[-1]
                if not task:
                    continue
                if task not in GRADLE_BUILTIN_TASKS and not re.search(r"\b%s\b" % re.escape(task), gradle) and f"{rel(page)}: ./gradlew {task}" not in COMMAND_ALLOWLIST:
                    problems.append(f"{rel(page)}:{number}: `./gradlew {task}` is not a task that a Gradle file or a plugin defines")
        for m in re.finditer(r"(?:--spring\.profiles\.active=|SPRING_PROFILES_ACTIVE[=:]\s*)[\"']?([\w,-]+)", segment):
            for profile in m.group(1).split(","):
                if profile and profile not in known_profiles():
                    problems.append(f"{rel(page)}:{number}: Spring profile `{profile}` does not exist")
        if fenced:
            for token in re.split(r"[\s\"'=]+", segment):
                candidate = token.rstrip(",;")
                if (refs.PATH.match(candidate) and candidate not in refs.PATH_ALLOWLIST and f"{rel(page)}: {candidate}" not in COMMAND_ALLOWLIST and not (REPO_ROOT / candidate).exists()
                        and not refs.is_ignored(candidate)):
                    problems.append(f"{rel(page)}:{number}: `{candidate}` in a shell example does not exist")
    return problems


# ---------------------------------------------------------------------------------------------
# routes and REST paths
# ---------------------------------------------------------------------------------------------

ROUTE_SPAN = re.compile(r"^(/[A-Za-z0-9<>{}:_.-]+)(/[A-Za-z0-9<>{}:_.-]+)+$")
PLACEHOLDER = re.compile(r"^(?::.+|<.+>|\{.+\}|\$.+|\d+)$")


@cache
def client_route_segments() -> set[str]:
    segments: set[str] = set()
    for path in tracked_files():
        if path.startswith("src/main/webapp/app/") and path.endswith(".ts") and not path.endswith((".spec.ts", ".model.ts")) and "/openapi/" not in path:
            for m in re.finditer(r"\bpath:\s*['\"`]([^'\"`]*)['\"`]", read(path)):
                segments.update(s for s in m.group(1).split("/") if s)
    return segments


@cache
def server_path_segments() -> set[str]:
    segments: set[str] = set()
    pattern = re.compile(r'@(?:Get|Post|Put|Delete|Patch|Request)Mapping\s*\(([^)]*)\)|String\s+\w*(?:PATH|ENDPOINT|API)\w*\s*=\s*"([^"]*)"', re.I)
    for path in tracked_files():
        if path.startswith("src/main/java/") and path.endswith(".java"):
            for m in pattern.finditer(read(path)):
                for literal in re.findall(r'"([^"]*)"', m.group(1) or "") + ([m.group(2)] if m.group(2) else []):
                    segments.update(re.split(r"[/{}]+", literal))
    segments.discard("")
    return segments


def route_problems(page: Path, text: str) -> list[str]:
    problems = []
    client, server = client_route_segments(), server_path_segments()
    for number, line in prose_lines(text):
        for span in INLINE_CODE.findall(line):
            candidate = span.strip().rstrip(".,:;")
            if not ROUTE_SPAN.match(candidate) or refs.PATH.match(candidate.lstrip("/")) or "?" in candidate:
                continue
            parts = [p for p in candidate.split("/") if p]
            if parts[0] != "api" and not any(f"/docs/{area}/" in rel(page) for area in ROUTE_AREAS):
                continue
            known = server if parts[0] == "api" else client
            if parts[0] == "api":
                parts = parts[1:]
            if parts and parts[0] not in known and f"{rel(page)}: {parts[0]}" not in ROUTE_SEGMENT_ALLOWLIST and not PLACEHOLDER.match(parts[0]):
                # the first segment decides: an unknown top-level segment is a path that moved, a later one may be a literal of a deep route
                problems.append(f"{rel(page)}:{number}: `{candidate}` starts with `{parts[0]}`, which no {'mapping' if known is server else 'route'} defines")
                continue
            for part in parts[1:]:
                if part not in known and not PLACEHOLDER.match(part) and f"{rel(page)}: {part}" not in ROUTE_SEGMENT_ALLOWLIST:
                    problems.append(f"{rel(page)}:{number}: `{candidate}` contains `{part}`, which no {'mapping' if known is server else 'route'} defines")
                    break
    return problems


# ---------------------------------------------------------------------------------------------
# images
# ---------------------------------------------------------------------------------------------

MARKDOWN_IMAGE = re.compile(r"!\[([^\]]*)\]\(([^)\s]+)")


IMAGE_REFERENCE = re.compile(r"[\w@./~%-]+\.(?:png|jpe?g|gif|webp|svg|ico)\b", re.I)
IMAGE_TAG = re.compile(r"<(?:img|Image)\b[^>]*>", re.I | re.S)
ALT_ATTRIBUTE = re.compile(r"""\balt\s*=\s*(?:"[^"]*[^"\s][^"]*"|'[^']*[^'\s][^']*'|\{\s*(?:"[^"]*[^"\s][^"]*"|'[^']*[^'\s][^']*'|`[^`]*[^`\s][^`]*`|[A-Za-z_][\w.]*)\s*\})""")


def referenced_images() -> set[str]:
    """The repository paths that a documentation source names, each resolved against the file that names it, so that two images with the same file name are told apart."""
    referenced: set[str] = set()
    for path in tracked_files():
        if not (path.startswith("documentation/") and Path(path).suffix in (".md", ".mdx", ".ts", ".tsx", ".js", ".css", ".json") and "node_modules" not in path):
            continue
        for target in IMAGE_REFERENCE.findall(read(path)):
            if target.startswith("@site/"):
                resolved = "documentation/" + target.removeprefix("@site/")
            elif target.startswith("/"):
                resolved = "documentation/static" + target
            else:
                resolved = (Path(path).parent / target).as_posix()
            referenced.add(posixpath.normpath(resolved))
    return referenced


def image_problems() -> list[str]:
    problems = []
    # documentation/static is served under fixed URLs that other sites may link to, so only the images of the pages are checked
    images = [p for p in tracked_files() if p.startswith("documentation/docs/") and Path(p).suffix.lower() in IMAGE_SUFFIXES]
    referenced = referenced_images()
    for image in images:
        if (REPO_ROOT / image).stat().st_size > IMAGE_BUDGET_BYTES:
            problems.append(f"{image}: {(REPO_ROOT / image).stat().st_size // 1000} kB is above the budget of {IMAGE_BUDGET_BYTES // 1000} kB, crop or reduce it")
        if image not in referenced:
            problems.append(f"{image}: no page references this image, delete it")
    for page in prose_files():
        problems.extend(alt_text_problems(page, page.read_text(encoding="utf-8")))
    return problems


def alt_text_problems(page: Path, text: str) -> list[str]:
    """Markdown images, raw <img> tags and <Image> components without a text alternative."""
    problems = []
    for number, line in prose_lines(text):
        for alt, target in MARKDOWN_IMAGE.findall(line):
            if not alt.strip() and Path(target).suffix.lower() in IMAGE_SUFFIXES:
                problems.append(f"{rel(page)}:{number}: image `{target}` has no alt text")
    # a tag may span several lines, so it is matched on the page without its fenced blocks and code spans, which keeps the line numbers
    prose = "\n".join(line if is_prose else "" for is_prose, line in _prose_marks(text))
    for m in IMAGE_TAG.finditer(prose):
        if not ALT_ATTRIBUTE.search(m.group(0)):
            problems.append(f"{rel(page)}:{prose.count(chr(10), 0, m.start()) + 1}: `{m.group(0).split()[0].rstrip('>')}` has no alt text")
    return problems


def _prose_marks(text: str):
    """(is prose, line) for every line of the page."""
    fenced = False
    for line in text.splitlines():
        if FENCE.match(line):
            fenced = not fenced
            yield False, line
        else:
            yield not fenced, INLINE_CODE.sub(" ", line)


# ---------------------------------------------------------------------------------------------
# examples
# ---------------------------------------------------------------------------------------------

PLACEHOLDER_IN_BLOCK = re.compile(r"<[A-Za-z][^>\n]*>|\.\.\.|…|\bTODO\b|\{\{")


def fenced_blocks(text: str):
    """(first line number, language, lines) for every fenced block."""
    inside, language, start, block = False, "", 0, []
    for number, line in enumerate(text.splitlines(), 1):
        m = FENCE.match(line)
        if m and not inside:
            inside, language, start, block = True, m.group(2).lower(), number + 1, []
        elif m:
            yield start, language, block
            inside = False
        elif inside:
            block.append(line)


def example_problems(page: Path, text: str) -> list[str]:
    problems = []
    if rel(page) in EXAMPLE_ALLOWLIST:
        return problems
    for start, language, lines in fenced_blocks(text):
        body = "\n".join(lines)
        if PLACEHOLDER_IN_BLOCK.search(body) or not body.strip():
            continue
        if language == "json":
            if re.search(r"^\s*//|/\*", body):
                continue
            try:
                json.loads(body)
            except ValueError as error:
                problems.append(f"{rel(page)}:{start}: the JSON example does not parse ({error})")
        elif language in ("sh", "bash", "shell", "zsh"):
            script = "\n".join(re.sub(r"^\s*\$ ", "", line) for line in lines)
            result = subprocess.run(["bash", "-n"], input=script, capture_output=True, text=True, check=False)
            if result.returncode != 0:
                problems.append(f"{rel(page)}:{start}: the shell example has a syntax error ({result.stderr.strip().splitlines()[-1] if result.stderr.strip() else 'bash -n failed'})")
        elif language in ("yaml", "yml"):
            for offset, line in enumerate(lines):
                if "\t" in line[: len(line) - len(line.lstrip())]:
                    problems.append(f"{rel(page)}:{start + offset}: the YAML example indents with a tab")
                    break
    return problems


# ---------------------------------------------------------------------------------------------
# removal markers
# ---------------------------------------------------------------------------------------------

MARKER = re.compile(r"REMOVE_IN_ARTEMIS_(\d+)")
REMOVAL_PROSE = re.compile(r"\b(?:remov(?:ed|al|e)|deleted|dropped|end of support)\b[^.\n]{0,50}\bArtemis (\d+)\b|\bArtemis (\d+)\b[^.\n]{0,30}\b(?:removes?|will remove|drops?)\b", re.I)


def marker_problems() -> list[str]:
    problems = []
    major = artemis_major()
    for path in tracked_files():
        if path.endswith((".png", ".jpg", ".gif", ".jar", ".ico", ".woff", ".woff2", ".pdf", ".zip", ".svg")) or "node_modules" in path or path.startswith("documentation/docs/"):
            continue
        if path == "supporting_scripts/check_docs_consistency.py":
            continue
        try:
            text = read(path)
        except UnicodeDecodeError:
            continue
        for number, line in enumerate(text.splitlines(), 1):
            for m in MARKER.finditer(line):
                if int(m.group(1)) <= major:
                    problems.append(f"{path}:{number}: `{m.group(0)}` is due, the version is {major}: remove the code and the marker")
    for page in prose_files():
        for number, line in prose_lines(page.read_text(encoding="utf-8")):
            for m in REMOVAL_PROSE.finditer(line):
                due = int(m.group(1) or m.group(2))
                if due <= major:
                    problems.append(f"{rel(page)}:{number}: announces a removal in Artemis {due}, the version is {major}: update the sentence")
    return problems


# ---------------------------------------------------------------------------------------------
# glossary
# ---------------------------------------------------------------------------------------------


def glossary_rows(text: str) -> list[tuple[str, str]]:
    """(term, the raw Avoid cell) for every row of the glossary tables."""
    rows = []
    columns: list[str] = []
    for line in text.splitlines():
        if not line.startswith("|"):
            columns = []
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if cells and cells[0].lower() == "term":
            columns = [c.lower() for c in cells]
            continue
        if columns and "avoid" in columns and not set(cells[0]) <= set("-: "):
            rows.append((cells[0].strip("*` "), cells[columns.index("avoid")] if columns.index("avoid") < len(cells) else ""))
    return rows


def glossary_terms() -> list[tuple[str, list[str]]]:
    """(term, words to avoid) for every row of the glossary tables. The Avoid column holds backticked words."""
    if not GLOSSARY.exists():
        return []
    return [(term, re.findall(r"`([^`]+)`", cell)) for term, cell in glossary_rows(GLOSSARY.read_text(encoding="utf-8"))]


def avoid_cell_problems(rows: list[tuple[str, str]]) -> list[str]:
    """A word in the Avoid column that is not in backticks would silently ban nothing."""
    return [f"{rel(GLOSSARY)}: the Avoid cell of `{term}` names no word in backticks, so the check cannot use it: {cell.strip()}"
            for term, cell in rows if cell.strip() not in ("", "-", "\u2013", "\u2014", "n/a") and "`" not in cell]


def glossary_problems() -> list[str]:
    if not GLOSSARY.exists():
        return [f"{rel(GLOSSARY)} is missing"]
    problems = []
    terms = glossary_terms()
    if not terms:
        return [f"{rel(GLOSSARY)} defines no terms"]
    problems.extend(avoid_cell_problems(glossary_rows(GLOSSARY.read_text(encoding="utf-8"))))
    names = [t.lower() for t, _ in terms]
    for dup in {n for n in names if names.count(n) > 1}:
        problems.append(f"{rel(GLOSSARY)}: the term `{dup}` is defined twice")
    banned = [(re.compile(r"\b%s\b" % re.escape(word), re.I), word, term) for term, avoid in terms for word in avoid]
    for page in prose_files():
        # The pages that explain which words to avoid have to name them
        if page == GLOSSARY or page.name in ("terminology.mdx", "language.mdx"):
            continue
        for number, line in prose_lines(page.read_text(encoding="utf-8")):
            # a file name or an identifier in a code span is not prose
            line = INLINE_CODE.sub(" ", line)
            for pattern, word, term in banned:
                if pattern.search(line):
                    problems.append(f"{rel(page)}:{number}: says `{word}`, the glossary calls it `{term}`")
    return problems


# ---------------------------------------------------------------------------------------------
# driver
# ---------------------------------------------------------------------------------------------

PAGE_CHECKS = {
    "versions": version_claims,
    "defaults": default_claims,
    "commands": command_problems,
    "routes": route_problems,
    "examples": example_problems,
}
GLOBAL_CHECKS = {"images": image_problems, "markers": marker_problems, "glossary": glossary_problems}


def check(only: set[str] | None = None) -> list[str]:
    problems: list[str] = []
    for name, function in PAGE_CHECKS.items():
        if only and name not in only:
            continue
        for page in prose_files():
            problems.extend(function(page, page.read_text(encoding="utf-8")))
    for name, function in GLOBAL_CHECKS.items():
        if not only or name in only:
            problems.extend(function())
    return problems


def self_test() -> None:
    """Proves that the matchers still match, because a scan that stopped matching reports a clean tree."""
    page = REPO_ROOT / "documentation/docs/admin/x.mdx"
    pins = pinned_versions()
    assert {"Java", "Node", "Angular", "Spring Boot", "PostgreSQL", "Gradle"} <= set(pins), pins
    assert version_matches("24", "24.21.0") and version_matches("4.1", "4.1.2") and not version_matches("4.2", "4.1.2") and not version_matches("2", "24.1")
    wrong = f"Artemis runs on Java {int(pins['Java']) + 1}.\n"
    assert len(version_claims(page, wrong)) == 1, version_claims(page, wrong)
    assert not version_claims(page, f"Artemis runs on Java {pins['Java']} and Node {pins['Node'].split('.')[0]}.\n")
    assert not version_claims(page, "Since Java 11 this was different.\n"), "history is not a claim"
    mixed = f"Upgraded from Java 21; now requires Java {int(pins['Java']) - 1}.\n"
    assert len(version_claims(page, mixed)) == 1, "the current claim next to a historical one is still checked"
    assert not version_claims(page, f"Upgraded from Java 21; now requires Java {pins['Java']}.\n")

    values = {"artemis.x.timeout": "90"}
    found: list[str] = []
    check_default(page, 1, "artemis.x.timeout", "`91`", values, found)
    check_default(page, 2, "artemis.x.timeout", "`90`", values, found)
    check_default(page, 3, "artemis.x.unknown", "`1`", values, found)
    assert len(found) == 1 and "91" in found[0], found
    assert yaml_scalar("${ARTEMIS_X:42} # comment") == "42" and yaml_scalar("${ARTEMIS_X}") is None and yaml_scalar("'a b'") == "a b"

    bad = "Run `pnpm run no-such-script-xyz` and `./gradlew noSuchTaskXyz` and `--spring.profiles.active=dev,nosuchprofile`.\n```bash\n./gradlew bootRun -x test\npnpm run build\ndocker compose -f src/no/such/compose.yml up\n```\n"
    found = command_problems(page, bad)
    assert len(found) == 4, found
    assert not command_problems(page, "`pnpm install` `./gradlew build` `--spring.profiles.active=dev,localvc,localci`\n")
    assert len(command_problems(page, "`pnpm --dir documentation run no-such-script-xyz`\n")) == 1, "options may precede the command"
    assert not command_problems(page, "`pnpm --dir documentation run build` `pnpm -r run build` `pnpm --filter foo install`\n")
    assert not command_problems(page, "```bash\npnpm --version   # should print the pinned version\n```\n"), "a comment after an option is not a command"
    assert not command_problems(page, "```bash\n# Requires pnpm and Node 24, use pnpm to install\npnpm install  # pnpm should work\n```\n"), "a shell comment is not a command"
    assert len(command_problems(page, "```bash\n# pnpm is needed\npnpm run no-such-script-xyz  # still reported\n```\n")) == 1
    assert len(command_problems(page, "`./gradlew clean noSuchTaskXyz`\n")) == 1, "every task is checked, not only the first"
    assert not command_problems(page, "`./gradlew build --console plain -x test`\n"), "an option value is not a task"

    instructor_page = REPO_ROOT / "documentation/docs/instructor/x.mdx"
    found = route_problems(instructor_page, "`/nosuchsection/x/y` and `/api/nosuchmodule/foo` and `/course-management/<id>/exercises`\n")
    assert len(found) == 2 and "nosuchsection" in found[0] and "nosuchmodule" in found[1], found
    assert len(route_problems(page, "`/nosuchsection/x/y` and `/api/nosuchmodule/foo`\n")) == 1, "only the REST path is checked outside the client pages"
    assert ROUTE_SPAN.match("/course-management/123/exercises") and not ROUTE_SPAN.match("/single")

    text = '```json\n{"a": 1,}\n```\n```json\n{"a": 1}\n```\n```bash\nif true; then\n```\n```bash\necho ok\n```\n```yaml\na:\n\tb: 1\n```\n```json\n{"a": <x>}\n```\n'
    found = example_problems(page, text)
    assert len(found) == 3, found

    text = "Jenkins is removed in Artemis 3. Removal in Artemis 99 is fine.\n"
    major = artemis_major()
    assert major >= 3 and REMOVAL_PROSE.search(text) and int(REMOVAL_PROSE.search(text).group(1)) == 3 and MARKER.search("// REMOVE_IN_ARTEMIS_11")

    banned = glossary_terms()
    assert not GLOSSARY.exists() or banned, "the glossary has terms"
    rows = glossary_rows("| Term | Meaning | Avoid |\n| --- | --- | --- |\n| Exercise | x | coding exercise |\n| Result | y | `outcome` |\n| Run | z | - |\n")
    assert [t for t, _ in rows] == ["Exercise", "Result", "Run"], rows
    assert len(avoid_cell_problems(rows)) == 1 and "Exercise" in avoid_cell_problems(rows)[0], avoid_cell_problems(rows)

    tags = alt_text_problems(page, '<Image src={a} />\n<Image src={a} alt="" />\n<Image\n  src={a}\n  alt="A chart"\n/>\n<img src="a.png">\n<Image src={a} alt={"x"} />\n`<img src="b.png">`\n')
    assert len(tags) == 3 and ":1:" in tags[0] and ":2:" in tags[1] and ":7:" in tags[2], tags
    assert posixpath.normpath((Path("documentation/docs/a") / "../b/x.png").as_posix()) == "documentation/docs/b/x.png"
    assert "documentation/docs/student/assets/faq/student-view.png" in referenced_images() or not tracked_files(), "the images are resolved to repository paths"
    print("Self-test passed.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--only", help="comma separated names of the checks to run: " + ", ".join([*PAGE_CHECKS, *GLOBAL_CHECKS]))
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return 0
    only = set(args.only.split(",")) if args.only else None
    unknown = (only or set()) - {*PAGE_CHECKS, *GLOBAL_CHECKS}
    if unknown:
        parser.error(f"unknown check(s): {', '.join(sorted(unknown))}. The checks are: {', '.join([*PAGE_CHECKS, *GLOBAL_CHECKS])}")
    problems = check(only)
    for problem in problems:
        print(problem)
    if problems:
        print(f"\n{len(problems)} documentation statement(s) contradict the repository.")
        return 1
    print("Documentation consistency check passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
