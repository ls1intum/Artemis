#!/usr/bin/env python3
"""Check the release executables, including omissions the Java compiler cannot detect."""

import argparse
import json
from pathlib import Path
from zipfile import ZipFile


MIB = 1024 * 1024
CLASSES = "WEB-INF/classes/"
ARTEMIS = CLASSES + "de/tum/cit/aet/artemis/"
LIB = "WEB-INF/lib/"
WORKER_EXCLUSIONS = (
    CLASSES + "static/",
    CLASSES + "config/liquibase/",
    ARTEMIS + "admin/web/",
    LIB + "hibernate-core-",
    LIB + "liquibase-core-",
    LIB + "mysql-connector-j-",
    LIB + "postgresql-",
    LIB + "jplag-",
)


def check_archive(path, required, forbidden, limit, sbom, has_client):
    size = path.stat().st_size
    with ZipFile(path) as archive:
        entries = {entry.filename for entry in archive.infolist() if not entry.is_dir()}
        if sbom is not None:
            components = ["server", "client"] if has_client else ["server"]
            expected = {CLASSES + f"sbom/{name}-sbom.json" for name in components} if sbom else set()
            actual = {entry for entry in entries if entry.startswith(CLASSES + "sbom/")}
            if expected != actual:
                raise ValueError(f"{path.name}: expected SBOMs {sorted(expected)}, found {sorted(actual)}")
            for entry in actual:
                if not json.loads(archive.read(entry)).get("components"):
                    raise ValueError(f"{path.name}: {entry} has no dependency inventory")
        missing = (set(required) | {"META-INF/NOTICE"}) - entries
        unexpected = [name for name in sorted(entries) if name.startswith(forbidden)]
        if missing or unexpected or size > limit:
            raise ValueError(
                f"{path.name}: {size:,} bytes (limit {limit:,}), missing {sorted(missing)}, "
                f"unexpected {unexpected[:10]}"
            )
        libraries = sorted(
            ((entry.compress_size, Path(entry.filename).name) for entry in archive.infolist() if entry.filename.startswith(LIB) and entry.filename.endswith(".jar")),
            reverse=True,
        )
        resources = sum(entry.compress_size for entry in archive.infolist() if entry.filename.startswith(CLASSES) and not entry.filename.endswith(".class"))
        client = sum(entry.compress_size for entry in archive.infolist() if entry.filename.startswith(CLASSES + "static/"))
    print(f"{path.name}: {size:,} bytes ({size / MIB:.1f} MiB), {len(libraries)} libraries, {resources / MIB:.1f} MiB resources including {client / MIB:.1f} MiB client")
    print("  Largest libraries: " + ", ".join(f"{label} ({count / MIB:.1f} MiB)" for count, label in libraries[:5]))
    return size


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("universal", type=Path)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--sbom", action=argparse.BooleanOptionalAction, help="require or exclude embedded dependency inventories")
    args = parser.parse_args()
    client = CLASSES + "static/index.html"
    agent = ARTEMIS + "buildagent/service/BuildJobExecutionService.class"
    ai = ARTEMIS + "hyperion/service/worker/HyperionWorkloadService.class"
    universal_size = check_archive(args.universal, [client, agent, ai], (), args.universal.stat().st_size, args.sbom, True)

    # Independent upper bounds leave room for release SBOMs and regular dependency updates.
    # A forbidden library fails the check even if an unrelated dependency shrinks.
    roles = [
        ("core", 415, [ARTEMIS + "CoreApp.class", client, CLASSES + "prompts/hyperion/variants/plan_quiz.st"],
         (ARTEMIS + "buildagent/service/", ARTEMIS + "localci/service/scaparser/ReportParser", ARTEMIS + "hyperion/service/worker/", LIB + "kubernetes-", LIB + "docker-java-core-", LIB + "docker-java-transport")),
        ("buildagent", 130, [agent, ARTEMIS + "buildagent/BuildAgentApplication.class"],
         WORKER_EXCLUSIONS + (ARTEMIS + "hyperion/", LIB + "openai-java-", LIB + "plantuml-")),
        ("aiworker", 230, [ai, ARTEMIS + "aiworker/AiWorkerApplication.class"],
         WORKER_EXCLUSIONS + (ARTEMIS + "buildagent/service/", LIB + "kubernetes-", LIB + "tomcat-embed-core-")),
    ]
    for role, budget, required, forbidden in roles:
        size = check_archive(args.directory / f"Artemis-{role}.war", required, forbidden, min(universal_size - 1, budget * MIB), args.sbom, role == "core")
        print(f"  Saves {(universal_size - size) / MIB:.1f} MiB ({1 - size / universal_size:.1%}) against this universal WAR")


if __name__ == "__main__":
    main()
