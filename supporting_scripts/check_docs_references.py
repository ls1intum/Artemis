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
    "TransactionSynchronizationManager": "a Spring type that the pages name to explain what a transaction boundary is needed for",
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


def flatten_yaml_with_lines(lines):
    """Yields (index of the line, dotted key) for every mapping entry of a YAML document. Enough YAML for configuration files.
    A dotted key such as `deimos.llm.model:` and a quoted key are one entry each, at their own line."""
    stack: list[tuple[int, str]] = []
    for number, line in enumerate(lines):
        if not line.strip() or line.lstrip().startswith("#") or line.strip() == "---":
            continue
        m = re.match(r"""^(\s*)([A-Za-z0-9_.\-\[\]"'/]+):(?:\s|$)""", line)
        if not m:
            continue
        indent = len(m.group(1))
        while stack and stack[-1][0] >= indent:
            stack.pop()
        stack.append((indent, m.group(2).strip("\"'")))
        yield number, ".".join(k for _, k in stack)


def flatten_yaml(lines):
    for _, key in flatten_yaml_with_lines(lines):
        yield key


# ---------------------------------------------------------------------------------------------
# a small reader of Java declarations: classes, their methods and the fields that Spring binds
# ---------------------------------------------------------------------------------------------

DECLARATION = re.compile(r"\b(?:class|interface|enum|record|type)\s+([A-Z][A-Za-z0-9_]*)")
JAVA_IMPORT = re.compile(r"^import\s+(?:static\s+)?[\w.]*\.([A-Z]\w*);", re.M)
TS_IMPORT = re.compile(r"^import\s+(?:type\s+)?\{([^}]*)\}\s+from", re.M)
KEYWORDS = {"if", "for", "while", "switch", "catch", "return", "new", "throw", "else", "synchronized", "assert", "try", "yield", "do", "case", "var", "function"}
MODIFIERS = r"(?:(?:public|protected|private|static|final|abstract|default|synchronized|native|transient|volatile)\s+)*"
ANNOTATIONS = r"(?:@\w+(?:\([^)]*\))?\s+)*"
JAVA_METHOD = re.compile(r"^\s*" + ANNOTATIONS + MODIFIERS + r"(?:<[^>]+>\s+)?([\w<>\[\],.? ]+?)\s+(\w+)\s*\(", re.M)
JAVA_FIELD = re.compile(r"^\s*" + ANNOTATIONS + r"((?:(?:private|protected|public|static|final|transient|volatile)\s+)*)([\w<>\[\],.? ]+?)\s+(\w+)\s*(?:=[^;]*)?;\s*$")
TS_METHOD = re.compile(r"^\s*(?:(?:public|private|protected|static|async|override|readonly|get|set)\s+)*(\w+)\s*(?:<[^>]*>)?\s*\(", re.M)
SIMPLE_TYPES = {"String", "Integer", "Long", "Boolean", "Double", "Float", "Short", "Byte", "Character", "Duration", "Period", "URI", "URL", "Path", "File", "Pattern", "Object",
                "BigDecimal", "BigInteger", "Instant", "LocalDate", "LocalDateTime", "ZonedDateTime", "DataSize", "Locale", "Charset", "UUID", "Class", "Resource", "InetAddress", "Date"}


def strip_comments(text: str) -> str:
    """Removes comments, so that a name that survives only in a comment does not count as declared."""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"(?m)(?<![:\"'\w])//.*$", "", text)


def declared_classes(text: str, suffix: str) -> set[str]:
    """The classes, interfaces, enums, records and types that a source file declares, and the classes that it imports."""
    if suffix not in (".java", ".ts", ".kts", ".py"):
        return set()
    code = strip_comments(text) if suffix != ".py" else text
    names = set(DECLARATION.findall(code))
    names.update(JAVA_IMPORT.findall(code))
    for group in TS_IMPORT.findall(code):
        names.update(part.split(" as ")[-1].strip() for part in group.split(",") if part.strip())
    return names


def matching_end(text: str, start: int, opening: str, closing: str) -> int:
    """The index after the bracket that closes the one at `start`, skipping string and character literals."""
    depth, i = 0, start
    while i < len(text):
        c = text[i]
        if text.startswith('"""', i):
            i = text.find('"""', i + 3)
            if i < 0:
                return len(text)
            i += 3
            continue
        if c in "\"'":
            j = i + 1
            while j < len(text) and text[j] != c:
                j += 2 if text[j] == "\\" else 1
            i = j + 1
            continue
        if c == opening:
            depth += 1
        elif c == closing:
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return len(text)


def split_top_level(text: str) -> list[str]:
    """Splits at the commas that are not inside <>, () or []."""
    parts, depth, current = [], 0, []
    for c in text:
        if c in "<([":
            depth += 1
        elif c in ">)]":
            depth -= 1
        if c == "," and depth == 0:
            parts.append("".join(current))
            current = []
        else:
            current.append(c)
    parts.append("".join(current))
    return [p.strip() for p in parts if p.strip()]


def class_declaration(text: str, name: str):
    """(record components, body) of the class, interface, enum or record `name` in the text, or None. Comments are removed."""
    code = strip_comments(text)
    m = re.search(r"\b(class|interface|enum|record)\s+%s\b" % re.escape(name), code)
    if not m:
        return None
    i = m.end()
    components = ""
    if m.group(1) == "record":
        generics = re.match(r"\s*<[^>]*>", code[i:])
        i += generics.end() if generics else 0
        open_at = code.find("(", i)
        end = matching_end(code, open_at, "(", ")")
        components = code[open_at + 1:end - 1]
        i = end
    brace = code.find("{", i)
    if brace < 0:
        return components, ""
    end = matching_end(code, brace, "{", "}")
    return components, code[brace + 1:end - 1]


def own_lines(body: str):
    """Yields the lines of a class body that belong to the class itself: not the lines of a nested class, of a method or of an initializer."""
    depth = 0
    for line in body.splitlines():
        if depth == 0:
            yield line
        code = re.sub(r"\"(?:\\.|[^\"\\])*\"|'(?:\\.|[^'\\])'", "", line)
        depth += code.count("{") - code.count("}")


def declared_methods(declaration) -> set[str]:
    """The methods that a class declares itself, and the accessors of the components of a record. Calls and the methods of a nested class do not count."""
    components, body = declaration
    names = {c.split()[-1] for c in split_top_level(components)}
    for line in own_lines(body):
        m = JAVA_METHOD.match(line)
        if m and (m.group(1).split() or [""])[0] not in KEYWORDS:
            names.add(m.group(2))
    return names


def declared_members(declaration) -> list[tuple[str, str]]:
    """(name, type) of the components of a record and of the instance fields of a class, which Spring can bind."""
    components, body = declaration
    members = []
    for component in split_top_level(components):
        component = re.sub(r"@\w+(?:\([^)]*\))?\s*", "", component).strip()
        if " " in component:
            kind, _, name = component.rpartition(" ")
            members.append((name, kind.strip()))
    for line in own_lines(body):
        m = JAVA_FIELD.match(line)
        if m and "static" not in m.group(1).split() and (m.group(2).split() or [""])[0] not in KEYWORDS:
            members.append((m.group(3), m.group(2).strip()))
    return members


def bound_type(type_name: str):
    """What a field type means for binding: ('map', None), or ('type', the class that holds the values)."""
    t = re.sub(r"\s+", "", type_name)
    if t.startswith(("Map<", "Properties")):
        return "map", None
    m = re.match(r"(?:Optional|List|Set|Collection|ArrayList|Iterable)<(.+)>$", t)
    if m:
        return bound_type(m.group(1))
    t = t.rstrip("[]")
    return "type", t.split(".")[-1].split("<")[0]


# ---------------------------------------------------------------------------------------------
# the index of the source
# ---------------------------------------------------------------------------------------------


class SourceIndex:
    """What the source knows: declared classes, their methods, configuration keys and interface texts."""

    def __init__(self, scan: bool = True) -> None:
        self.classes: set[str] = set()  # declared or imported, never named in a comment only
        self.files_by_stem: dict[str, list[Path]] = {}
        self.keys: set[str] = set()  # normalized dotted configuration keys, including the paths that Spring binds
        self.flat_keys: set[str] = set()  # the same without dots, for environment variables
        self.open_prefixes: set[str] = set()  # below these, the administrator chooses the keys: a map
        self.environment_names: set[str] = set()  # ARTEMIS_* names that occur anywhere, for example as ${ARTEMIS_X:default}
        self.labels: set[str] = set()
        self._bound: list[tuple[str, str, int]] = []  # (text of a source file, prefix, end of the annotation prefix) of @ConfigurationProperties
        if scan:
            self._scan_source()
            self._scan_binding()
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
                    self.classes.update(declared_classes(text, path.suffix))
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
        if path.suffix == ".java" and "@ConfigurationProperties" in text:
            for m in re.finditer(r"""@ConfigurationProperties\(\s*(?:(?:prefix|value)\s*=\s*)?"([^"]+)\"""", text):
                self._bound.append((text, m.group(1), m.end()))

    def _scan_binding(self) -> None:
        for text, prefix, after in self._bound:
            self.add_binding(text, prefix, after)

    def add_binding(self, text: str, prefix: str, after: int = 0) -> None:
        """Adds the complete paths that Spring binds below a prefix: the fields of the annotated class and, recursively, of the
        classes of its fields. A class that cannot be read leaves its prefix open instead of rejecting valid keys."""
        m = re.match(r"[^)]*\)(?:\s*@\w+(?:\([^)]*\))?)*\s*(?:(?:public|final|abstract)\s+)*(?:class|record)\s+(\w+)", text[after:], re.S)
        if not m:
            self.open_prefixes.add(normalize(prefix))  # an annotated factory method, for example
            return
        self._add_members(prefix, m.group(1), text, set())

    def _declaration(self, name: str, here: str):
        declaration = class_declaration(here, name)
        if declaration:
            return declaration, here
        for path in self.files_by_stem.get(name, []):
            if path.suffix == ".java":
                other = path.read_text(encoding="utf-8", errors="ignore")
                declaration = class_declaration(other, name)
                if declaration:
                    return declaration, other
        return None, here

    def _add_members(self, path: str, class_name: str, here: str, seen: set[str]) -> None:
        declaration, text = self._declaration(class_name, here)
        members = declared_members(declaration) if declaration else []
        if not members:
            self.open_prefixes.add(normalize(path))
            return
        for name, type_name in members:
            member = f"{path}.{name}"
            self._add_key(member)
            kind, target = bound_type(type_name)
            if kind == "map":
                self.open_prefixes.add(normalize(member))
            elif target not in SIMPLE_TYPES and target not in seen and target[:1].isupper() and self._declaration(target, text)[0]:
                self._add_members(member, target, text, seen | {class_name})

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
        return name in self.classes

    def class_has_method(self, name: str, method: str) -> bool:
        files = [f for f in self.files_by_stem.get(name, []) if f.suffix in (".java", ".ts")]
        if not files:
            return True  # a class without a file of its own, for example a nested class: only the name can be checked
        for f in files:
            text = f.read_text(encoding="utf-8", errors="ignore")
            if f.suffix == ".ts":
                if any(m.group(1) == method for m in TS_METHOD.finditer(strip_comments(text))):
                    return True
                continue
            declaration = class_declaration(text, name)
            if declaration is None or method in declared_methods(declaration):
                return True
        return False

    def knows_property(self, key: str) -> bool:
        k = normalize(key)
        if k in self.keys or any(x.startswith(k + ".") for x in self.keys):
            return True
        return any(k == o or k.startswith(o + ".") for o in self.open_prefixes)

    def knows_environment_variable(self, name: str) -> bool:
        if name in self.environment_names:
            return True
        flat = normalize(name)  # relaxed binding: a property can be set as an environment variable without a mention
        return flat in self.flat_keys or any(flat.startswith(o.replace(".", "")) for o in self.open_prefixes)

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
        for offset, key in flatten_yaml_with_lines(block):
            if key.startswith("artemis.") and not allowed_property(key) and not index.knows_property(key):
                problems.append(f"{rel(page)}:{start + offset}: property `{key}` in the YAML example is not defined in the configuration or the source")
    return problems


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
    found = unknown_classes(REPO_ROOT / page, "`NoSuchThingService` and `PerNodeCacheEvictionService.evictEverywhere()` and `PerNodeCacheEvictionService.noSuchMethod()` and `PerNodeCacheEvictionService.getCache()`\n", index)
    # getCache is only called by the service, on the cache manager, so it is not a method of the service
    assert len(found) == 3 and "NoSuchThingService" in found[0] and "noSuchMethod" in found[1] and "getCache" in found[2], found
    assert not unknown_classes(REPO_ROOT / page, "`PerNodeCacheEvictionService` `Course` `artemis.yml`\n", index), "known names must pass"

    found = unknown_configuration(REPO_ROOT / page, "`artemis.no-such.property` `ARTEMIS_NOSUCH_PROPERTY` `artemis.deimos.llm.timeout-seconds` `artemis.example.com`\n"
                                  "```yaml\nartemis:\n  deimos:\n    llm:\n      no-such-key: 1\n      timeout-seconds: 90\n```\n", index)
    assert len(found) == 3, found
    assert any("no-such.property" in f for f in found) and any("ARTEMIS_NOSUCH" in f for f in found) and any("no-such-key" in f for f in found), found

    found = unknown_labels(REPO_ROOT / "documentation/docs/instructor/x.mdx", "Click **No Such Button** and then **Save**; the **Bold Word** is not a label. Open **Course Management → No Such Page**.\n", index)
    assert len(found) == 2 and "No Such Button" in found[0] and "No Such Page" in found[1], found
    # a name that survives only in a comment is not a class, and a name that is only called is not a method
    assert declared_classes("// the removed OldThingService\n/* OldThingService */\nclass Real {}\nimport a.b.Imported;\n", ".java") == {"Real", "Imported"}
    java = "class S {\n  private final CacheManager cacheManager;\n  public void evictEverywhere(String a) {\n    var c = cacheManager.getCache(a);\n  }\n}\n"
    methods = declared_methods(class_declaration(java, "S"))
    assert "evictEverywhere" in methods and "getCache" not in methods, methods
    assert "name" in declared_methods(class_declaration("record R(String name, int size) {}", "R"))
    nested = "class ArtemisProperties {\n  private final Mail mail;\n  public Mail getMail() { return mail; }\n  public static class Mail {\n    public String getBaseUrl() { return \"x\"; }\n  }\n}\n"
    outer = declared_methods(class_declaration(nested, "ArtemisProperties"))
    assert "getMail" in outer and "getBaseUrl" not in outer, outer
    assert "getBaseUrl" in declared_methods(class_declaration(nested, "Mail"))

    # a dotted and a quoted YAML key keep their own line, and the keys after them are not lost
    block = ["artemis:", "    deimos.llm.no-such-key: 1", "    'deimos':", "        llm:", "            temperature: 0"]
    assert list(flatten_yaml_with_lines(block)) == [(0, "artemis"), (1, "artemis.deimos.llm.no-such-key"), (2, "artemis.deimos"), (3, "artemis.deimos.llm"), (4, "artemis.deimos.llm.temperature")]

    # an environment variable has to name a whole key, and a bound path has to be complete
    empty = SourceIndex(scan=False)
    empty._add_key("artemis.continuous-integration.build-timeout-seconds.max")
    assert empty.knows_environment_variable("ARTEMIS_CONTINUOUSINTEGRATION_BUILDTIMEOUTSECONDS_MAX")
    assert not empty.knows_environment_variable("ARTEMIS_CONTINUOUSINTEGRATION_BUILDTIMEOUTSECONDS_MA")
    bound = '@ConfigurationProperties(prefix = "artemis.feature-usage")\npublic record FeatureUsageProperties(boolean enabled, Digest digest, Map<String, Cost> costs) {\n    public record Digest(List<String> recipients, int minimumSessions) {}\n    public record Cost(double input) {}\n}\n'
    empty.add_binding(bound, "artemis.feature-usage", bound.index('")') + 1)
    assert empty.knows_property("artemis.feature-usage.digest.recipients") and empty.knows_property("artemis.feature-usage.costs.any-model.input")
    assert not empty.knows_property("artemis.feature-usage.recipients")
    assert empty.knows_environment_variable("ARTEMIS_FEATUREUSAGE_DIGEST_MINIMUMSESSIONS")

    # a static constant is not bound, whatever its modifiers are
    weaviate = '@ConfigurationProperties(prefix = "artemis.weaviate")\npublic class WeaviateProperties {\n    public static final String DEFAULT_HTTP_PORT = "8080";\n    private static int counter;\n    private int httpPort;\n}\n'
    constants = SourceIndex(scan=False)
    constants.add_binding(weaviate, "artemis.weaviate", weaviate.index('")') + 1)
    assert constants.knows_property("artemis.weaviate.http-port") and constants.knows_environment_variable("ARTEMIS_WEAVIATE_HTTPPORT")
    assert not constants.knows_property("artemis.weaviate.default-http-port") and not constants.knows_environment_variable("ARTEMIS_WEAVIATE_DEFAULTHTTPPORT")
    assert not constants.knows_property("artemis.weaviate.counter")
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
