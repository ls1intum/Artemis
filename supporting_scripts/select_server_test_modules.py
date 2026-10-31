"""Conservatively select server test packages for get_changed_modules.sh.

The public interface is the shell script. Empty stdout means the full suite, never
"no tests". This deliberately overapproximates type consumers from BOTH sides of
the diff: deleted classes/imports and moves still contribute dependency edges.
Contract and dynamic-wiring changes cannot be scoped by imports and run in full.
"""

from collections import defaultdict, deque
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys


PACKAGE = "de.tum.cit.aet.artemis"
ROOTS = ("src/main/java/", "src/test/java/")
MODULE_ROOTS = tuple(root + PACKAGE.replace(".", "/") + "/" for root in ROOTS)
GLOBAL_MODULES = {"core", "shared", "config"}
CONTRACT_DIRECTORIES = {
    "domain", "dto", "api", "config", "configuration", "event", "events",
    "aspect", "aspects", "aop", "annotation", "annotations", "exception", "security",
}
# Server tests read these client/doc files from disk. Check them before the
# non-server allowlist below; mixed Java/input changes also require the full suite.
# See has_server_test_inputs in .github/workflows/ci.yml. Resource inputs already
# take the default global-path fallback.
SERVER_DISK_INPUT_PREFIXES = ("src/main/webapp/i18n/",)
SERVER_DISK_INPUT_FILES = {"documentation/docs/developer/feature-usage-catalogue.mdx"}
# Keep this allowlist narrow. Every other non-Java path (including CI, Gradle,
# resources, dependencies and unknown files) invalidates module selection.
NON_SERVER_PREFIXES = (
    "documentation/", "src/main/webapp/", "src/test/playwright/", "packages/tum-aet-ui/",
)
NON_SERVER_FILES = {"README.md", "AGENTS.md", "CLAUDE.md", "LICENSE"}
IDENTIFIER = r"[A-Za-z_$][A-Za-z0-9_$]*"
QUALIFIED = rf"{IDENTIFIER}(?:\.{IDENTIFIER})*"
LEXEMES = re.compile(
    r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\n]*'
)
DYNAMIC_WIRING = re.compile(
    # Advice and enabling/import annotations can install cross-module behavior
    # without introducing a type reference from the affected controllers/beans.
    r"@(?:[\w.]+\.)?(?:Bean|Configuration|TestConfiguration|ComponentScan|Import|ImportResource|"
    r"ControllerAdvice|RestControllerAdvice|Enable\w*|EventListener|"
    r"TransactionalEventListener|Scheduled|Aspect|Around|Before|After|AfterReturning|"
    r"AfterThrowing|Conditional\w*)\b|"
    r"\b(?:ApplicationEventPublisher|ApplicationListener|BeanFactory|ApplicationContext|"
    r"ServiceLoader|ReflectionUtils|MethodHandles)\b|"
    r"\b(?:publishEvent|forName|getBean|loadClass|registerBean|registerSingleton)\s*\("
)


class FullSuite(Exception):
    """The affected set cannot safely be narrowed."""


def git(*args, input_data=None):
    result = subprocess.run(["git", *args], input=input_data, capture_output=True, check=False)
    if result.returncode:
        raise FullSuite(f"git {args[0]} failed: {result.stderr.decode(errors='replace').strip()[:300]}")
    return result.stdout


def code_only(text):
    """Hide comments and literals without treating // inside a string as a comment."""
    return LEXEMES.sub(lambda match: re.sub(r"[^\n]", " ", match[0]), text)


def owner(path):
    for root in MODULE_ROOTS:
        if path.startswith(root):
            parts = PurePosixPath(path[len(root):]).parts
            if len(parts) > 1 and re.fullmatch(r"[a-z][a-z0-9]*", parts[0]):
                return parts[0]
    return None


def changed_source(path):
    if path.startswith(SERVER_DISK_INPUT_PREFIXES) or path in SERVER_DISK_INPUT_FILES:
        raise FullSuite(f"server test disk input change: {path}")
    if path.startswith(NON_SERVER_PREFIXES) or path in NON_SERVER_FILES:
        return False
    module = owner(path)
    if not module or module in GLOBAL_MODULES or not path.endswith(".java"):
        raise FullSuite(f"global or unrecognized change: {path}")
    root = next(root for root in MODULE_ROOTS if path.startswith(root))
    parts = PurePosixPath(path[len(root):]).parts
    if any(part in CONTRACT_DIRECTORIES for part in parts[1:-1]):
        raise FullSuite(f"contract/configuration change: {path}")
    if parts[-1] in {"package-info.java", "module-info.java"}:
        raise FullSuite(f"package/module metadata change: {path}")
    if root == MODULE_ROOTS[1]:
        # Helpers, abstract test bases and repositories can affect every Spring
        # context without a direct type import from each test that uses them.
        if (parts[-1].startswith("Abstract")
                or not re.search(r"(?:Test|Tests|IT)\.java$", parts[-1])
                or any(part in {"util", "base", "test_repository", "config"} for part in parts[1:-1])):
            raise FullSuite(f"test support change: {path}")
    elif len(parts) < 3 or parts[1] not in {"service", "web", "util", "factories"}:
        raise FullSuite(f"unclassified production change: {path}")
    return True


def base_sources(revision):
    """Read all source blobs in two Git calls rather than one process per file."""
    entries = []
    tree = git("ls-tree", "-rz", revision, "--", *ROOTS)
    for entry in tree.split(b"\0"):
        if not entry:
            continue
        metadata, path_bytes = entry.split(b"\t", 1)
        mode, kind, object_id = metadata.split()
        path = path_bytes.decode()
        if path.endswith(".java"):
            if kind != b"blob" or mode not in {b"100644", b"100755"}:
                raise FullSuite(f"unsupported source entry: {path}")
            entries.append((path, object_id))
    blobs = git("cat-file", "--batch", input_data=b"".join(oid + b"\n" for _, oid in entries))
    offset = 0
    for path, _ in entries:
        end = blobs.index(b"\n", offset)
        _, kind, length = blobs[offset:end].split()
        if kind != b"blob":
            raise FullSuite(f"unreadable source blob: {path}")
        size = int(length)
        yield path, blobs[end + 1:end + 1 + size].decode()
        offset = end + size + 2


def current_sources():
    for entry in git("ls-files", "-s", "-z", "--", *ROOTS).split(b"\0"):
        if not entry:
            continue
        metadata, path_bytes = entry.split(b"\t", 1)
        mode, _, stage = metadata.split()
        path = path_bytes.decode()
        if not path.endswith(".java"):
            continue
        if stage != b"0" or mode not in {b"100644", b"100755"} or Path(path).is_symlink():
            raise FullSuite(f"unsupported/unmerged source entry: {path}")
        if Path(path).exists():
            yield path, Path(path).read_text(encoding="utf-8")


@dataclass
class Source:
    path: str
    code: str
    package: str
    types: set
    ambiguous_supertype: bool = False


def parse_source(path, contents):
    code = code_only(contents)
    if not code.strip():
        return None  # Existing empty Java placeholders do not declare types.
    packages = re.findall(rf"(?m)^\s*package\s+({QUALIFIED})\s*;", code)
    types = set(re.findall(rf"\b(?:class|interface|enum|record)\s+({IDENTIFIER})", code))
    if len(packages) != 1 or (not types and not path.endswith("package-info.java")) or "\\u" in code:
        raise FullSuite(f"unsupported Java declaration: {path}")
    root = next(root for root in ROOTS if path.startswith(root))
    expected_package = str(PurePosixPath(path[len(root):]).parent).replace("/", ".")
    if packages[0] != expected_package:
        raise FullSuite(f"package/path mismatch: {path}")
    return Source(path, code, packages[0], types)


def dependency_graph(sources):
    symbols = defaultdict(set)
    packages = defaultdict(set)
    declared_types = defaultdict(set)
    for source in sources:
        declared_types[source.path] |= source.types
        for name in source.types:
            symbols[source.package + "." + name].add(source.path)
            packages[source.package].add(source.package + "." + name)

    def resolve(qualified):
        # Static members and nested classes belong to their declaring source.
        while qualified:
            if qualified in symbols:
                return set(symbols[qualified])
            # Hibernate generates Entity_ from its persistent domain type.
            # It is absent from Git, but its consumer is also a consumer of
            # the entity (and must not disable selection for unrelated PRs).
            if qualified.endswith("_") and ".domain." in qualified and qualified[:-1] in symbols:
                return set(symbols[qualified[:-1]])
            qualified = qualified.rpartition(".")[0]
        return set()

    consumers = defaultdict(set)
    for source in sources:
        dependencies = set()
        imported_types = defaultdict(set)
        for imported, wildcard in re.findall(rf"\bimport\s+(?:static\s+)?({QUALIFIED})(\.\*)?\s*;", source.code):
            resolved = resolve(imported)
            if wildcard and imported in packages:
                for symbol in packages[imported]:
                    resolved |= symbols[symbol]
                    imported_types[symbol.rsplit(".", 1)[1]] |= symbols[symbol]
            elif wildcard:
                # A type wildcard can import nested interfaces/classes, including
                # an implemented interface whose consumers must also be traced.
                for path in resolved:
                    for name in declared_types[path]:
                        imported_types[name].add(path)
            elif not wildcard:
                imported_types[imported.rsplit(".", 1)[1]] |= resolved
            if not resolved and imported.startswith(PACKAGE + "."):
                raise FullSuite(f"unresolved internal import in {source.path}: {imported}")
            dependencies |= resolved

        tokens = set(re.findall(IDENTIFIER, source.code))
        for name in tokens:
            dependencies |= symbols.get(source.package + "." + name, set())
        for qualified in re.findall(rf"\b{IDENTIFIER}(?:\.{IDENTIFIER})+", source.code):
            dependencies |= resolve(qualified)
        for dependency in dependencies:
            consumers[dependency].add(source.path)

        # Implementations can be injected through interfaces/supertypes. Make
        # inheritance edges bidirectional so their consumers remain covered.
        # Test subclasses are executed independently, never injected as beans;
        # their shared bases must not pull every sibling test into a selection.
        if not source.path.startswith(ROOTS[0]):
            continue
        for header in re.findall(rf"\b(?:class|interface|enum|record)\s+{IDENTIFIER}([^{{;}}]*)\{{", source.code):
            inheritance = re.search(r"\b(?:extends|implements)\b(.*)", header, re.DOTALL)
            if not inheritance:
                continue
            parents = set()
            for name in re.findall(QUALIFIED, inheritance[1]):
                if name in {"extends", "implements", "permits", "super"}:
                    continue
                resolved = (resolve(name) | imported_types.get(name, set())
                            | symbols.get(source.package + "." + name, set()))
                if not resolved:
                    # An external interface (Supplier, listener/provider SPI,
                    # etc.) can be injected without mentioning its concrete
                    # implementation. Generic bounds we cannot resolve also
                    # take this conservative path.
                    source.ambiguous_supertype = True
                parents |= resolved
            consumers[source.path] |= parents
    return consumers


def select(base_ref):
    roots = git("merge-base", "--all", base_ref, "HEAD").decode().split()
    if len(roots) != 1:
        raise FullSuite("missing or ambiguous merge base")
    base = roots[0]
    changed = [path.decode() for path in git("diff", "--no-renames", "--name-only", "-z", base, "--").split(b"\0") if path]
    seeds = {path for path in changed if changed_source(path)}
    if not seeds:
        raise FullSuite("no scoped server changes")

    base_contents = dict(base_sources(base))
    known_modules = {owner(path) for path in base_contents if path.startswith(MODULE_ROOTS[0])}
    if any(owner(path) not in known_modules for path in seeds):
        raise FullSuite("a changed source belongs to a new or unknown module")
    current_contents = dict(current_sources())
    sources = []
    for path in base_contents.keys() | current_contents.keys():
        for contents in {base_contents.get(path), current_contents.get(path)} - {None}:
            source = parse_source(path, contents)
            if source:
                sources.append(source)
    declared_paths = {source.path for source in sources}
    if not seeds <= declared_paths:
        raise FullSuite("a changed source has no recognized declaration")
    graph = dependency_graph(sources)
    affected = set(seeds)
    pending = deque(seeds)
    while pending:
        for consumer in graph[pending.popleft()] - affected:
            affected.add(consumer)
            pending.append(consumer)

    for source in sources:
        if source.path in affected:
            if source.ambiguous_supertype or DYNAMIC_WIRING.search(source.code):
                raise FullSuite(f"dynamic/ambiguous wiring in affected source: {source.path}")
    modules = {owner(path) for path in affected}
    if None in modules:
        raise FullSuite("an affected consumer is outside the module test packages")
    # A broad selection on one runner loses the full suite's sharding. Prefer
    # the full matrix once either module breadth or test footprint reaches half.
    if len(modules) * 2 >= len(known_modules - {None}):
        raise FullSuite("affected modules cover at least half the server")
    test_classes = {
        source.path for source in sources
        if source.path.startswith(ROOTS[1]) and re.search(r"(?:Test|Tests|IT)\.java$", source.path)
    }
    # Match gradle/test.gradle's mandatory global checks, including dynamic scanners without
    # import edges. Count their cost before replacing the full seven-shard matrix with one job.
    selected_test_classes = {
        path for path in test_classes
        if owner(path) in modules | {"shared", "core"}
        or "/architecture/" in path
        or path.endswith("/videosource/service/GocastLoggingSafetyTest.java")
    }
    if not test_classes or len(selected_test_classes) * 2 >= len(test_classes):
        raise FullSuite("selected tests cover at least half the server test classes")
    return ",".join(sorted(modules))


def main():
    if len(sys.argv) != 2:
        print("Usage: get_changed_modules.sh <branch_to_compare>", file=sys.stderr)
        return 1
    try:
        selected = select(sys.argv[1])
    except (FullSuite, OSError, ValueError, UnicodeError) as error:
        print(f"Server test selection: full suite ({error})", file=sys.stderr)
        selected = ""
    print(selected)
    return 0


if __name__ == "__main__":
    sys.exit(main())
