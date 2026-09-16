#!/usr/bin/env python3
"""Fail when a top-level Java class in src/main/java is referenced by nothing.

Artemis has no other cross-file dead-code detection. Checkstyle covers Javadoc and braces,
Modernizer covers legacy APIs, and SonarQube Cloud and Codacy only report *file-local* unused
things: a private method, a private field, a local variable. None of them can see that a public
DTO, exception or interface is reachable from nowhere, so dead classes accumulate silently and are
found only when somebody hunts for them by hand in an IDE.

The rule this script enforces is deliberately narrow, because the interesting question -- "can
anything reach this class?" -- has two very different answers in a Spring application:

  * A plain class is reachable only if some other file names it. Java has no way to use a type
    without writing its name, and that includes JPQL constructor expressions inside @Query, class
    literals in @Conditional, and fully-qualified names in spring.factories or Liquibase XML,
    because the simple name is a token inside the qualified name either way. So for a plain class,
    "no file mentions the name" and "dead" are the same statement, and this script fails the build.

  * An annotation-wired class is reachable because a framework scans for its annotation, not
    because anything names it. Zero references is the *normal* state of a live @Configuration.
    Deciding those needs bean-definition provenance from a running context, not a text search, so
    this script never reports them -- see ANNOTATION_WIRED below and
    documentation/docs/developer/guidelines/dead-code.mdx for how to decide one by hand.

Known limitations, all of which err towards silence rather than a false red build:

  * Nested types are not candidates. Only a file's own top-level class is checked, so a dead nested
    record inside a live file is not reported. It is also not independently deletable, so the loss
    is small.
  * A class reachable only from another dead class is reported only after that one is removed.
    Dead code therefore peels off in layers across successive pull requests rather than all at once.
  * A name that collides with an unrelated identifier anywhere in the repository -- a TypeScript
    interface, a YAML key, a word in a Markdown sentence -- counts as a reference and hides a dead
    class. This is the deliberate trade: a missed detection costs nothing, a false positive would
    block an unrelated pull request.
  * Reflection by a name that is assembled at runtime is invisible. No such call exists in Artemis
    today; ALLOWLIST is the escape hatch if one ever does.

Run it directly (`python3 supporting_scripts/check_dead_code.py`) or with `--self-test` to check the
detection logic itself against synthetic sources. CI runs both, in .github/workflows/ci-dead-code.yml.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from collections import defaultdict

# Roots searched for *references*. Anything that can name a Java class belongs here: production Java
# sources, resources (spring.factories, Liquibase changelogs, application*.yml), the Angular client
# (the generated OpenAPI client names server DTOs), the workflows, the docs and the helper scripts.
#
# `src/test` is deliberately absent. A production class that only a test names is not reached from the
# application at all: the test asserts behaviour nothing asks for, and the class and its test are dead
# together. Counting test references hid three such pairs until this root was narrowed. A class the
# framework reaches without being named is a separate question, answered by ANNOTATION_WIRED and
# is_spring_data_fragment below, and never by a test reference.
#
# Naming a class in a comment anywhere under these roots makes it look referenced -- the name-collision
# limitation in the module docstring. Do not name candidate classes here.
REFERENCE_ROOTS = ["src/main", "docker", "supporting_scripts", "documentation", ".github", "gradle"]

# Only classes below this root are *candidates* for removal. Test-only classes are out of scope:
# a test helper that no test uses yet is a different problem with a different fix.
CANDIDATE_ROOT = os.path.join("src", "main", "java")

# Never descend into build output or dependencies. `documentation/build` and `.docusaurus` are
# generated copies of the docs and would make every documented name look referenced.
SKIP_DIRS = {
    "build", "node_modules", ".docusaurus", "dist", ".angular", ".git",
    "target", "out", "coverage", "__pycache__", ".gradle", ".idea",
}

# Binary and generated-asset extensions, skipped for speed. A Java class name never appears in them.
SKIP_SUFFIXES = (
    ".png", ".jpg", ".jpeg", ".gif", ".pdf", ".zip", ".jar", ".war", ".class",
    ".woff", ".woff2", ".ttf", ".eot", ".ico", ".svg", ".mp4", ".webm", ".lockb",
)

# Annotations that make a class reachable without any file naming it, because a framework discovers
# it by scanning. A class carrying one of these is skipped entirely: its reference count says
# nothing about whether it is alive, in either direction.
#
# @Component and friends are found by Spring's classpath scan. @Entity and @Converter are found by
# Hibernate's. @Endpoint / @EndpointWebExtension are found by Actuator. @Aspect is woven by AspectJ.
# @ConfigurationProperties is bound by name from configuration. @JsonComponent is picked up by
# Jackson's Spring integration.
#
# The optional package prefix matches a fully-qualified form such as @org.springframework.stereotype.Service,
# which is a valid annotation Spring scans exactly like the imported one. No class writes it that way today,
# but the miss would turn a live bean into a red build on a required check, so the regex allows for it.
ANNOTATION_WIRED = re.compile(
    r"@(?:[A-Za-z_][A-Za-z0-9_$]*\.)*(RestController|Controller|ControllerAdvice|RestControllerAdvice|Component|Service"
    r"|Configuration|AutoConfiguration|Repository|Entity|Embeddable|MappedSuperclass|Converter"
    r"|Endpoint|EndpointWebExtension|ServletEndpoint|Aspect|SpringBootApplication"
    r"|ConfigurationProperties|JsonComponent|WebFilter|WebServlet|WebListener)\b"
)

# Files that are unreferenced by construction and are not classes anybody could call.
STRUCTURAL_SKIPS = {"package-info.java", "module-info.java"}

# Spring Data resolves a custom repository fragment by name: for a fragment interface `CustomPostRepository`
# that a repository extends, it instantiates `CustomPostRepositoryImpl` purely because of the `Impl` suffix
# (`repositoryImplementationPostfix`, `Impl` by default). Nothing names the implementation and it carries no
# annotation, so it looks exactly like a dead class to a text search while being essential at runtime.
#
# The `Impl` suffix alone is not enough to earn the exemption. Any `FooImpl implements Foo` matches that shape,
# and exempting all of them would hide a dead pair forever: the implementation's own `implements` clause keeps
# the interface referenced, and the exemption would keep the implementation unexamined. So a fragment is
# recognised only when some repository actually composes the interface, which is the thing Spring Data reacts to.
SPRING_DATA_FRAGMENT_SUFFIX = "Impl"

# `interface Name extends A, B {` -- the declaration whose extends list may compose a fragment. Applied to a
# source with its type arguments already removed, so that a generic declaration such as
# `interface ArtemisJpaRepository<T, ID> extends JpaRepository<T, ID>, Fragment` is matched like any other.
INTERFACE_EXTENDS = re.compile(r"\binterface\s+(\w+)\s+extends\s+([^{]+)\{", re.DOTALL)

# One balanced `<...>` group with nothing nested inside it. Applied repeatedly, this removes a whole nest from
# the inside out, which is what makes `Map<String, List<Long>>` disappear in two passes rather than leaving a
# stray `>` behind for the declaration regex to trip over.
TYPE_ARGUMENTS = re.compile(r"<[^<>]*>")


def strip_type_arguments(source: str) -> str:
    """Remove every balanced type argument list, innermost first."""
    previous = None
    while previous != source:
        previous = source
        source = TYPE_ARGUMENTS.sub("", source)
    return source


def _base_names(extends_list: str) -> list[str]:
    """The simple names in an extends list, with type arguments and qualifiers stripped."""
    names = []
    for part in re.sub(r"<[^<>]*(?:<[^<>]*>)?[^<>]*>", "", extends_list).split(","):
        part = part.strip().split(".")[-1]
        if part.isidentifier():
            names.append(part)
    return names


def find_composed_fragment_interfaces(root: str) -> set[str]:
    """The interfaces some Spring Data repository extends, which is what makes an `Impl` of one a fragment."""
    composed: set[str] = set()
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for filename in filenames:
            if not filename.endswith(".java"):
                continue
            try:
                with open(os.path.join(dirpath, filename), encoding="utf-8") as handle:
                    source = handle.read()
            except OSError:
                continue
            for _, extends_list in INTERFACE_EXTENDS.findall(strip_type_arguments(source)):
                names = _base_names(extends_list)
                # A repository is recognised by what it extends, which is always a Spring Data repository interface.
                if any(name.endswith("Repository") for name in names) or "@Repository" in source:
                    composed.update(names)
    return composed


def is_spring_data_fragment(simple_name: str, source: str, composed_interfaces: set[str]) -> bool:
    """True if this is the `Impl` half of a Spring Data custom repository fragment some repository composes."""
    if not simple_name.endswith(SPRING_DATA_FRAGMENT_SUFFIX):
        return False
    fragment_interface = simple_name[: -len(SPRING_DATA_FRAGMENT_SUFFIX)]
    if not fragment_interface or fragment_interface not in composed_interfaces:
        return False
    return re.search(rf"\bimplements\b[^{{]*\b{re.escape(fragment_interface)}\b", source) is not None

# Classes that are genuinely reachable but that no file names, for a reason a text search cannot see.
# Every entry needs the mechanism that reaches it, so the next person can tell a real entry from a
# stale one. Keep this list as short as the facts allow.
ALLOWLIST: dict[str, str] = {
    "src/main/java/de/tum/cit/aet/artemis/core/ApplicationWebXml.java": (
        "WAR deployment entry point. Extends SpringBootServletInitializer, which the servlet "
        "container discovers through SpringServletContainerInitializer's ServletContainerInitializer "
        "SPI, so no file names it. Deleting it breaks `./gradlew -Pprod -Pwar bootWar` deployments "
        "while leaving every test and the dev server green."
    ),
}

IDENTIFIER = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")


def iter_files(roots):
    """Yield every readable, non-binary file below the given roots, skipping build output."""
    for root in roots:
        if not os.path.isdir(root):
            continue
        for dirpath, dirnames, filenames in os.walk(root):
            dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
            for filename in filenames:
                if filename.endswith(SKIP_SUFFIXES):
                    continue
                yield os.path.join(dirpath, filename)


def build_reference_index(roots) -> dict[str, set[str]]:
    """Map every identifier in the tree to the set of files that contain it.

    One pass over the repository beats one grep per candidate: Artemis has ~3200 candidate classes
    and ~12600 files, so the per-candidate approach costs 3200 full scans and this costs one.
    """
    index: dict[str, set[str]] = defaultdict(set)
    for path in iter_files(roots):
        try:
            with open(path, encoding="utf-8", errors="ignore") as handle:
                text = handle.read()
        except OSError:
            continue
        for token in set(IDENTIFIER.findall(text)):
            index[token].add(path)
    return index


def find_candidates(root: str, composed_interfaces: set[str] | None = None):
    """Yield (simple_name, path) for every top-level class file eligible for the check.

    The whole file is read rather than a prefix of it. The annotation sits after the imports and the
    class Javadoc, and 36 classes in Artemis -- WebsocketConfiguration and ExamResource among them --
    carry theirs past the 4,000th character. Reading a prefix made those look like plain classes, so
    whether a live bean could fail this check depended on how long its import block happened to be.
    """
    if composed_interfaces is None:
        composed_interfaces = find_composed_fragment_interfaces(root)
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for filename in filenames:
            if not filename.endswith(".java") or filename in STRUCTURAL_SKIPS:
                continue
            path = os.path.join(dirpath, filename)
            try:
                with open(path, encoding="utf-8") as handle:
                    source = handle.read()
            except OSError:
                continue
            if ANNOTATION_WIRED.search(source):
                continue
            simple_name = filename[:-len(".java")]
            if is_spring_data_fragment(simple_name, source, composed_interfaces):
                continue
            yield simple_name, path


def find_dead_classes(candidate_root=CANDIDATE_ROOT, reference_roots=REFERENCE_ROOTS):
    """Return the sorted paths of candidate classes that no other file mentions."""
    index = build_reference_index(reference_roots)
    dead = []
    for simple_name, path in find_candidates(candidate_root):
        if path.replace(os.sep, "/") in ALLOWLIST:
            continue
        if not index.get(simple_name, set()) - {path}:
            dead.append(path)
    return sorted(dead)


def report(dead: list[str]) -> int:
    if not dead:
        print(f"No dead classes. Checked every top-level class below {CANDIDATE_ROOT}.")
        return 0
    print(f"{len(dead)} class(es) below {CANDIDATE_ROOT} are referenced by no other file:\n")
    for path in dead:
        print(f"  {path}")
    print(
        "\nEach one is unreachable: nothing names it, and it carries no annotation that would let a\n"
        "framework discover it. Delete it.\n"
        "\n"
        "If one of them IS reachable, the mechanism is something this check cannot see. Add it to\n"
        "ALLOWLIST in this script together with that mechanism -- not just the fact that it is used.\n"
        "See documentation/docs/developer/guidelines/dead-code.mdx."
    )
    return 1


def self_test() -> int:
    """Check the detection logic against synthetic sources in a temporary tree."""
    import tempfile

    failures = []

    def check(name, condition):
        if condition:
            print(f"  ok   {name}")
        else:
            print(f"  FAIL {name}")
            failures.append(name)

    with tempfile.TemporaryDirectory() as tmp:
        java = os.path.join(tmp, "src", "main", "java", "pkg")
        res = os.path.join(tmp, "src", "main", "resources")
        test = os.path.join(tmp, "src", "test", "java", "pkg")
        os.makedirs(java)
        os.makedirs(res)
        os.makedirs(test)

        def write(directory, filename, text):
            with open(os.path.join(directory, filename), "w", encoding="utf-8") as handle:
                handle.write(text)

        write(java, "DeadDto.java", "package pkg;\npublic record DeadDto(long id) {}\n")
        write(java, "UsedDto.java", "package pkg;\npublic record UsedDto(long id) {}\n")
        write(java, "Caller.java", "package pkg;\nclass Caller { UsedDto d; }\n")
        write(java, "DeadService.java", "package pkg;\n@Service\npublic class DeadService {}\n")
        write(java, "QueryDto.java", "package pkg;\npublic record QueryDto(long n) {}\n")
        write(java, "Repo.java",
              'package pkg;\ninterface Repo { @Query("SELECT new pkg.QueryDto(c.id) FROM C c") void f(); }\n')
        write(java, "FactoriesOnly.java", "package pkg;\npublic class FactoriesOnly {}\n")
        write(res, "spring.factories", "some.Key=pkg.FactoriesOnly\n")
        write(java, "package-info.java", "package pkg;\n")
        write(java, "Allowlisted.java", "package pkg;\npublic class Allowlisted {}\n")
        write(java, "QualifiedService.java",
              "package pkg;\n@org.springframework.stereotype.Service\npublic class QualifiedService {}\n")
        write(java, "TestOnlyDto.java", "package pkg;\npublic record TestOnlyDto(long id) {}\n")
        write(test, "TestOnlyDtoTest.java", "package pkg;\nclass TestOnlyDtoTest { TestOnlyDto d; }\n")
        write(java, "FragmentRepository.java", "package pkg;\npublic interface FragmentRepository {}\n")
        write(java, "FragmentRepositoryImpl.java",
              "package pkg;\npublic class FragmentRepositoryImpl implements FragmentRepository {}\n")
        write(java, "PostRepository.java",
              "package pkg;\npublic interface PostRepository extends JpaRepository<Post, Long>, FragmentRepository {}\n")
        write(java, "Standalone.java", "package pkg;\npublic interface Standalone {}\n")
        write(java, "StandaloneImpl.java", "package pkg;\npublic class StandaloneImpl implements Standalone {}\n")
        write(java, "GenericFragment.java", "package pkg;\npublic interface GenericFragment {}\n")
        write(java, "GenericFragmentImpl.java",
              "package pkg;\npublic class GenericFragmentImpl implements GenericFragment {}\n")
        write(java, "GenericBaseRepository.java",
              "package pkg;\npublic interface GenericBaseRepository<T, ID> extends JpaRepository<T, ID>, GenericFragment {}\n")
        write(java, "LateAnnotation.java",
              "package pkg;\n" + "// filler to push the annotation past the old 4000-character cutoff\n" * 80
              + "@Component\npublic class LateAnnotation {}\n")

        allowlisted_path = os.path.join(java, "Allowlisted.java")
        ALLOWLIST[allowlisted_path.replace(os.sep, "/")] = "self-test entry"
        try:
            dead = set(find_dead_classes(os.path.join(tmp, "src", "main", "java"), [os.path.join(tmp, "src", "main")]))
        finally:
            del ALLOWLIST[allowlisted_path.replace(os.sep, "/")]

        check("an unreferenced plain class is reported", os.path.join(java, "DeadDto.java") in dead)
        check("a referenced class is not reported", os.path.join(java, "UsedDto.java") not in dead)
        check("the referencing class itself is not reported", os.path.join(java, "Caller.java") in dead)
        check("an annotation-wired class is never reported", os.path.join(java, "DeadService.java") not in dead)
        check("a JPQL constructor expression counts as a reference", os.path.join(java, "QueryDto.java") not in dead)
        check("a spring.factories entry counts as a reference", os.path.join(java, "FactoriesOnly.java") not in dead)
        check("package-info.java is never reported", os.path.join(java, "package-info.java") not in dead)
        check("an allowlisted class is not reported", allowlisted_path not in dead)
        check("a fully qualified annotation is never reported", os.path.join(java, "QualifiedService.java") not in dead)
        check("an annotation past the 4000th character is never reported", os.path.join(java, "LateAnnotation.java") not in dead)
        check("a class only a test names is reported", os.path.join(java, "TestOnlyDto.java") in dead)
        check("a Spring Data fragment implementation is not reported", os.path.join(java, "FragmentRepositoryImpl.java") not in dead)
        check("an Impl of an interface no repository composes is still reported", os.path.join(java, "StandaloneImpl.java") in dead)
        check("a fragment composed by a generic repository is never reported", os.path.join(java, "GenericFragmentImpl.java") not in dead)

    # `Caller` is itself unreferenced, which is why it is expected in `dead` above; that is the
    # documented layering behaviour, not a bug.
    print()
    if failures:
        print(f"{len(failures)} self-test(s) failed.")
        return 1
    print("All self-tests passed.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--self-test", action="store_true",
                        help="check the detection logic against synthetic sources and exit")
    args = parser.parse_args()

    if args.self_test:
        return self_test()
    return report(find_dead_classes())


if __name__ == "__main__":
    sys.exit(main())
