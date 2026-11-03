#!/usr/bin/env python3
"""Fail when the documentation points at something that no longer exists.

Documentation drifts when the code moves and nobody notices. Five kinds of drift are cheap to detect
without false alarms, so this check detects exactly those:

  * Sidebar registration. Every page below documentation/docs/<area>/ has to appear in the sidebar of
    its area (documentation/sidebar-<area>.ts). Docusaurus renders an unregistered page, but nobody
    can navigate to it, so it silently stops being maintained.
  * Repository paths. An inline code span in the prose that names a repository path, for example
    `src/main/resources/config/application-localci.yml`, has to exist. A moved or deleted file is
    the commonest way a setup guide goes stale. Paths that the version control ignores (build
    output, local configuration) and the few entries of PATH_ALLOWLIST are exempt.
  * Class names. An inline code span that names a class by its role, for example
    `BuildScriptProviderService` or `PerNodeCacheEvictionService.evictEverywhere()`, has to occur in
    the source, and a named method has to occur in the file of its class. A renamed or deleted class
    leaves its old name behind in the prose.
  * Configuration. A property such as `artemis.continuous-integration.build-timeout-seconds.max` or an
    environment variable such as `ARTEMIS_CONTINUOUSINTEGRATION_DOCKERCONNECTIONURI` in the admin and
    developer pages has to be known: it occurs in a YAML or properties file, in a string literal of
    the code, or it is bound by a @ConfigurationProperties class. The same holds for the keys under
    `artemis:` in a fenced YAML example.
  * Interface labels. A bold label after a verb such as "Click", for example `Click **Save**`, in the
    instructor and student pages has to be a text of the English translation files. A path such as
    `**Course Management → Exercises**` is checked segment by segment.

The prose of README.md, AGENTS.md, skills/ and .github/ is checked for paths, class names and
configuration as well, because it describes the same code.

Not checked on purpose: code blocks other than YAML configuration (they hold examples, not
references), paths with placeholders or wildcards (`<module>`, `${name}`, `*`), and anything that does not
start with a top-level directory of the repository. A check that reports false alarms gets disabled,
so it only reports what it can prove. Every exception has an entry with a reason below.

Usage:
    python3 supporting_scripts/check_docs_references.py
    python3 supporting_scripts/check_docs_references.py --self-test
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from functools import cache
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DOCS_ROOT = REPO_ROOT / "documentation" / "docs"
AREAS = {"about": "sidebar-about.ts", "admin": "sidebar-admin.ts", "developer": "sidebar-developer.ts", "instructor": "sidebar-instructors.ts", "student": "sidebar-students.ts"}

# Areas that describe the server, so that their configuration references can be checked against it.
CONFIG_AREAS = ("admin", "developer")
# Areas that describe the user interface of Artemis.
UI_AREAS = ("instructor", "student")
# Prose outside documentation/docs that describes the same code.
EXTRA_PROSE = ("README.md", "AGENTS.md", "CLAUDE.md", ".github/CONTRIBUTING.md", ".github/PULL_REQUEST_TEMPLATE.md", ".github/workflows/README.md")
EXTRA_PROSE_DIRS = ("skills",)

# Top-level directories whose paths a page may reference.
REPO_DIRS = ("src", "documentation", "deployment", "supporting_scripts", ".github", "gradle", "skills", "packages", "config")
# Directories whose files are searched for class names, property names and method names.
SOURCE_DIRS = ("src", "deployment", "supporting_scripts", ".github", "packages", "gradle", "skills")
SOURCE_SUFFIXES = {".java", ".ts", ".yml", ".yaml", ".properties", ".gradle", ".xml", ".json", ".html", ".py", ".sh", ".kts", ".toml", ".env"}
SKIP_DIRS = {"node_modules", "build", ".git", "openapi", "i18n", "docs"}

INLINE_CODE = re.compile(r"`([^`\n]+)`")
PATH = re.compile(r"^(?:%s)/[A-Za-z0-9_./-]+$" % "|".join(re.escape(d) for d in REPO_DIRS))
FENCE = re.compile(r"^\s*(```|~~~)\s*([A-Za-z]*)")

ROLES = (
    "Service|Resource|Repository|Api|Provider|Configuration|Config|Controller|Component|Parser|Policy|Topic|Queue|DTO|Factory|Handler|Listener|Strategy|Filter|Interceptor|"
    "Manager|Registry|Properties|Template|Job|Task|Scheduler|Resolver|Validator|Converter|Mapper|Adapter|Client"
)
CLASS_REFERENCE = re.compile(r"^([A-Z][A-Za-z0-9]*(?:%s))(?:[#.]([a-z][A-Za-z0-9]*)\(\.{0,3}\))?$" % ROLES)
PROPERTY = re.compile(r"^artemis(?:\.[a-z0-9]+(?:-[a-z0-9]+)*)+$")
ENVIRONMENT_VARIABLE = re.compile(r"^ARTEMIS_[A-Z0-9_]+$")
# A host name is not a property, although it starts with the same word.
HOST_SUFFIXES = (".com", ".de", ".org", ".net", ".io", ".yml", ".yaml", ".md", ".json", ".xml")
BOLD = re.compile(r"\*\*([^*\n]{2,60})\*\*")
LABEL_CONTEXT = re.compile(
    r"(?:click(?:ing)?(?: on)?|select(?:ing)?|choose|press|tap|open|the button|button|tab|menu|toggle|enable|disable|activate|check|uncheck|navigate to|go to|switch to|"
    r"under|in the|field)\W*$"
)

# References that are intentionally not present in the repository, for example files that an
# administrator creates. Each entry needs a reason.
PATH_ALLOWLIST: dict[str, str] = {
    "src/main/resources/config/application-local.yml": "created by the developer, see the setup guide",
    "src/main/resources/config/application-artemis.local.yml": "created by the developer, see the setup guide",
    "deployment/docker/artemis/config/local-secrets.env": "an example name for a file that the reader creates and keeps untracked",
    "src/public-api.ts": "relative to packages/tum-aet-ui, which the page is about",
    "src/client.js": "a file in the exercise repository that the page describes, not in this repository",
    "config/default.exp": "a file in the exercise repository that the page describes, not in this repository",
}
ALLOWLIST = PATH_ALLOWLIST

CLASS_ALLOWLIST: dict[str, str] = {
    "PostUpdateEventListener": "a Hibernate type that the page names",
    "PostDeleteEventListener": "a Hibernate type that the page names",
    "ExampleParticipationService": "an illustrative name in a guideline, not a real class",
    "EntryListener": "a Hazelcast type that the page names to explain what the distributed data provider replaces",
}

# Names of settings that are not Artemis server properties although they start with the same word.
# An entry that ends with a dot also covers the keys below it, because the keys below a map are chosen by the administrator.
PROPERTY_ALLOWLIST: dict[str, str] = {
    "artemis.llm.model-costs.": "the keys below model-costs are model names that the administrator chooses",
}

# Pages about another product than the Artemis web client. Their labels are texts of that product.
LABEL_EXEMPT_PREFIXES = (
    "documentation/docs/student/tools-reference/vscode-extension/",
    "documentation/docs/student/getting-started/mobile-applications.mdx",
)
LABEL_ALLOWLIST: dict[str, str] = {
    "install": "a button of the VS Code marketplace, not of Artemis",
    "add feedback": "TODO: the label of the gutter widget of the code editor is not in the translation files, an owner of the instructor pages has to check it in the running client",
    "add general feedback": "TODO: see add feedback",
}


# ---------------------------------------------------------------------------------------------
# prose
# ---------------------------------------------------------------------------------------------


def doc_pages(area: str) -> list[Path]:
    return sorted(p for p in (DOCS_ROOT / area).rglob("*") if p.suffix in (".md", ".mdx"))


def extra_prose() -> list[Path]:
    files = [REPO_ROOT / name for name in EXTRA_PROSE if (REPO_ROOT / name).exists()]
    for directory in EXTRA_PROSE_DIRS:
        files.extend(sorted((REPO_ROOT / directory).rglob("*.md")))
    return files


def rel(path: Path) -> str:
    return path.relative_to(REPO_ROOT).as_posix()


def prose_lines(text: str):
    """Yields (line number, line) for every line outside fenced code blocks."""
    fenced = False
    for number, line in enumerate(text.splitlines(), 1):
        if FENCE.match(line):
            fenced = not fenced
            continue
        if not fenced:
            yield number, line


def yaml_blocks(text: str):
    """Yields (first line number, lines) for every fenced YAML block."""
    inside = False
    is_yaml = False
    start = 0
    block: list[str] = []
    for number, line in enumerate(text.splitlines(), 1):
        m = FENCE.match(line)
        if m and not inside:
            inside, is_yaml, start, block = True, m.group(2).lower() in ("yaml", "yml"), number + 1, []
        elif m:
            if is_yaml:
                yield start, block
            inside = False
        elif inside and is_yaml:
            block.append(line)


# ---------------------------------------------------------------------------------------------
# sidebar registration
# ---------------------------------------------------------------------------------------------


def doc_ids(area: str, path: Path) -> set[str]:
    """The ids under which a page can appear in a sidebar: its path without extension, and its front matter id."""
    relative = path.relative_to(DOCS_ROOT / area).with_suffix("")
    ids = {relative.as_posix()}
    # a folder's index page is addressed by the folder name as well
    if relative.name in ("index", relative.parent.name):
        ids.add(relative.parent.as_posix())
    front = re.search(r"^---\n(.*?)\n---", path.read_text(encoding="utf-8"), re.S)
    if front:
        m = re.search(r"^id:\s*['\"]?([^'\"\n]+)['\"]?\s*$", front.group(1), re.M)
        if m:
            ids.add((relative.parent / m.group(1).strip()).as_posix().lstrip("./"))
    return ids


def unregistered_pages() -> list[str]:
    problems = []
    for area, sidebar_name in AREAS.items():
        sidebar = (REPO_ROOT / "documentation" / sidebar_name).read_text(encoding="utf-8")
        referenced = set(re.findall(r"""['"]([A-Za-z0-9_./-]+)['"]""", sidebar))
        for page in doc_pages(area):
            if not (doc_ids(area, page) & referenced):
                problems.append(f"{rel(page)}: not registered in documentation/{sidebar_name}")
    return problems


# ---------------------------------------------------------------------------------------------
# repository paths
# ---------------------------------------------------------------------------------------------


def is_ignored(candidate: str) -> bool:
    """Build output and local configuration are ignored by the version control and are expected to be missing from a checkout."""
    try:
        return subprocess.run(["git", "check-ignore", "-q", candidate], cwd=REPO_ROOT, check=False).returncode == 0
    except OSError:
        return False


def missing_paths(page: Path, text: str) -> list[str]:
    problems = []
    for number, line in prose_lines(text):
        for span in INLINE_CODE.findall(line):
            candidate = span.strip().rstrip(".,:;")
            if not PATH.match(candidate) or candidate in PATH_ALLOWLIST:
                continue
            if not (REPO_ROOT / candidate).exists() and not is_ignored(candidate):
                problems.append(f"{rel(page)}:{number}: `{candidate}` does not exist")
    return problems


# ---------------------------------------------------------------------------------------------
# the index of the source
# ---------------------------------------------------------------------------------------------


def normalize(key: str) -> str:
    """Relaxed binding: `build-timeout-seconds`, `buildTimeoutSeconds` and `BUILD_TIMEOUT_SECONDS` are one key."""
    return re.sub(r"[-_]", "", key).lower()


def flatten_yaml(lines):
    """Yields the dotted key of every mapping entry of a YAML document. Enough YAML for configuration files."""
    stack: list[tuple[int, str]] = []
    for line in lines:
        if not line.strip() or line.lstrip().startswith("#") or line.strip() == "---":
            continue
        m = re.match(r"""^(\s*)([A-Za-z0-9_.\-\[\]"'/]+):(?:\s|$)""", line)
        if not m:
            continue
        indent = len(m.group(1))
        while stack and stack[-1][0] >= indent:
            stack.pop()
        stack.append((indent, m.group(2).strip("\"'")))
        yield ".".join(k for _, k in stack)


class SourceIndex:
    """What the source knows: identifiers, the file of each class, configuration keys and interface texts."""

    def __init__(self) -> None:
        self.tokens: set[str] = set()
        self.files_by_stem: dict[str, list[Path]] = {}
        self.keys: set[str] = set()  # normalized dotted configuration keys
        self.flat_keys: set[str] = set()  # the same without dots, for environment variables
        self.environment_names: set[str] = set()  # ARTEMIS_* names that occur anywhere, for example as ${ARTEMIS_X:default}
        self.bound_prefixes: dict[str, set[str]] = {}  # @ConfigurationProperties prefix -> identifiers of its classes
        self.labels: set[str] = set()
        self._scan_source()
        self._scan_labels()

    def _add_key(self, key: str) -> None:
        normalized = normalize(key)
        self.keys.add(normalized)
        self.flat_keys.add(normalized.replace(".", ""))

    def _scan_source(self) -> None:
        for base in SOURCE_DIRS:
            for directory, subdirectories, files in os.walk(REPO_ROOT / base):
                subdirectories[:] = [d for d in subdirectories if d not in SKIP_DIRS]
                for name in files:
                    path = Path(directory) / name
                    if path.suffix not in SOURCE_SUFFIXES and not name.endswith(".env"):
                        continue
                    if name.startswith("check_docs_"):
                        continue  # the checks name things that must not exist, which would make them known
                    try:
                        text = path.read_text(encoding="utf-8", errors="ignore")
                    except OSError:
                        continue
                    self.files_by_stem.setdefault(path.stem, []).append(path)
                    self.tokens.update(re.findall(r"[A-Za-z_][A-Za-z0-9_]*", text))
                    self.environment_names.update(re.findall(r"ARTEMIS_[A-Z0-9_]+", text))
                    self._scan_configuration(path, text)

    def _scan_configuration(self, path: Path, text: str) -> None:
        if path.suffix in (".yml", ".yaml"):
            for key in flatten_yaml(text.splitlines()):
                self._add_key(key)
        elif path.suffix == ".properties":
            for line in text.splitlines():
                if "=" in line and not line.lstrip().startswith("#"):
                    self._add_key(line.split("=")[0].strip())
        if path.suffix in (".java", ".kts", ".gradle", ".py", ".sh"):
            # string literals such as "${artemis.deimos.llm.temperature:0}" or Integer.getInteger("artemis.test.wave-size")
            for m in re.finditer(r"artemis(?:\.[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*)+", text):
                self._add_key(m.group(0))
        if path.suffix == ".java":
            m = re.search(r"""@ConfigurationProperties\(\s*(?:prefix\s*=\s*)?"([^"]+)\"""", text)
            if m:
                self.bound_prefixes.setdefault(normalize(m.group(1)), set()).update(normalize(t) for t in re.findall(r"[A-Za-z_][A-Za-z0-9_]*", text))

    def _scan_labels(self) -> None:
        def walk(value) -> None:
            if isinstance(value, dict):
                for item in value.values():
                    walk(item)
            elif isinstance(value, str):
                plain = re.sub(r"<[^>]+>", "", value)
                for text in (re.sub(r"\{\{.*?\}\}", "", plain), re.sub(r"\{\{.*?\}\}", "N", plain)):
                    text = re.sub(r"\s+", " ", text).strip().lower().rstrip(":")
                    self.labels.add(text)
                    self.labels.add(re.sub(r"\s*\(\s*N?\s*\)$", "", text))  # "Import selected (3)" is named "Import selected"

        for translation in sorted((REPO_ROOT / "src/main/webapp/i18n/en").glob("*.json")):
            walk(json.loads(translation.read_text(encoding="utf-8")))

    # -- queries
    def knows_class(self, name: str) -> bool:
        return name in self.tokens

    def class_has_method(self, name: str, method: str) -> bool:
        files = self.files_by_stem.get(name)
        if not files:
            return True  # a class without a file of its own, for example a nested class: only the name can be checked
        return any(re.search(r"\b%s\b" % re.escape(method), f.read_text(encoding="utf-8", errors="ignore")) for f in files)

    def knows_property(self, key: str) -> bool:
        k = normalize(key)
        if k in self.keys or any(x.startswith(k + ".") for x in self.keys):
            return True
        for prefix, tokens in self.bound_prefixes.items():
            if k == prefix or k.startswith(prefix + "."):
                rest = k[len(prefix):].strip(".")
                if all(segment in tokens for segment in rest.split(".") if segment):
                    return True
        return False

    def knows_environment_variable(self, name: str) -> bool:
        if name in self.environment_names:
            return True
        flat = normalize(name)  # relaxed binding: a property can be set as an environment variable without a mention
        return any(x.startswith(flat) for x in self.flat_keys)

    def knows_label(self, label: str) -> bool:
        return label.lower().rstrip(":") in self.labels


@cache
def source_index() -> SourceIndex:
    return SourceIndex()


# ---------------------------------------------------------------------------------------------
# class names, configuration, interface labels
# ---------------------------------------------------------------------------------------------


def unknown_classes(page: Path, text: str, index: SourceIndex) -> list[str]:
    problems = []
    for number, line in prose_lines(text):
        for span in INLINE_CODE.findall(line):
            m = CLASS_REFERENCE.match(span.strip())
            if not m or m.group(1) in CLASS_ALLOWLIST:
                continue
            name, method = m.groups()
            if not index.knows_class(name):
                problems.append(f"{rel(page)}:{number}: class `{name}` does not exist in the source")
            elif method and not index.class_has_method(name, method):
                problems.append(f"{rel(page)}:{number}: `{name}` has no method `{method}`")
    return problems


def allowed_property(key: str) -> bool:
    return any(key == entry or (entry.endswith(".") and key.startswith(entry)) for entry in PROPERTY_ALLOWLIST)


def unknown_configuration(page: Path, text: str, index: SourceIndex) -> list[str]:
    problems = []
    for number, line in prose_lines(text):
        for span in INLINE_CODE.findall(line):
            candidate = span.strip().rstrip(".,:;")
            if allowed_property(candidate) or candidate.endswith(HOST_SUFFIXES):
                continue
            if PROPERTY.match(candidate) and not index.knows_property(candidate):
                problems.append(f"{rel(page)}:{number}: property `{candidate}` is not defined in the configuration or the source")
            elif ENVIRONMENT_VARIABLE.match(candidate) and not index.knows_environment_variable(candidate):
                problems.append(f"{rel(page)}:{number}: environment variable `{candidate}` does not match any property")
    for start, block in yaml_blocks(text):
        for offset, key in _yaml_keys_with_lines(block):
            if key.startswith("artemis.") and not allowed_property(key) and not index.knows_property(key):
                problems.append(f"{rel(page)}:{start + offset}: property `{key}` in the YAML example is not defined in the configuration or the source")
    return problems


def _yaml_keys_with_lines(block: list[str]):
    keys = list(flatten_yaml(block))
    # flatten_yaml skips blank and comment lines, so pair the keys with their lines again
    index = 0
    for offset, line in enumerate(block):
        if index < len(keys) and re.match(r"""^\s*%s:(?:\s|$)""" % re.escape(keys[index].split(".")[-1]), line):
            yield offset, keys[index]
            index += 1


def unknown_labels(page: Path, text: str, index: SourceIndex) -> list[str]:
    path = rel(page)
    if path.startswith(LABEL_EXEMPT_PREFIXES):
        return []
    problems = []
    for number, line in prose_lines(text):
        for m in BOLD.finditer(line):
            if not LABEL_CONTEXT.search(line[max(0, m.start() - 40):m.start()].lower()) or not m.group(1)[0].isalnum():
                continue
            for segment in re.split(r"\s*(?:→|->|>)\s*", m.group(1)):
                label = re.sub(r"\s+", " ", segment).strip()
                if label.lower().rstrip(":") in LABEL_ALLOWLIST or index.knows_label(label):
                    continue
                problems.append(f"{path}:{number}: label **{label}** is not a text of the English translation files")
    return problems


# ---------------------------------------------------------------------------------------------
# driver
# ---------------------------------------------------------------------------------------------


def check() -> list[str]:
    problems = unregistered_pages()
    index = source_index()
    for area in AREAS:
        for page in doc_pages(area):
            text = page.read_text(encoding="utf-8")
            problems.extend(missing_paths(page, text))
            problems.extend(unknown_classes(page, text, index))
            if area in CONFIG_AREAS:
                problems.extend(unknown_configuration(page, text, index))
            if area in UI_AREAS:
                problems.extend(unknown_labels(page, text, index))
    for page in extra_prose():
        text = page.read_text(encoding="utf-8")
        problems.extend(missing_paths(page, text))
        problems.extend(unknown_classes(page, text, index))
        problems.extend(unknown_configuration(page, text, index))
    return problems


def self_test() -> None:
    """Proves that the matchers still match, because a scan that stopped matching reports a clean tree."""
    page = Path("documentation/docs/admin/x.mdx")
    text = "See `src/main/java/DoesNotExistAnywhere.java` and `build.gradle`.\n```\n`src/also/missing.txt`\n```\n`src/main/<module>/x` `src/*/y`\n"
    found = missing_paths(REPO_ROOT / page, text)
    assert len(found) == 1 and "DoesNotExistAnywhere" in found[0], found
    assert not missing_paths(REPO_ROOT / page, "`README.md` `documentation/docs/admin/telemetry.mdx`"), "existing paths must pass"
    assert PATH.match("src/main/resources/config/application.yml") and not PATH.match("src/main/${x}/y")

    index = source_index()
    found = unknown_classes(REPO_ROOT / page, "`NoSuchThingService` and `PerNodeCacheEvictionService.evictEverywhere()` and `PerNodeCacheEvictionService.noSuchMethod()`\n", index)
    assert len(found) == 2 and "NoSuchThingService" in found[0] and "noSuchMethod" in found[1], found
    assert not unknown_classes(REPO_ROOT / page, "`PerNodeCacheEvictionService` `Course` `artemis.yml`\n", index), "known names must pass"

    found = unknown_configuration(REPO_ROOT / page, "`artemis.no-such.property` `ARTEMIS_NOSUCH_PROPERTY` `artemis.deimos.llm.timeout-seconds` `artemis.example.com`\n"
                                  "```yaml\nartemis:\n  deimos:\n    llm:\n      no-such-key: 1\n      timeout-seconds: 90\n```\n", index)
    assert len(found) == 3, found
    assert any("no-such.property" in f for f in found) and any("ARTEMIS_NOSUCH" in f for f in found) and any("no-such-key" in f for f in found), found

    found = unknown_labels(REPO_ROOT / "documentation/docs/instructor/x.mdx", "Click **No Such Button** and then **Save**; the **Bold Word** is not a label. Open **Course Management → No Such Page**.\n", index)
    assert len(found) == 2 and "No Such Button" in found[0] and "No Such Page" in found[1], found
    print("Self-test passed.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return 0
    problems = check()
    for problem in problems:
        print(problem)
    if problems:
        print(f"\n{len(problems)} documentation reference(s) point at something that does not exist.")
        return 1
    print("Documentation reference check passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
