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

# Roots searched for *references*. Anything that can name a Java class belongs here: Java sources and
# tests, resources (spring.factories, Liquibase changelogs, application*.yml), the Angular client (the
# generated OpenAPI client names server DTOs), the workflows, the docs and the helper scripts.
REFERENCE_ROOTS = ["src", "docker", "supporting_scripts", "documentation", ".github", "gradle"]

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
ANNOTATION_WIRED = re.compile(
    r"@(RestController|Controller|ControllerAdvice|RestControllerAdvice|Component|Service"
    r"|Configuration|AutoConfiguration|Repository|Entity|Embeddable|MappedSuperclass|Converter"
    r"|Endpoint|EndpointWebExtension|ServletEndpoint|Aspect|SpringBootApplication"
    r"|ConfigurationProperties|JsonComponent|WebFilter|WebServlet|WebListener)\b"
)

# Files that are unreferenced by construction and are not classes anybody could call.
STRUCTURAL_SKIPS = {"package-info.java", "module-info.java"}

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


def find_candidates(root: str):
    """Yield (simple_name, path) for every top-level class file eligible for the check."""
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for filename in filenames:
            if not filename.endswith(".java") or filename in STRUCTURAL_SKIPS:
                continue
            path = os.path.join(dirpath, filename)
            try:
                with open(path, encoding="utf-8") as handle:
                    head = handle.read(4000)
            except OSError:
                continue
            if ANNOTATION_WIRED.search(head):
                continue
            yield filename[:-len(".java")], path


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
        os.makedirs(java)
        os.makedirs(res)

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

        allowlisted_path = os.path.join(java, "Allowlisted.java")
        ALLOWLIST[allowlisted_path.replace(os.sep, "/")] = "self-test entry"
        try:
            dead = set(find_dead_classes(os.path.join(tmp, "src", "main", "java"), [tmp]))
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
