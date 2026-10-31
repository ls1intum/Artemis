"""Conservatively select server test packages for get_changed_modules.sh.

The public interface is the shell script. Empty stdout means the full suite, never
"no tests". This deliberately overapproximates type consumers from BOTH sides of
the diff: deleted classes/imports and moves still contribute dependency edges.
Contract and dynamic-wiring changes cannot be scoped by imports and run in full.
"""

from collections import defaultdict, deque
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
import argparse
import json
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
    contents: str = ""


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
    return Source(path, code, packages[0], types, contents=contents)


def type_headers(code):
    return re.findall(rf"\b(?:class|interface|enum|record)\s+{IDENTIFIER}[^{{;}}]*\{{", code)


def without_generics(header):
    # Parent type arguments are ordinary dependencies, never supertypes.
    while re.search(r"<[^<>]*>", header):
        header = re.sub(r"<[^<>]*>", "", header)
    return header


def stable_constants(base_contents, current_contents):
    def declarations(contents):
        code = code_only(contents)
        result = {}
        for match in re.finditer(
                rf"\bstatic\s+final\s+(?:String|boolean|byte|short|int|long|char|float|double)\s+"
                rf"({IDENTIFIER})\s*=(?P<value>[^;]*);", code):
            # Only literal compile-time constants can be unrelated to changed
            # behavior. A final singleton or a method-computed String can change.
            start, end = match.span("value")
            value = contents[start:end].strip()
            if re.fullmatch(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|true|false|[-+]?\d[\w.]*', value):
                result[match[1]] = contents[match.start():match.end()]
        return result
    result = {}
    for path in base_contents.keys() & current_contents.keys():
        before = declarations(base_contents[path])
        after = declarations(current_contents[path])
        result[path] = {name for name in before.keys() & after.keys() if before[name] == after[name]}
    return result


def dependency_graph(sources, precise=False, constants=None, test_references=None):
    constants = constants or {}
    symbols = defaultdict(set)
    packages = defaultdict(set)
    declared_types = defaultdict(set)
    sources_by_path = defaultdict(list)
    for source in sources:
        sources_by_path[source.path].append(source)
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

    empty_contracts = {
        source.path for source in sources
        if re.fullmatch(r"\s*}\s*", source.code.partition("{")[2])
    }
    consumers = defaultdict(set)
    parents_by_path = defaultdict(set)
    inheritance_uses = defaultdict(lambda: defaultdict(int))
    code_tokens_by_path = defaultdict(lambda: defaultdict(int))
    for source in sources:
        dependencies = set()
        body_code = re.sub(r"\bimport\s+[^;]+;", "", source.code) if precise else source.code
        body_tokens = set(re.findall(IDENTIFIER, body_code))
        member_references = defaultdict(set)
        if precise:
            for type_name, member in re.findall(rf"\b({IDENTIFIER})\.({IDENTIFIER})\b", body_code):
                member_references[type_name].add(member)
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
            if not precise or wildcard or imported.rsplit(".", 1)[1] in body_tokens:
                dependencies |= resolved

        for token in re.findall(IDENTIFIER, body_code):
            code_tokens_by_path[source.path][token] += 1
        tokens = body_tokens
        for name in tokens:
            dependencies |= symbols.get(source.package + "." + name, set())
        for qualified in re.findall(rf"\b{IDENTIFIER}(?:\.{IDENTIFIER})+", body_code):
            dependencies |= resolve(qualified)
        if test_references is not None and source.path.startswith(ROOTS[1]):
            for dependency in dependencies:
                if dependency.startswith(ROOTS[1]):
                    test_references[dependency].add(source.path)
        if precise:
            for dependency in set(dependencies):
                names = declared_types[dependency]
                remaining = body_code
                used_constant = False
                for name in names:
                    for constant in constants.get(dependency, set()) & member_references[name]:
                        remaining, count = re.subn(rf"\b(?:{QUALIFIED}\.)?{name}\.{constant}\b", "", remaining)
                        used_constant |= count > 0
                if used_constant and not any(re.search(rf"\b{name}\b", remaining) for name in names):
                    dependencies.remove(dependency)
        for dependency in dependencies:
            consumers[dependency].add(source.path)

        # Implementations can be injected through interfaces/supertypes. Make
        # inheritance edges bidirectional so their consumers remain covered.
        # Test subclasses are executed independently, never injected as beans;
        # their shared bases must not pull every sibling test into a selection.
        if not source.path.startswith(ROOTS[0]):
            continue
        headers = re.findall(rf"\b(?:class|interface|enum|record)\s+{IDENTIFIER}([^{{;}}]*)\{{", source.code)
        for header in headers:
            if precise:
                header = without_generics(header).split("permits", 1)[0]
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
                if precise:
                    for parent in resolved:
                        inheritance_uses[(source.path, parent)][name.rsplit(".", 1)[-1]] += 1
            # Empty module API markers carry no dispatchable behavior. A leaf
            # implementation fix does not change all sibling API implementations.
            # Changes to the marker itself remain global contract changes.
            if precise:
                parents_by_path[source.path] |= parents
            else:
                consumers[source.path] |= parents
    if precise:
        unsafe_parents = {
            source.path for source in sources if source.path.startswith(ROOTS[0])
            and (source.ambiguous_supertype or DYNAMIC_WIRING.search(local_wiring(source.code)[1]))
        }
        empty_contracts -= unsafe_parents
        while True:
            inherited_behavior = {path for path in empty_contracts if parents_by_path[path] - empty_contracts}
            if not inherited_behavior:
                break
            empty_contracts -= inherited_behavior
        direct_consumers = {path: set(users) for path, users in consumers.items()}
        for child in list(parents_by_path):
            ancestors = set()
            pending = list(parents_by_path[child])
            while pending:
                parent = pending.pop()
                if parent not in ancestors:
                    ancestors.add(parent)
                    pending.extend(parents_by_path[parent])
            if ancestors & unsafe_parents:
                for source in sources_by_path[child]:
                    source.ambiguous_supertype = True
            for parent in ancestors - empty_contracts:
                for user in direct_consumers.get(parent, set()):
                    if user == parent:
                        continue
                    inherited = inheritance_uses[(user, parent)]
                    # Reaching an implementation affects callers dispatching
                    # through its parent, not sibling implementations that only
                    # name the parent in their own inheritance declaration.
                    if not inherited or any(code_tokens_by_path[user][name] > count for name, count in inherited.items()):
                        consumers[child].add(user)
    return consumers


def analyze(base_ref, precise=False):
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
    test_references = defaultdict(set)
    graph = dependency_graph(sources, precise=precise, constants=stable_constants(base_contents, current_contents),
                             test_references=test_references)
    affected = set(seeds)
    pending = deque(seeds)
    while pending:
        for consumer in graph[pending.popleft()] - affected:
            affected.add(consumer)
            pending.append(consumer)

    return seeds, affected, sources, known_modules, base_contents, current_contents, graph, test_references


def select(base_ref):
    seeds, affected, sources, known_modules, _, _, _, _ = analyze(base_ref)
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


# Existing feature conditions and schedules are local when their wiring metadata
# is unchanged. Wiring changes and behavior that dispatches through names/events
# remain global; imports cannot discover their downstream consumers.
LOCAL_WIRING = re.compile(r"@(?:[\w.]+\.)?(?:Conditional\w*|Scheduled|EventListener|TransactionalEventListener)\b")


def local_wiring(contents, all_annotations=False):
    """Read balanced annotation arguments and their attached declaration."""
    code = code_only(contents)
    signatures = []
    spans = []
    matcher = re.compile(rf"@{QUALIFIED}\b") if all_annotations else LOCAL_WIRING
    for match in matcher.finditer(code):
        end = match.end()
        while end < len(code) and code[end].isspace():
            end += 1
        if end < len(code) and code[end] == "(":
            depth = 1
            end += 1
            while end < len(code) and depth:
                depth += (code[end] == "(") - (code[end] == ")")
                end += 1
            if depth:
                raise FullSuite("unbalanced wiring annotation")
        spans.append((match.start(), end))
        declaration_end = code.find("{", end)
        if declaration_end < 0:
            raise FullSuite("unsupported wiring declaration")
        signatures.append(contents[match.start():declaration_end])
    for start, end in reversed(spans):
        code = code[:start] + " " * (end - start) + code[end:]
    return signatures, code


GLOBAL_SCANNER = re.compile(
    r"ClassFileImporter|ClassGraph|Reflections|RequestMappingHandlerMapping|"
    r"(?:src[/\\\"', +]+(?:main|test)[/\\\"', +]+(?:java|webapp))|feature-usage-catalogue"
)


def ordinary_test(source):
    name = PurePosixPath(source.path).name
    return (source.path.startswith(ROOTS[1])
            and bool(re.search(r"(?:Test|Tests|IT)\.java$", name))
            and not name.startswith("Abstract") and "IT" not in name and "IntTest" not in name
            and not re.search(r"@Tag\(\s*\"nightly-lti\"\s*\)", source.contents))


def global_test_guards(sources, graph):
    """Keep runtime/source scanners, including future subclasses of scanners."""
    guards = {
        source.path for source in sources if source.path.startswith(ROOTS[1]) and (
            "/architecture/" in source.path
            or "/core/authorization/" in source.path
            or "/core/service/featureusage/" in source.path
            or "/core/service/distributed/" in source.path
            or source.path.endswith("/videosource/service/GocastLoggingSafetyTest.java")
            or re.search(r"@Tag\(\s*\"ArchitectureTest\"\s*\)", source.contents)
            or GLOBAL_SCANNER.search(source.contents))
    }
    pending = deque(guards)
    while pending:
        for consumer in graph[pending.popleft()] - guards:
            if consumer.startswith(ROOTS[1]):
                guards.add(consumer)
                pending.append(consumer)
    return guards



JAVA_STRING = re.compile(r'"(?:\\.|[^"\\])*"')
HTTP_MAPPING = re.compile(r"@(?:[\w.]+\.)?(?:RequestMapping|GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|HeadMapping|OptionsMapping)\b")
HTTP_CALL = re.compile(
    r"\b(?P<receiver>request|mockMvc|mvc|restTemplate|webTestClient|MockMvcRequestBuilders)\s*\.\s*"
    r"(?P<method>get\w*|post\w*|put\w*|delete\w*|patch\w*|head\w*|options\w*|perform\w*|exchange|execute|multipart)\s*\("
)
# This transport receives its URL from callers; it owns no application routes.
# Route-owning test helpers below are followed transitively to their callers.
HTTP_TRANSPORT = "src/test/java/de/tum/cit/aet/artemis/core/util/RequestUtilService.java"


def java_literals(contents):
    uncommented = LEXEMES.sub(lambda match: " " * len(match[0]) if match[0].startswith(("//", "/*")) else match[0], contents)
    return [match[0][1:-1] for match in JAVA_STRING.finditer(uncommented)]



def mapping_paths(arguments):
    """Read only path/value arguments, never produces/headers annotation metadata."""
    code = code_only(arguments)
    explicit = list(re.finditer(r"\b(?:path|value)\s*=", code))
    if explicit:
        expressions = [first_argument(arguments, code, match.end()) for match in explicit]
    elif re.match(r"\s*\w+\s*=", code):
        return [], False  # Named metadata without a path uses the enclosing base.
    elif code.strip() or java_literals(arguments):
        expressions = [first_argument(arguments, code, 0)]
    else:
        return [], False
    paths = []
    unknown = False
    for expression in expressions:
        # A string or array of strings is bounded. A constant/concatenation in
        # any alias remains unsupported even if another alias is a literal.
        if re.fullmatch(r"[\s{},]*", code_only(expression)):
            paths.extend(java_literals(expression))
        else:
            unknown = True
    return paths, unknown


def http_route_owners(sources):
    """Map URL prefixes to owners, including older routes without module names."""
    prefixes = defaultdict(set)
    unknown = set()
    for source in sources:
        if not source.path.startswith(ROOTS[0]) or not HTTP_MAPPING.search(source.code):
            continue
        module = owner(source.path)
        mappings = []
        public_base = False
        fixed_namespace = False
        for match in HTTP_MAPPING.finditer(source.code):
            start = match.end()
            while start < len(source.code) and source.code[start].isspace():
                start += 1
            if start < len(source.code) and source.code[start] == "(":
                depth = 1
                end = start + 1
                while end < len(source.code) and depth:
                    depth += (source.code[end] == "(") - (source.code[end] == ")")
                    end += 1
                arguments = source.contents[start + 1:end - 1]
            else:
                arguments = ""
            values, unresolved = mapping_paths(arguments)
            at_type = match.start() < source.code.find("{")
            if at_type:
                fixed_namespace = bool(values) and not unresolved and all(
                    re.match(r"/?api/[A-Za-z][A-Za-z0-9_-]*(?:/|$)", value) for value in values)
                for value in values:
                    first = value.strip("/").split("/", 1)[0]
                    if first and first != "api":
                        if re.fullmatch(r"[A-Za-z0-9_.-]+", first):
                            public_base = True
                        else:
                            unknown.add(source.path)
            # Spring combines method mappings underneath the class mapping.
            # A LOGIN_REGEX/relative method alias cannot change a proven class
            # API namespace. An unresolved class alias remains global.
            if unresolved and (at_type or not fixed_namespace):
                unknown.add(source.path)
            mappings.extend(values)
        found = public_base
        root_api = any(value.strip("/") == "api" for value in mappings)
        for value in mappings:
            route = value.strip("/")
            if route.startswith("api/"):
                segment = route[4:].split("/", 1)[0]
            elif root_api and route and route != "api":
                segment = route.split("/", 1)[0]
            else:
                continue
            if re.fullmatch(r"[A-Za-z][A-Za-z0-9_-]*", segment):
                prefixes[segment].add(module)
                found = True
            else:
                unknown.add(source.path)
        if not found:
            unknown.add(source.path)
    return prefixes, unknown




HTTP_VERBS = {"get", "post", "put", "delete", "patch", "head", "options", "multipart", "request"}


def transport_http_methods(sources):
    methods = HTTP_VERBS | {"perform", "performMvcRequest", "exchange", "execute"}
    for source in sources:
        if source.path != HTTP_TRANSPORT:
            continue
        if any(re.search(r"/?api/", value) for value in java_literals(source.contents)):
            raise FullSuite("generic HTTP transport declares application routes")
        # RequestUtil also exposes getObjectMapper; request mock callbacks have
        # getURI/getBody. Only its path/builder-taking methods initiate a request.
        methods |= set(re.findall(
            rf"\bpublic\s+[^{{;\n]+?\b({IDENTIFIER})\s*\(\s*(?:String|AbstractMockHttpServletRequestBuilder)\b", source.code))
    return methods


def http_calls(source, methods):
    request_aliases = set(re.findall(rf"\bRequestUtilService\s+({IDENTIFIER})\b", source.code)) | {"request"}
    calls = [match for match in HTTP_CALL.finditer(source.code)
             if match["receiver"] not in request_aliases or match["method"] in methods]
    aliases = request_aliases - {"request"}
    if aliases:
        matcher = re.compile(rf"\b(?:{'|'.join(sorted(aliases))})\s*\.\s*(?:{'|'.join(sorted(methods))})\s*\(")
        calls.extend(matcher.finditer(source.code))
    # A RequestPostProcessor/type import does not initiate HTTP. Only actual
    # calls to statically imported MockMvc request builders add URI consumers.
    imported = set(re.findall(
        r"\bimport\s+static\s+org\.springframework\.test\.web\.servlet\.request\.MockMvcRequestBuilders\.(\w+|\*)\s*;", source.code))
    builders = HTTP_VERBS if "*" in imported else HTTP_VERBS & imported
    if builders:
        calls.extend(re.finditer(rf"(?<![\w.])(?:{'|'.join(sorted(builders))})\s*\(", source.code))
    return calls


def first_argument(contents, code, offset):
    depth = 0
    end = offset
    while end < len(code):
        value = code[end]
        if depth == 0 and value in ",);":
            break
        depth += value in "([{"
        depth -= value in ")]}"
        end += 1
    return contents[offset:end].strip()


def literal_http_prefix(expression, appended=False):
    match = JAVA_STRING.match(expression.lstrip())
    if not match:
        return None
    value = match[0][1:-1]
    route = re.match(r"/?api/([A-Za-z][A-Za-z0-9_-]*)(/|\?|$)", value)
    # /api/alpha + suffix could become /api/alpha-other; a fixed module
    # segment followed by a slash cannot be changed by the remaining suffix.
    suffix = expression.lstrip()[match.end():].strip()
    if suffix and not suffix.startswith("+"):
        return None  # replace/substring and other transformations can change the module
    if route and (route[2] or (not suffix and not appended)):
        return route[1]
    return None


def classified_http_target(expression, source, seen=None, appended=False):
    """Resolve bounded local literal prefixes; ambiguous expressions stay global."""
    expression = expression.strip()
    route = literal_http_prefix(expression, appended=appended)
    if route:
        return {route}
    seen = set() if seen is None else seen
    # URI construction and String.format preserve their literal first argument.
    wrapper = re.match(r"(?:new\s+URI|URI\.create|String\.format)\s*\(", expression)
    if wrapper:
        code = code_only(expression)
        depth, end = 1, wrapper.end()
        while end < len(code) and depth:
            depth += (code[end] == "(") - (code[end] == ")")
            end += 1
        suffix = expression[end:].strip()
        if depth or (suffix and not suffix.startswith("+")):
            return set()
        nested = first_argument(expression, code, wrapper.end())
        return classified_http_target(nested, source, seen, appended=appended or bool(suffix))
    identifier = re.match(rf"({IDENTIFIER})(?:\s*\+|\s*$)", expression)
    if identifier:
        name = identifier[1]
        if name in seen:
            return set()
        seen = seen | {name}
        # A same-named method parameter can shadow a field or another method's
        # local URL. Never infer its route from those unrelated assignments.
        if re.search(rf"(?:\(|,)\s*(?:final\s+)?(?:String|URI)\s+{name}\s*[,)]", source.code):
            return set()
        assignments = list(re.finditer(rf"\b{name}\s*=(?!=)", source.code))
        if not assignments:
            return set()
        routes = set()
        for assignment in assignments:
            value = first_argument(source.contents, source.code, assignment.end())
            resolved = classified_http_target(value, source, seen, appended=appended or "+" in expression)
            if not resolved:
                return set()
            routes |= resolved
        return routes
    # A local helper returning one literal URI is common in REST integration
    # tests. Complex helpers/switches/builders are deliberately unclassified.
    method = re.fullmatch(rf"({IDENTIFIER})\s*\([^()]*\)", expression)
    if method and method[1] not in seen:
        declarations = list(re.finditer(rf"\b(?:String|URI)\s+{method[1]}\s*\([^)]*\)\s*\{{", source.code))
        if len(declarations) == 1:
            declaration = declarations[0]
            end = source.code.find("}", declaration.end())
            body = source.contents[declaration.end():end]
            returns = list(re.finditer(r"\breturn\s+", code_only(body)))
            if len(returns) == 1 and "{" not in code_only(body):
                value = first_argument(body, code_only(body), returns[0].end())
                return classified_http_target(value, source, seen | {method[1]}, appended=appended)
    return set()


def http_test_consumers(sources, production_modules, test_references, affected_production):
    """HTTP tests often use Map/String JSON and have no Java controller import."""
    prefixes, unknown = http_route_owners(sources)
    if affected_production & unknown:
        raise FullSuite("an affected HTTP controller has an unclassified route")
    # The canonical module prefix also covers route-owning helpers in fixtures
    # and API call strings when a controller is deleted from one revision.
    for source in sources:
        module = owner(source.path)
        if module:
            prefixes[module].add(module)
    consumers = set()
    methods = transport_http_methods(sources)
    for source in sources:
        if not source.path.startswith(ROOTS[1]) or source.path == HTTP_TRANSPORT:
            continue
        literals = java_literals(source.contents)
        routes = {match[1] for value in literals for match in re.finditer(r"/?api/([A-Za-z][A-Za-z0-9_-]*)", value)}
        # An external mock expectation may mention /api/v1 or a conversion URL.
        # Only an actual application HTTP call with an unknown target is global.
        unclassified = False
        calls = http_calls(source, methods)
        for call in calls:
            target = first_argument(source.contents, source.code, call.end())
            resolved = classified_http_target(target, source)
            if not resolved or resolved - prefixes.keys():
                unclassified = True
            routes |= resolved
        if unclassified or any(prefixes.get(route, set()) & production_modules for route in routes):
            consumers.add(source.path)
    pending = deque(consumers)
    while pending:
        for consumer in test_references[pending.popleft()] - consumers:
            if consumer.startswith(ROOTS[1]):
                consumers.add(consumer)
                pending.append(consumer)
    return consumers


def select_plan(base_ref):
    seeds, affected, sources, _, base_contents, current_contents, graph, test_references = analyze(base_ref, precise=True)
    for source in sources:
        if source.path not in affected:
            continue
        # Existing test configurations are exercised by the selected test; they
        # cannot add production consumers. Changed test support still fails in
        # changed_source, and changed nested configuration is checked below.
        if source.path.startswith(ROOTS[1]) and source.path not in seeds:
            continue
        if source.path in seeds:
            before = base_contents.get(source.path, "")
            after = current_contents.get(source.path, "")
            if source.path.startswith(ROOTS[0]) and (
                    not before or not after
                    or type_headers(code_only(before)) != type_headers(code_only(after))):
                raise FullSuite(f"changed production type or wiring membership: {source.path}")
            if (local_wiring(before)[0] != local_wiring(after)[0]
                    or (source.path.startswith(ROOTS[0])
                        and local_wiring(before, all_annotations=True)[0] != local_wiring(after, all_annotations=True)[0])):
                raise FullSuite(f"changed dynamic wiring: {source.path}")
        if source.ambiguous_supertype or DYNAMIC_WIRING.search(local_wiring(source.contents)[1]):
            raise FullSuite(f"dynamic/ambiguous wiring in affected source: {source.path}")
    current = [source for source in sources if current_contents.get(source.path) == source.contents]
    guards = global_test_guards(sources, graph)
    production_modules = {owner(path) for path in affected if path.startswith(ROOTS[0])}
    if production_modules:
        affected |= http_test_consumers(sources, production_modules, test_references,
                                        {path for path in affected if path.startswith(ROOTS[0])})
    modules = {owner(path) for path in affected}
    if None in modules:
        raise FullSuite("an affected consumer is outside the module test packages")
    tests = {source.path: source for source in current if ordinary_test(source)}
    selected = {path for path in tests if path in affected or path in guards or owner(path) in production_modules}
    if not selected:
        raise FullSuite("no ordinary tests selected")
    if len(selected) * 2 >= len(tests):
        raise FullSuite("selected tests cover at least half the server test classes")
    return {
        "mode": "selected", "reason": "affected production modules, test consumers and global guards",
        "modules": sorted(modules),
        "tests": sorted(tests[path].package + "." + PurePosixPath(path).stem for path in selected),
        "selectedTestClasses": len(selected), "totalTestClasses": len(tests),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--format", choices=("csv", "json"), default="csv")
    parser.add_argument("base_ref")
    arguments = parser.parse_args()
    try:
        selected = select_plan(arguments.base_ref) if arguments.format == "json" else select(arguments.base_ref)
    except (FullSuite, OSError, ValueError, UnicodeError) as error:
        reason = str(error)
        print(f"Server test selection: full suite ({reason})", file=sys.stderr)
        selected = {"mode": "full", "reason": reason, "modules": [], "tests": [],
                    "selectedTestClasses": 0, "totalTestClasses": 0} if arguments.format == "json" else ""
    print(json.dumps(selected, sort_keys=True) if arguments.format == "json" else selected)
    return 0


if __name__ == "__main__":
    sys.exit(main())
