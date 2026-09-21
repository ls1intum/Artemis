#!/usr/bin/env python3
"""
Verifies that the Liquibase changelogs in this repository produce the schema they claim to.

Six checks, each run against PostgreSQL and MySQL:

  layout            Structural rules the others depend on: every folded changelog pins its
                    logicalFilePath, and no folded changelog is gated by a context.
  replay            An empty database accepts the whole folded history in order.
  converge          The baseline produces the same schema as replaying the history it folded.
  fresh-vs-upgrade  A fresh installation and one that replayed the whole folded history end up with
                    the same schema and the same recorded changesets.
  upgrade-from-floor  An installation at the oldest release this one can upgrade from ends up matching
                    a fresh installation.
  seed              The seed data actually lands on a fresh installation.

Run everything, which is what CI does:

    python3 supporting_scripts/liquibase/verify_schema.py

Run one check against one database while working on a baseline:

    python3 supporting_scripts/liquibase/verify_schema.py --check converge --database postgres

Needs Docker, and the JDBC drivers exported by ``./gradlew exportLiquibaseDrivers`` (this script runs
that for you if they are missing).
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import uuid
import xml.etree.ElementTree as ElementTree
from dataclasses import dataclass
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import db_schema  # noqa: E402

REPO = Path(__file__).resolve().parents[2]
LIQUIBASE_DIR = REPO / "src/main/resources/config/liquibase"
DRIVER_DIR = REPO / "build/liquibase-drivers"

MASTER = "config/liquibase/master.xml"
HISTORY_MASTER = "config/liquibase/history/master.xml"

# The contexts an end-to-end stack starts with; see docker/artemis/config/playwright.env.
SEED_CONTEXTS = "prod,e2e"

# Changesets an upgraded database has recorded that a fresh one never will, because the changelog that
# created them is no longer in the repository. Liquibase does not mind a recorded row whose file is
# gone, so these stay behind harmlessly -- but they are the one legitimate difference between the two
# routes, and naming them individually is what keeps upgrade-from-floor able to report every other one.
# Matched on the whole identity Liquibase records, not on the id alone: an id matches the row it names
# and also any other row that happens to share it, which would wave through a changeset recorded under
# an author or a path nobody expected.
ORPHANED_ON_UPGRADE = {
    # The cleanup changeset of the old consolidation scheme, deleted by this layout. Every database that
    # has been through 9.0 recorded it; no fresh installation from the v10 baseline ever will.
    ("20260406120000", "krusche", "config/liquibase/changelog/20240331151800_cleanup.xml"),
}

NAMESPACE = "{http://www.liquibase.org/xml/ns/dbchangelog}"
CHANGESET_TAG = f"{NAMESPACE}changeSet"


def log(message: str) -> None:
    print(message, flush=True)


def run(argv: list[str], **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(argv, capture_output=True, text=True, check=False, **kwargs)


def gradle_property(name: str) -> str:
    for line in (REPO / "gradle.properties").read_text().splitlines():
        if line.startswith(f"{name}="):
            return line.split("=", 1)[1].strip()
    raise RuntimeError(f"{name} is not set in gradle.properties")


# --- Database engines ------------------------------------------------------------------------------


@dataclass
class Engine:
    name: str
    image: str
    port: int
    ready_command: list[str]

    def jdbc_url(self, port: int) -> str:
        if self.name == "postgres":
            return f"jdbc:postgresql://host.docker.internal:{port}/Artemis"
        return f"jdbc:mysql://host.docker.internal:{port}/Artemis?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC"

    @property
    def user(self) -> str:
        return "artemis" if self.name == "postgres" else "root"

    @property
    def password(self) -> str:
        return "artemis" if self.name == "postgres" else ""

    @property
    def driver_prefix(self) -> str:
        return "postgresql-" if self.name == "postgres" else "mysql-connector-j-"

    def docker_args(self) -> list[str]:
        if self.name == "postgres":
            return ["-e", "POSTGRES_USER=artemis", "-e", "POSTGRES_PASSWORD=artemis", "-e", "POSTGRES_DB=Artemis"]
        return ["-e", "MYSQL_ALLOW_EMPTY_PASSWORD=yes", "-e", "MYSQL_DATABASE=Artemis"]

    def server_args(self) -> list[str]:
        if self.name == "postgres":
            return []
        return ["--lower_case_table_names=1", "--character_set_server=utf8mb4", "--collation-server=utf8mb4_unicode_ci", "--explicit_defaults_for_timestamp"]

    def read_schema(self, container: str) -> db_schema.SchemaModel:
        if self.name == "postgres":
            return db_schema.read_postgres(container, "Artemis", "artemis")
        return db_schema.read_mysql(container, "Artemis", "root")

    def read_changelog_rows(self, container: str) -> set[tuple[str, str, str]]:
        """Returns the (id, author, filename) of every recorded changeset."""
        query = "SELECT ID, AUTHOR, FILENAME FROM DATABASECHANGELOG;"
        if self.name == "postgres":
            argv = ["psql", "-U", "artemis", "-d", "Artemis", "-t", "-A", "-F", "\t", "-c", query]
        else:
            argv = ["mysql", "--default-character-set=utf8mb4", "-u", "root", "-N", "-B", "--database", "Artemis", "-e", query]
        result = run(["docker", "exec", container, *argv])
        if result.returncode != 0:
            raise RuntimeError(f"could not read DATABASECHANGELOG: {result.stderr.strip()}")
        rows = set()
        for line in result.stdout.splitlines():
            parts = line.split("\t")
            if len(parts) == 3:
                rows.add(tuple(part.strip() for part in parts))
        return rows

    def read_row_count(self, container: str, table: str) -> int:
        query = f"SELECT COUNT(*) FROM {table};"
        if self.name == "postgres":
            argv = ["psql", "-U", "artemis", "-d", "Artemis", "-t", "-A", "-c", query]
        else:
            argv = ["mysql", "--default-character-set=utf8mb4", "-u", "root", "-N", "-B", "--database", "Artemis", "-e", query]
        result = run(["docker", "exec", container, *argv])
        if result.returncode != 0:
            raise RuntimeError(f"could not count rows in {table}: {result.stderr.strip()}")
        return int(result.stdout.strip() or 0)


# Both readiness probes deliberately go over TCP. Each image runs a temporary server to initialise the
# data directory and that one listens on a unix socket only, so a socket probe reports ready while the
# real server is still coming up, and the connection dies mid-migration a few seconds later.
ENGINES = {
    "postgres": Engine("postgres", "postgres:18", 5432, ["pg_isready", "-h", "127.0.0.1", "-U", "artemis", "-q"]),
    "mysql": Engine("mysql", "mysql:9", 3306, ["mysql", "--protocol=TCP", "-h", "127.0.0.1", "-u", "root", "-e", "SELECT 1"]),
}


class Database:
    """One throwaway database container, started for a single check and removed afterwards."""

    def __init__(self, engine: Engine, label: str, port: int):
        self.engine = engine
        self.label = label
        self.port = port
        self.container = f"lbverify-{engine.name}-{label}-{uuid.uuid4().hex[:8]}"

    def __enter__(self) -> "Database":
        argv = ["docker", "run", "-d", "--name", self.container, *self.engine.docker_args(), "-p", f"{self.port}:{self.engine.port}", self.engine.image]
        argv += self.engine.server_args()
        result = run(argv)
        if result.returncode != 0:
            raise RuntimeError(f"could not start {self.container}: {result.stderr.strip()}")
        try:
            self._await_ready()
        except BaseException:
            # __exit__ only runs once __enter__ has returned, so a container that never becomes ready
            # would be left running and holding its published port, and the next run would fail to bind.
            self.remove()
            raise
        return self

    def __exit__(self, *exc) -> None:
        self.remove()

    def remove(self) -> None:
        run(["docker", "rm", "-f", self.container])

    def _await_ready(self, timeout_seconds: int = 180) -> None:
        deadline = time.monotonic() + timeout_seconds
        while time.monotonic() < deadline:
            if run(["docker", "exec", self.container, *self.engine.ready_command]).returncode == 0:
                return
            time.sleep(1)
        raise RuntimeError(f"{self.container} did not become ready within {timeout_seconds}s")

    @property
    def url(self) -> str:
        return self.engine.jdbc_url(self.port)

    def schema(self) -> db_schema.SchemaModel:
        return self.engine.read_schema(self.container)

    def changelog_rows(self) -> set[tuple[str, str, str]]:
        return self.engine.read_changelog_rows(self.container)

    def row_count(self, table: str) -> int:
        return self.engine.read_row_count(self.container, table)


# --- Liquibase -------------------------------------------------------------------------------------


class Liquibase:
    """
    Runs the Liquibase CLI from its Docker image, pinned to the version the application uses.

    The changelogs are handed over as a copy rather than the repository itself, because the CLI cannot
    resolve the ``classpath:`` prefix that ``master.xml`` uses for Spring. Rewriting it in the copy is
    the one difference between what runs here and what the application runs; every file it points at is
    byte-for-byte the shipped one, and every changeset pins its own logicalFilePath, so the identities
    Liquibase records are the same either way.
    """

    def __init__(self, workspace: Path):
        self.workspace = workspace
        self.version = gradle_property("liquibase_version")

    def command(self, database: Database, changelog: str, command: str, contexts: str = "prod") -> subprocess.CompletedProcess:
        driver = self._driver_for(database.engine)
        argv = [
            "docker", "run", "--rm",
            # Docker Desktop resolves host.docker.internal on its own; Linux, which is what CI runs,
            # does not, and without this the Liquibase container cannot reach the published database port.
            "--add-host", "host.docker.internal:host-gateway",
            "-v", f"{self.workspace}:/lb",
            "-v", f"{driver}:/liquibase/lib/driver.jar",
            f"liquibase/liquibase:{self.version}",
            "--search-path=/lb",
            f"--changeLogFile={changelog}",
            f"--url={database.url}",
            f"--username={database.engine.user}",
            f"--password={database.engine.password}",
            f"--contexts={contexts}",
            command,
        ]
        return run(argv)

    def apply(self, database: Database, changelog: str, command: str = "update", contexts: str = "prod") -> None:
        result = self.command(database, changelog, command, contexts)
        if result.returncode != 0:
            raise RuntimeError(f"liquibase {command} of {changelog} against {database.label} failed:\n{result.stdout}\n{result.stderr}")

    def _driver_for(self, engine: Engine) -> Path:
        candidates = sorted(DRIVER_DIR.glob(f"{engine.driver_prefix}*.jar"))
        if not candidates:
            raise RuntimeError(f"no {engine.driver_prefix}*.jar in {DRIVER_DIR}; run ./gradlew exportLiquibaseDrivers")
        # The build pins exactly one version of each driver. More than one means a stale export, and
        # picking between them by name would sort 9.9.0 above 9.10.0, so say so rather than guess.
        if len(candidates) > 1:
            raise RuntimeError(f"several {engine.driver_prefix} jars in {DRIVER_DIR}; delete it and run ./gradlew exportLiquibaseDrivers again")
        return candidates[0]


def build_workspace(destination: Path) -> Path:
    """Copies the Liquibase resources into a scratch directory the CLI can read, and adds the changelogs the checks need."""
    workspace = destination / "lb"
    shutil.copytree(REPO / "src/main/resources/config", workspace / "config")

    master = workspace / "config/liquibase/master.xml"
    master.write_text(master.read_text().replace("classpath:", ""))

    # What master.xml looked like before the current baseline was cut: the same manifest without it. This
    # is the changelog an installation of the previous generation actually ran, and the starting point
    # the upgrade check needs.
    without_baseline = [line for line in master.read_text().splitlines() if "/baseline/" not in line]
    (workspace / "config/liquibase/master-without-baseline.xml").write_text("\n".join(without_baseline) + "\n")

    for path in (workspace / "config/liquibase").rglob("*.xml"):
        path.write_text(path.read_text().replace("classpath:", ""))

    return workspace


def current_baseline() -> str:
    """Returns the changelog path of the newest baseline, or an empty string when none has been cut."""
    baselines = sorted((LIQUIBASE_DIR / "baseline").glob("*_initial_schema.xml")) if (LIQUIBASE_DIR / "baseline").is_dir() else []
    return f"config/liquibase/baseline/{baselines[-1].name}" if baselines else ""


# --- Checks ----------------------------------------------------------------------------------------


def baseline_guard_problems(changeset, relative: Path) -> list[str]:
    """
    Checks the precondition that stops a baseline changeset running on a database that already has it.

    Being lenient here is worse than having no rule: a guard that reads plausibly but tests the wrong
    thing -- an existence check next to the <not> rather than inside it, or one naming an object this
    changeset does not create -- lets the changeset execute on an upgrade and fail on a duplicate. So
    the shape is pinned exactly, and the object named has to be the first one the changeset creates.
    """
    identifier = changeset.get("id", "?")
    expected = (
        f'{relative}: changeset {identifier} needs <preConditions onFail="MARK_RAN"> whose <not> contains exactly one '
        f"tableExists or columnExists, naming an object the changeset itself creates"
    )

    preconditions = changeset.find(f"{NAMESPACE}preConditions")
    if preconditions is None or preconditions.get("onFail") != "MARK_RAN":
        return [expected]

    negations = [child for child in preconditions if child.tag == f"{NAMESPACE}not"]
    if len(negations) != 1 or len(negations[0]) != 1:
        return [expected]

    guard = negations[0][0]
    if guard.tag not in (f"{NAMESPACE}tableExists", f"{NAMESPACE}columnExists"):
        return [expected]

    guarded = (guard.get("tableName"), guard.get("columnName"))
    created = created_objects(changeset)
    if not created:
        return [f"{relative}: changeset {identifier} creates nothing, so nothing can guard it"]
    if guarded not in created:
        return [
            f"{relative}: changeset {identifier} guards on {guarded[0]}{'.' + guarded[1] if guarded[1] else ''}, which it does not "
            f"create, so the guard says nothing about whether this changeset has already been applied"
        ]
    return []


def created_objects(changeset) -> set[tuple[str, str | None]]:
    """
    Every table and column this changeset brings into existence.

    A guard has to name one of these. That it names *which* one does not matter: the changeset creates
    them together, so either they are all present on an upgrade or none of them is. What the guard
    cannot do is name something the changeset does not create, because then it reports on some unrelated
    part of the schema. Whether the named object is old enough to exist at the oldest supported release
    is a different question, and the one upgrade-from-floor answers by actually performing that upgrade.
    """
    created: set[tuple[str, str | None]] = set()
    for element in changeset:
        if element.tag == f"{NAMESPACE}createTable":
            created.add((element.get("tableName"), None))
            for column in element:
                if column.tag == f"{NAMESPACE}column":
                    created.add((element.get("tableName"), column.get("name")))
        if element.tag == f"{NAMESPACE}addColumn":
            for column in element:
                if column.tag == f"{NAMESPACE}column":
                    created.add((element.get("tableName"), column.get("name")))
    return created


def check_layout() -> list[str]:
    """The structural rules the database checks assume. Needs no database, so it runs first and once."""
    failures: list[str] = []
    history = LIQUIBASE_DIR / "history"
    if not history.is_dir():
        return [f"{history} does not exist"]

    for path in sorted(history.rglob("*.xml")):
        relative = path.relative_to(REPO)
        root = ElementTree.parse(path).getroot()

        if path.name != "master.xml" and not root.get("logicalFilePath"):
            failures.append(f"{relative}: folded changelogs must pin logicalFilePath, so that moving the file does not re-run it")

        for changeset in root.iter(CHANGESET_TAG):
            if changeset.get("context") or changeset.get("contexts"):
                identifier = changeset.get("id", "?")
                failures.append(
                    f"{relative}: changeset {identifier} is gated by a context. A folded changeset is recorded, not executed, "
                    f"on a fresh installation, so its rows would be missing. Move it to config/liquibase/data."
                )

    baseline_dir = LIQUIBASE_DIR / "baseline"
    for path in sorted(baseline_dir.glob("*.xml")) if baseline_dir.is_dir() else []:
        root = ElementTree.parse(path).getroot()
        if not root.get("logicalFilePath"):
            failures.append(f"{path.relative_to(REPO)}: a baseline must pin logicalFilePath")
        for changeset in root.iter(CHANGESET_TAG):
            failures += baseline_guard_problems(changeset, path.relative_to(REPO))

    return failures


def check_replay(engine: Engine, liquibase: Liquibase, port: int) -> list[str]:
    """
    An empty database must accept the whole folded history, in order, on this engine.

    The assertion is that Liquibase exits cleanly, which ``apply`` turns into a failure; the table count
    below only rules out the degenerate case where every changeset was filtered out and the run
    succeeded by doing nothing at all.
    """
    with Database(engine, "replay", port) as database:
        liquibase.apply(database, "config/liquibase/master-without-baseline.xml")
        if not database.schema().tables:
            return ["replaying the history succeeded but produced no tables, so every changeset must have been filtered out"]
    return []


def check_converge(engine: Engine, liquibase: Liquibase, port: int) -> list[str]:
    """The baseline and the history it folded must describe the same schema."""
    baseline = current_baseline()
    if not baseline:
        log("    no baseline cut yet, nothing to converge against")
        return []

    with Database(engine, "baseline", port) as from_baseline, Database(engine, "history", port + 1) as from_history:
        liquibase.apply(from_baseline, baseline)
        liquibase.apply(from_history, HISTORY_MASTER)
        return db_schema.diff(from_baseline.schema(), from_history.schema(), "baseline", "history")


def check_fresh_vs_upgrade(engine: Engine, liquibase: Liquibase, port: int) -> list[str]:
    """
    A fresh installation and an upgraded one must end up identical.

    Fresh records the folded history without executing it and then applies the master changelog, which
    is what the application does on an empty database. Upgrade replays the history the way an
    installation of the previous generation already did, and then applies the same master changelog.
    """
    with Database(engine, "fresh", port) as fresh, Database(engine, "upgrade", port + 1) as upgraded:
        liquibase.apply(fresh, HISTORY_MASTER, command="changelog-sync")
        liquibase.apply(fresh, MASTER)

        liquibase.apply(upgraded, "config/liquibase/master-without-baseline.xml")
        liquibase.apply(upgraded, MASTER)

        failures = db_schema.diff(fresh.schema(), upgraded.schema(), "fresh", "upgraded")

        only_fresh = fresh.changelog_rows() - upgraded.changelog_rows()
        only_upgraded = upgraded.changelog_rows() - fresh.changelog_rows()
        for identifier, author, filename in sorted(only_fresh):
            failures.append(f"changeset {identifier} ({author}, {filename}) is recorded only on the fresh installation")
        for identifier, author, filename in sorted(only_upgraded):
            failures.append(f"changeset {identifier} ({author}, {filename}) is recorded only on the upgraded installation")

    return failures


def migration_floor() -> str:
    """
    The oldest release this one can be upgraded from, read from DatabaseMigration.java.

    Taking it from the source rather than repeating it here is what keeps the check and the gate that
    enforces it from drifting apart: raise the floor in the Java and this check follows.
    """
    source = (REPO / "src/main/java/de/tum/cit/aet/artemis/core/config/migration/DatabaseMigration.java").read_text()
    versions = re.findall(r'new MigrationPath\("([0-9][0-9.]*)"\)', source)
    if not versions:
        raise RuntimeError("no MigrationPath found in DatabaseMigration.java")
    return versions[-1]


def floor_workspace(destination: Path, version: str) -> Path:
    """Extracts the Liquibase resources as they were at the floor release, from its git tag."""
    if run(["git", "rev-parse", "--verify", f"{version}^{{commit}}"], cwd=REPO).returncode != 0:
        fetched = run(["git", "fetch", "--depth=1", "origin", f"refs/tags/{version}:refs/tags/{version}"], cwd=REPO)
        if fetched.returncode != 0:
            raise RuntimeError(f"tag {version} is not available and could not be fetched: {fetched.stderr.strip()}")

    workspace = destination / "floor"
    workspace.mkdir()
    # Binary, not text: this is a tar stream, and decoding it as UTF-8 corrupts it.
    archive = subprocess.run(["git", "archive", version, "src/main/resources/config"], cwd=REPO, capture_output=True, check=False)
    if archive.returncode != 0:
        raise RuntimeError(f"could not read the Liquibase resources from {version}: {archive.stderr.decode(errors='replace').strip()}")
    extracted = subprocess.run(["tar", "-x", "-C", str(workspace)], input=archive.stdout, capture_output=True, check=False)
    if extracted.returncode != 0:
        raise RuntimeError(f"could not extract the Liquibase resources from {version}: {extracted.stderr.decode(errors='replace').strip()}")

    # git archive keeps the full path; the CLI search path expects config/ at the root.
    (workspace / "src/main/resources/config").rename(workspace / "config")
    for path in (workspace / "config/liquibase").rglob("*.xml"):
        path.write_text(path.read_text().replace("classpath:", ""))
    return workspace


def check_upgrade_from_floor(engine: Engine, liquibase: Liquibase, port: int) -> list[str]:
    """
    An installation at the oldest supported release must upgrade to this one and match a fresh install.

    This is the upgrade the release actually promises, and it is not the same thing as replaying the
    folded history: the floor release ran only the changelogs that existed then, so a database there is
    missing every changelog written since. The baseline's preconditions have to mark themselves as ran
    against *that* schema, which is a weaker premise than the develop-tip one fresh-vs-upgrade uses.
    """
    version = migration_floor()
    with tempfile.TemporaryDirectory(prefix="liquibase-floor-") as scratch:
        try:
            floor = floor_workspace(Path(scratch), version)
        except RuntimeError as error:
            return [str(error)]
        at_floor = Liquibase(floor)

        with Database(engine, "floor", port) as upgraded, Database(engine, "fresh", port + 1) as fresh:
            at_floor.apply(upgraded, MASTER)
            liquibase.apply(upgraded, MASTER)

            liquibase.apply(fresh, HISTORY_MASTER, command="changelog-sync")
            liquibase.apply(fresh, MASTER)

            failures = db_schema.diff(fresh.schema(), upgraded.schema(), "fresh", f"upgraded from {version}")

            # The schemas matching is not enough. A changelog whose logicalFilePath, id or author is
            # wrong is not recognised as already applied, so the upgrade runs it again; where its
            # preconditions make that harmless the schema still comes out right and only the recorded
            # identity gives it away. Comparing what each side recorded is what notices.
            for identifier, author, filename in sorted(fresh.changelog_rows() - upgraded.changelog_rows()):
                failures.append(f"changeset {identifier} ({author}, {filename}) is recorded only on the fresh installation")
            for row in sorted(upgraded.changelog_rows() - fresh.changelog_rows()):
                if row in ORPHANED_ON_UPGRADE:
                    continue
                identifier, author, filename = row
                failures.append(f"changeset {identifier} ({author}, {filename}) is recorded only after upgrading from {version}")

            return failures


def seed_expectations() -> list[tuple[str, int]]:
    """
    Returns (table, row count) for every CSV the seed changelogs load.

    Parsed, not matched with a regular expression: a pattern anchored on attribute order finds nothing
    the day someone writes tableName before file, and an expectation that quietly disappears turns this
    check into one that passes while the seed is missing, which is the failure it exists to catch.
    """
    expectations = []
    for changelog in sorted((LIQUIBASE_DIR / "data").glob("*.xml")):
        for element in ElementTree.parse(changelog).getroot().iter(f"{NAMESPACE}loadData"):
            csv_file = REPO / "src/main/resources" / element.get("file")
            data_lines = [line for line in csv_file.read_text().splitlines()[1:] if line.strip()]
            expectations.append((element.get("tableName"), len(data_lines)))
    return expectations


def check_seed(engine: Engine, liquibase: Liquibase, port: int) -> list[str]:
    """
    The seed data must actually land on a fresh installation.

    Nothing else here would notice if it did not. The seed changelogs describe rows, not schema, so the
    schema checks pass whether or not a single row was inserted, and the failure surfaces as end-to-end
    tests failing against an empty instance much later.

    Two things break it, and both have happened. A seed changeset filed among the folded changelogs is
    recorded rather than executed, so its rows are missing; the layout check covers that one. The other
    is a seed CSV that still names a column a later changelog dropped, which used to work only because
    the seed ran at a point in the changelog order where the column still existed. A baseline creates
    the schema in its final shape, so the seed has to match that shape.
    """
    with Database(engine, "seed", port) as database:
        # The contexts an end-to-end stack runs with (docker/artemis/config/playwright.env).
        liquibase.apply(database, HISTORY_MASTER, command="changelog-sync", contexts=SEED_CONTEXTS)
        liquibase.apply(database, MASTER, contexts=SEED_CONTEXTS)

        failures = []
        for table, expected in seed_expectations():
            actual = database.row_count(table)
            if actual < expected:
                failures.append(f"{table}: seeded {actual} row(s), expected at least {expected} from the CSV")
        return failures


CHECKS = {
    "replay": check_replay,
    "converge": check_converge,
    "fresh-vs-upgrade": check_fresh_vs_upgrade,
    "upgrade-from-floor": check_upgrade_from_floor,
    "seed": check_seed,
}


# --- Entry point -----------------------------------------------------------------------------------


def ensure_drivers() -> None:
    if DRIVER_DIR.is_dir() and any(DRIVER_DIR.glob("*.jar")):
        return
    log("Exporting JDBC drivers (./gradlew exportLiquibaseDrivers)")
    result = run(["./gradlew", "exportLiquibaseDrivers", "-q"], cwd=REPO)
    if result.returncode != 0:
        raise RuntimeError(f"could not export the JDBC drivers:\n{result.stdout}\n{result.stderr}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--database", choices=[*ENGINES, "both"], default="both")
    parser.add_argument("--check", choices=[*CHECKS, "layout", "all"], default="all")
    parser.add_argument("--base-port", type=int, default=int(os.environ.get("LIQUIBASE_VERIFY_BASE_PORT", "15400")))
    arguments = parser.parse_args()

    failures: dict[str, list[str]] = {}

    if arguments.check in ("layout", "all"):
        log("== layout ==")
        layout_failures = check_layout()
        failures["layout"] = layout_failures
        log("   ok" if not layout_failures else f"   {len(layout_failures)} problem(s)")

    database_checks = [arguments.check] if arguments.check in CHECKS else list(CHECKS)
    engines = list(ENGINES) if arguments.database == "both" else [arguments.database]

    if arguments.check != "layout":
        ensure_drivers()
        with tempfile.TemporaryDirectory(prefix="liquibase-verify-") as scratch:
            workspace = build_workspace(Path(scratch))
            liquibase = Liquibase(workspace)
            port = arguments.base_port
            for engine_name in engines:
                engine = ENGINES[engine_name]
                for check_name in database_checks:
                    log(f"== {check_name} ({engine_name}) ==")
                    started = time.monotonic()
                    try:
                        result = CHECKS[check_name](engine, liquibase, port)
                    except Exception as error:  # noqa: BLE001 - the message is the report
                        result = [str(error)]
                    port += 2
                    failures[f"{check_name} ({engine_name})"] = result
                    log(f"   {'ok' if not result else f'{len(result)} difference(s)'} in {time.monotonic() - started:.0f}s")

    log("")
    total = sum(len(problems) for problems in failures.values())
    if total == 0:
        log(f"All {len(failures)} check(s) passed.")
        return 0

    for name, problems in failures.items():
        if not problems:
            continue
        log(f"--- {name}: {len(problems)} problem(s) ---")
        for problem in problems[:40]:
            log(f"  {problem}")
        if len(problems) > 40:
            log(f"  ... and {len(problems) - 40} more")
    return 1


if __name__ == "__main__":
    sys.exit(main())
