#!/usr/bin/env python3
"""
Verifies that the Liquibase changelogs in this repository produce the schema they claim to.

These checks run; layout and text-introductions need no database, the rest run against PostgreSQL and MySQL:

  layout            Structural rules the others depend on: every folded changelog pins its
                    logicalFilePath, and no folded changelog is gated by a context.
  text-introductions  No changelog under changelog/ introduces an unbounded-text column (text/longtext)
                    that is not deliberately allow-listed (safeguard against findings F-005/F-007). Runs as
                    part of --check layout; it is not separately selectable via --check.
  replay            An empty database accepts the whole folded history in order.
  converge          The baseline produces the same schema as replaying the history it folded.
  fresh-vs-upgrade  A fresh installation and one that replayed the whole folded history end up with
                    the same schema and the same recorded changesets.
  upgrade-from-floor  An installation at the oldest release this one can upgrade from ends up matching
                    a fresh installation.
  seed              The seed data actually lands on a fresh installation.
  column-types      The final applied schema contains no unbounded-text column that is not grandfathered.

Run everything, which is what CI does:

    python3 supporting_scripts/liquibase/verify_schema.py

Run one check against one database while working on a baseline:

    python3 supporting_scripts/liquibase/verify_schema.py --check converge --database postgres

Needs Docker, and the JDBC drivers exported by ``./gradlew exportLiquibaseDrivers`` (this script runs
that for you if they are missing).
"""

from __future__ import annotations

import argparse
import csv
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

# The contexts an end-to-end stack starts with; see deployment/docker/artemis/config/playwright.env.
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

# --- Unbounded-text safeguard (Layer 1 / Safeguard S1) ---------------------------------------------
# Free-form text columns declared as an unbounded type (text/mediumtext/longtext/clob) accept
# arbitrarily large values, a storage-exhaustion and parse-cost risk (findings F-005/F-007). This guard
# stops the set of such columns from growing unreviewed. Two independent checks share three lists:
#   * check_column_types (database phase) asserts the final applied schema contains no unbounded-text
#     column that is not in GRANDFATHERED_UNBOUNDED_TEXT.
#   * scan_text_introductions (layout phase, no database) asserts that no changelog under changelog/
#     introduces an unbounded-text column that is not in ALLOWED_TEXT_INTRODUCTIONS, and that every
#     changeset the scanner cannot fully read and that might introduce unbounded text (<sqlFile>, a
#     custom change, a raw <sql> block mentioning a text type, or an unresolved ${property}) is in
#     UNVERIFIABLE_CHANGESETS.
# The grandfather list is keyed by (table, column) and is deliberately decoupled from the baseline, so a
# baseline re-cut cannot grow it -- only a reviewed edit here can. Introductions are keyed by their full
# historical identity so that dropping a grandfathered column and re-adding it under the same name in a
# NEW changeset is not waved through by the name alone.

# The Liquibase ClobType family (every alias Liquibase resolves to its unbounded CLOB type), as MATERIALISED.
# ntext/nclob are the national-charset siblings; longvarchar/longnvarchar and their spaced forms
# "long varchar"/"long nvarchar" are the JDBC-named large-text aliases. The java.sql.Types.* forms (e.g.
# java.sql.Types.CLOB) are handled in is_unbounded_text by comparing the final dotted segment.
# tinytext is deliberately NOT here: Liquibase keeps a declared tinytext bounded (TINYTEXT, 255) on MySQL, so a
# materialised MySQL tinytext column is bounded. But a declared tinytext is materialised as unbounded TEXT on
# PostgreSQL, so the SOURCE scan must still treat it as unbounded -- SOURCE_UNBOUNDED_TEXT_TYPES adds it for the
# changelog scan only, while the materialised-schema check keeps excluding MySQL TINYTEXT.
UNBOUNDED_TEXT_TYPES = frozenset(
    {"text", "mediumtext", "longtext", "clob", "ntext", "nclob", "longvarchar", "longnvarchar", "long varchar", "long nvarchar"}
)
SOURCE_UNBOUNDED_TEXT_TYPES = UNBOUNDED_TEXT_TYPES | {"tinytext"}

# Character-varying types are unbounded only when NO length limit is given: a PostgreSQL 'character varying' /
# 'varchar' with no (n) stores up to ~1 GB. Handled separately (is_unbounded_varchar) because it depends on the
# length, not the type name alone.
VARYING_TYPES = frozenset({"varchar", "character varying", "char varying", "nvarchar", "national character varying"})

# Type namespaces whose final segment names the real type: JDBC (java.sql.Types.*) and PostgreSQL's system
# catalog (pg_catalog.*). Only these exact prefixes are unwrapped, so a schema-qualified/custom type such as
# my_schema.text is never reinterpreted.
KNOWN_TYPE_NAMESPACES = ("java.sql.types.", "pg_catalog.")

# The (table, column) pairs that were already unbounded text when this guard was introduced. They are
# grandfathered so the guard is green on day one; a new unbounded-text column must not be added (give it a
# bounded type, varchar(n)). Tightening these existing columns is tracked by the text-length hardening
# effort, not by this guard. Consulted only by check_column_types. tinytext (255 bytes) and binary
# blob/bytea are intentionally not covered; they are not unbounded free-form text. Deliberately decoupled
# from the baseline so a re-cut cannot grow it -- only a reviewed edit here can.
GRANDFATHERED_UNBOUNDED_TEXT: set[tuple[str, str]] = {
    ("answer_post", "content"),
    ("apollon_diagram", "json_representation"),
    ("assessment_note", "note"),
    ("competency", "description"),
    ("complaint", "complaint_text"),
    ("complaint_response", "response_text"),
    ("course", "description"),
    ("course", "info_sharing_messaging_code_of_conduct"),
    ("course_request", "decision_reason"),
    ("course_request", "reason"),
    ("exam", "confirmation_end_text"),
    ("exam", "confirmation_start_text"),
    ("exam", "end_text"),
    ("exam", "start_text"),
    ("exam_live_event", "problem_statement"),
    ("exam_live_event", "text_content"),
    ("exercise", "example_solution"),
    ("exercise", "example_solution_explanation"),
    ("exercise", "example_solution_model"),
    ("exercise", "grading_instructions"),
    ("exercise", "problem_statement"),
    ("feedback_message", "text"),
    ("grading_instruction", "feedback"),
    ("grading_instruction", "instruction_description"),
    ("iris_json_message_content", "json_content"),
    ("iris_message", "origin"),
    ("iris_message", "proactive_outcome"),
    ("iris_message", "sender_origin"),
    ("iris_message", "tool_activity"),
    ("iris_proactive_episode", "hint_text"),
    ("iris_session", "chat_mode"),
    ("iris_session", "latest_suggestions"),
    ("iris_text_message_content", "text_content"),
    ("lecture", "description"),
    ("lecture_transcription", "transcription_status"),
    ("lecture_unit", "content"),
    ("lecture_unit", "description"),
    ("lecture_unit", "source"),
    ("long_feedback_text", "feedback_text"),
    ("plagiarism_case", "verdict_message"),
    ("programming_exercise_build_config", "build_plan_configuration"),
    ("programming_exercise_build_config", "docker_flags"),
    ("review_comment", "type"),
    ("review_comment_thread", "target_type"),
    ("spring_ai_chat_memory", "content"),
    ("spring_ai_chat_memory", "type"),
    ("submission", "explanation_text"),
    ("submission", "model"),
    ("submission", "text"),
    ("submission_version", "content"),
    ("system_notification", "text"),
    ("text_block", "text"),
    ("tutorial_group", "additional_information"),
    ("tutorial_group_schedule", "location"),
    ("tutorial_group_session", "location"),
    ("weaviate_outbox", "params"),
}

# (changelog_file, changeset_id, author, table, column) -> reason. Consulted only by scan_text_introductions.
# Keyed by the full Liquibase changeset identity (id + author + file) so that reusing an id in the same file
# under a different author does not inherit this approval.
ALLOWED_TEXT_INTRODUCTIONS: dict[tuple[str, str, str, str, str], str] = {
    ("config/liquibase/changelog/20260808192146_changelog.xml", "20260808192146-1", "nayerkotry", "weaviate_outbox", "params"):
        "Transactional-outbox payload for the Weaviate search sync; an internal, serialized operation record, not a user-facing free-text field.",
    ("config/liquibase/changelog/20261004235109_changelog.xml", "20261004235109-1-add-sender-origin-postgres", "senan-aslan", "iris_message", "sender_origin"):
        "A short enum ('IOS'/'ANDROID'); MySQL uses ENUM(...), PostgreSQL has no inline enum so it is stored as text. Effectively bounded, not free-form text.",
}

# (changelog_file, changeset_id, author) -> (reason, introduces). A changeset under changelog/ the scanner
# cannot fully read -- <sqlFile>, a custom change class, a raw <sql> block mentioning a text type or an
# unresolved ${property}, or a
# column whose type is an unresolved ${property} -- is hard-failed unless listed here. `introduces` is the
# tuple of (table, column) pairs the changeset is attested to add or re-add as unbounded text, or () when it
# adds none. Each declared pair must also be in ALLOWED_TEXT_INTRODUCTIONS (keyed by this changeset), so
# exempting a raw-SQL drop/re-add of a grandfathered column still needs an explicit, changeset-scoped
# introduction entry and cannot pass on the column name alone. (The final-state list is not required here:
# check_column_types already demands that any column still unbounded in the schema be grandfathered, and
# coupling the two would deadlock once a later migration bounds or drops the column.) An empty () is an
# explicit, reviewed attestation that the changeset adds no unbounded-text column; it cannot be
# machine-verified for opaque SQL and is a deliberate, documented trust point.
UNVERIFIABLE_CHANGESETS: dict[tuple[str, str, str], tuple[str, tuple[tuple[str, str], ...]]] = {
}

RESOURCES = REPO / "src/main/resources"
CHANGELOG_DIR = LIQUIBASE_DIR / "changelog"
CREATE_TABLE_TAG = f"{NAMESPACE}createTable"
ADD_COLUMN_TAG = f"{NAMESPACE}addColumn"
COLUMN_TAG = f"{NAMESPACE}column"
MODIFY_DATA_TYPE_TAG = f"{NAMESPACE}modifyDataType"
RENAME_COLUMN_TAG = f"{NAMESPACE}renameColumn"
MERGE_COLUMNS_TAG = f"{NAMESPACE}mergeColumns"
ROLLBACK_TAG = f"{NAMESPACE}rollback"
SQL_TAG = f"{NAMESPACE}sql"
MODIFY_SQL_TAG = f"{NAMESPACE}modifySql"
COMMENT_TAG = f"{NAMESPACE}comment"
# Opaque changes that cannot be statically resolved to a definite set of column types, so they are always
# flagged for review: <sqlFile> (an external file) and a custom change class cannot be read at all, and
# <modifySql> rewrites the generated SQL in arbitrary ways whose RESULT cannot be established (e.g. a
# <replace replace="(255)" with=""/> that silently drops a length and turns a bounded VARCHAR into an
# unbounded one). A raw <sql> block, by contrast, is read in full (including the text that follows a nested
# child such as <comment>, which element.text alone would miss): it is only flagged when that text mentions an
# unbounded type (a text/CLOB type or a no-length varchar), so the many raw-SQL data migrations (FK backfills,
# constraints) pass untouched while a raw-SQL unbounded-column introduction still needs a conscious decision.
# check_column_types backstops everything by inspecting the final materialised schema regardless of how a
# column was created.
HARD_OPAQUE_TAGS = frozenset({f"{NAMESPACE}sqlFile", f"{NAMESPACE}customChange", MODIFY_SQL_TAG})
TEXT_TYPE_TOKEN = re.compile(r"\b(?:text|longtext|mediumtext|clob|ntext|nclob|longvarchar|longnvarchar|long\s+varchar|long\s+nvarchar)\b", re.IGNORECASE)
# A character-varying type with no length is unbounded. In raw SQL we cannot read a length argument reliably, so
# we flag a varchar / character varying / char varying / nvarchar token that is NOT immediately followed by a
# "(...)" length -- "varchar(255)" is bounded and does not match, a bare "varchar" does and is sent to attestation.
VARYING_SQL_TOKEN = re.compile(r"\b(?:varchar|character\s+varying|char\s+varying|nvarchar|national\s+character\s+varying)\b(?!\s*\()", re.IGNORECASE)


def normalize_identifier(name: str | None) -> str:
    """Lower-cases an identifier and strips one surrounding quote/backtick/bracket pair."""
    if not name:
        return ""
    name = name.strip()
    for opener, closer in (('"', '"'), ("`", "`"), ("[", "]")):
        if len(name) >= 2 and name[0] == opener and name[-1] == closer:
            name = name[1:-1]
            break
    return name.lower()


def _normalize_type(sql_type: str) -> str:
    """Drop any length argument, lower-case, and collapse internal whitespace ("long  varchar" -> "long varchar")."""
    return " ".join(sql_type.split("(", 1)[0].lower().split())


def _strip_known_namespace(base: str) -> str:
    """For a type named through a known namespace (java.sql.Types.* / pg_catalog.*) return its final segment;
    otherwise return the name unchanged, so a schema-qualified/custom type such as my_schema.text is not
    reinterpreted."""
    for prefix in KNOWN_TYPE_NAMESPACES:
        if base.startswith(prefix):
            return base.rsplit(".", 1)[-1]
    return base


def _is_unbounded_text(sql_type: str | None, types: frozenset[str] | set[str]) -> bool:
    """Shared core: True when sql_type (any length argument dropped, case- and whitespace-normalised, and a known
    java.sql.Types.* / pg_catalog.* namespace unwrapped) names one of the given unbounded types."""
    if not sql_type:
        return False
    base = _normalize_type(sql_type)
    if not base:
        return False
    return base in types or _strip_known_namespace(base) in types


def is_unbounded_text(sql_type: str | None) -> bool:
    """
    MATERIALISED-schema test: True for the Liquibase unbounded-text/CLOB family (text/mediumtext/longtext/clob/
    ntext/nclob/longvarchar/longnvarchar, the spaced "long varchar"/"long nvarchar", and their java.sql.Types.*
    aliases). A materialised MySQL TINYTEXT is bounded and is NOT matched here.
    """
    return _is_unbounded_text(sql_type, UNBOUNDED_TEXT_TYPES)


def is_unbounded_text_source(sql_type: str | None) -> bool:
    """
    SOURCE-declaration test: like is_unbounded_text, but also treats a declared `tinytext` as unbounded, because
    Liquibase materialises a declared tinytext as unbounded TEXT on PostgreSQL (only MySQL keeps the bounded
    TINYTEXT). Used for changelog introductions; the materialised-schema check uses is_unbounded_text instead.
    """
    return _is_unbounded_text(sql_type, SOURCE_UNBOUNDED_TEXT_TYPES)


def is_unbounded_varchar(sql_type: str | None, length: str | int | None = None) -> bool:
    """
    True for a character-varying type with no length limit (an unbounded PostgreSQL 'character varying'/'varchar').

    The limit may be inline (varchar(255)) or recorded as separate schema metadata (``length``); when neither is
    present the column is unbounded. A declared type with no length metadata (``length`` is None) and no inline
    ``(n)`` is treated as unbounded -- the source-scan case, where only the declared type string is available.
    A known namespace is unwrapped first, so pg_catalog.varchar / java.sql.Types.VARCHAR are recognised too.
    """
    if not sql_type:
        return False
    raw = sql_type.strip().lower()
    if "(" in raw:  # an inline length such as varchar(255) / character varying(255)
        return False
    base = _strip_known_namespace(" ".join(raw.split()))
    if base not in VARYING_TYPES:
        return False
    return length is None or str(length).strip() in ("", "0")


def introduces_unbounded_text(sql_type: str | None) -> bool:
    """
    Source-side predicate for a declared column type: True when it materialises as unbounded text -- the Liquibase
    ClobType family (tinytext included, see is_unbounded_text_source) or a character-varying type declared with no
    length.
    """
    return is_unbounded_text_source(sql_type) or is_unbounded_varchar(sql_type)


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

    def command(self, database: Database, changelog: str, command: str, contexts: str = "prod", command_args: tuple[str, ...] = ()) -> subprocess.CompletedProcess:
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
            *command_args,
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
    Returns (table, surviving row count) for every CSV the seed changelogs load.

    Parsed, not matched with a regular expression: a pattern anchored on attribute order finds nothing
    the day someone writes tableName before file, and an expectation that quietly disappears turns this
    check into one that passes while the seed is missing, which is the failure it exists to catch.
    """
    expectations = []
    for changelog in sorted((LIQUIBASE_DIR / "data").glob("*.xml")):
        for element in ElementTree.parse(changelog).getroot().iter(f"{NAMESPACE}loadData"):
            csv_file = REPO / "src/main/resources" / element.get("file")
            with csv_file.open(newline="") as source:
                rows = list(csv.DictReader(source, delimiter=element.get("separator", ",")))
            table = element.get("tableName")
            if table == "jhi_user_authority":
                # The course-authorities migration removes these rows after the historical seed runs. Keep that seed
                # unchanged for installations that have recorded its checksum, but expect only its surviving authorities.
                rows = [row for row in rows if row["authority_name"] not in {"ROLE_TA", "ROLE_EDITOR", "ROLE_INSTRUCTOR"}]
            expectations.append((table, len(rows)))
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
        # The contexts an end-to-end stack runs with (deployment/docker/artemis/config/playwright.env).
        liquibase.apply(database, HISTORY_MASTER, command="changelog-sync", contexts=SEED_CONTEXTS)
        liquibase.apply(database, MASTER, contexts=SEED_CONTEXTS)

        failures = []
        for table, expected in seed_expectations():
            actual = database.row_count(table)
            if actual < expected:
                failures.append(f"{table}: seeded {actual} row(s), expected at least {expected} from the CSV")
        return failures


def iter_forward_elements(element):
    """
    Yield every descendant of a changeset except those inside a <rollback> block.

    A <rollback> describes the reverse operation; treating its createTable/addColumn/sql as a forward change
    would raise false positives (e.g. a rollback that restores a text column or carries a <sqlFile>).
    """
    for child in element:
        if child.tag == ROLLBACK_TAG:
            continue
        yield child
        yield from iter_forward_elements(child)


def unbounded_text_introductions(changeset) -> set[tuple[str, str]]:
    """
    Every (table, column) a changeset introduces as unbounded text, via a declarative change whose type is
    readable: createTable/addColumn (the column's type), modifyDataType (newDataType), renameColumn
    (columnDataType, which some databases require to re-emit the column's type), and mergeColumns
    (finalColumnType of the new merged column).
    """
    found: set[tuple[str, str]] = set()
    for element in iter_forward_elements(changeset):
        if element.tag in (CREATE_TABLE_TAG, ADD_COLUMN_TAG):
            table = normalize_identifier(element.get("tableName"))
            for column in element:
                if column.tag == COLUMN_TAG and introduces_unbounded_text(column.get("type")):
                    found.add((table, normalize_identifier(column.get("name"))))
        elif element.tag == MODIFY_DATA_TYPE_TAG and introduces_unbounded_text(element.get("newDataType")):
            found.add((normalize_identifier(element.get("tableName")), normalize_identifier(element.get("columnName"))))
        elif element.tag == RENAME_COLUMN_TAG and introduces_unbounded_text(element.get("columnDataType")):
            found.add((normalize_identifier(element.get("tableName")), normalize_identifier(element.get("newColumnName"))))
        elif element.tag == MERGE_COLUMNS_TAG and introduces_unbounded_text(element.get("finalColumnType")):
            found.add((normalize_identifier(element.get("tableName")), normalize_identifier(element.get("finalColumnName"))))
    return found


def raw_sql_text(element) -> str:
    """
    The full SQL text of a <sql> element, including text that follows a nested child element.

    ElementTree puts the text before the first child in ``element.text`` and the text after a child in that
    child's ``.tail``; a bare ``element.text`` therefore misses the SQL written after a nested <comment>
    (reported: ``<sql><comment>..</comment>CREATE TABLE .. TEXT</sql>`` slipped through). A <comment> child's
    own text is excluded, so prose in a comment does not trip the token scan.
    """
    parts = [element.text or ""]
    for child in element:
        if child.tag != COMMENT_TAG:
            parts.append("".join(child.itertext()))
        parts.append(child.tail or "")
    return "".join(parts)


def _mentions_unbounded(blob: str) -> bool:
    """True when a chunk of raw SQL mentions an unbounded type: a text/CLOB token, a no-length varchar token, or
    an unresolved ${property} that could substitute either."""
    return bool(TEXT_TYPE_TOKEN.search(blob) or VARYING_SQL_TOKEN.search(blob)) or "${" in blob


def needs_manual_review(changeset) -> bool:
    """
    True when a changeset (outside its <rollback>) carries something the scanner cannot statically resolve to a
    definite set of column types: a <sqlFile>, custom change class or <modifySql> (all in HARD_OPAQUE_TAGS,
    since a modifySql rewrite's result -- e.g. dropping a length -- cannot be established), a raw <sql> block
    mentioning an unbounded type (a text/CLOB type, a no-length varchar, or an unresolved ${property}), or a
    column/modifyDataType/renameColumn/mergeColumns whose type is an unresolved ${property}. Such a changeset
    must be attested in UNVERIFIABLE_CHANGESETS.
    """
    for element in iter_forward_elements(changeset):
        if element.tag in HARD_OPAQUE_TAGS or element.get("class"):
            return True
        if element.tag == SQL_TAG and _mentions_unbounded(raw_sql_text(element)):
            return True
        if element.tag == COLUMN_TAG and "${" in (element.get("type") or ""):
            return True
        if element.tag == MODIFY_DATA_TYPE_TAG and "${" in (element.get("newDataType") or ""):
            return True
        if element.tag == RENAME_COLUMN_TAG and "${" in (element.get("columnDataType") or ""):
            return True
        if element.tag == MERGE_COLUMNS_TAG and "${" in (element.get("finalColumnType") or ""):
            return True
    return False


def changelog_files() -> list[tuple[str, Path]]:
    """(path relative to src/main/resources, absolute path) for every changelog under changelog/, recursively."""
    return [(path.relative_to(RESOURCES).as_posix(), path) for path in sorted(CHANGELOG_DIR.rglob("*.xml"))]


def scan_text_introductions(files: list[tuple[str, Path]], allowed_introductions, unverifiable_changesets) -> list[str]:
    """
    Static, no-database scan of the post-baseline changelogs (see the safeguard block near the top).

    Pure: every input is passed in, so the unit tests drive it with temporary changelogs. Returns a list of
    problem strings; empty means the changelogs add no unreviewed unbounded-text column. Changesets are keyed
    by their full Liquibase identity (file, id, author); <rollback> blocks are ignored. An opaque attestation
    waives only the opaque hard-failure; declarative changes in the same changeset are still checked, so an
    attestation cannot silently cover a later-added structured change.
    """
    failures: list[str] = []
    seen_introductions: set[tuple[str, str, str, str, str]] = set()
    seen_unverifiable: set[tuple[str, str, str]] = set()
    for relative, path in files:
        for changeset in ElementTree.parse(path).getroot().iter(CHANGESET_TAG):
            identifier = changeset.get("id", "?")
            author = changeset.get("author", "?")
            if needs_manual_review(changeset):
                key = (relative, identifier, author)
                seen_unverifiable.add(key)
                entry = unverifiable_changesets.get(key)
                if entry is None:
                    failures.append(
                        f"{relative}: changeset {identifier} ({author}) carries a raw <sql>/<sqlFile>/<modifySql>/custom change "
                        f"or an unresolved ${{property}} (in a column type or raw SQL) the guard cannot read and that may "
                        f'introduce unbounded text. Review it, then add ("{relative}", "{identifier}", "{author}"): ("<reason>", '
                        f"(<(table, column) pairs it introduces, or empty>)) to UNVERIFIABLE_CHANGESETS."
                    )
                else:
                    _reason, introduces = entry
                    for declared_table, declared_column in introduces:
                        pair = (normalize_identifier(declared_table), normalize_identifier(declared_column))
                        intro_key = (relative, identifier, author, pair[0], pair[1])
                        seen_introductions.add(intro_key)
                        if intro_key not in allowed_introductions:
                            failures.append(
                                f"{relative}: changeset {identifier} ({author}) is attested to introduce {pair[0]}.{pair[1]}, "
                                f"but {intro_key} is not in ALLOWED_TEXT_INTRODUCTIONS."
                            )
            for table, column in unbounded_text_introductions(changeset):
                key = (relative, identifier, author, table, column)
                seen_introductions.add(key)
                if key not in allowed_introductions:
                    failures.append(
                        f"{relative}: changeset {identifier} ({author}) introduces unbounded-text column {table}.{column}. "
                        f"Give it a bounded type (varchar(n)), or, if it is deliberate, add "
                        f'("{relative}", "{identifier}", "{author}", "{table}", "{column}") to ALLOWED_TEXT_INTRODUCTIONS with a reason.'
                    )
    for key in sorted(allowed_introductions):
        if key not in seen_introductions:
            failures.append(f"ALLOWED_TEXT_INTRODUCTIONS entry {key} matches no current unbounded-text introduction; remove it.")
    for key in sorted(unverifiable_changesets):
        if key not in seen_unverifiable:
            failures.append(f"UNVERIFIABLE_CHANGESETS entry {key} matches no current reviewed changeset; remove it.")
    return failures


def evaluate_unbounded_text_columns(schema: db_schema.SchemaModel, grandfathered: set[tuple[str, str]], check_stale: bool) -> list[str]:
    """
    The pure core of the final-state check: given a schema, report unbounded-text columns that are not
    grandfathered, and (when check_stale) grandfather entries that no longer name an unbounded-text
    column. Separated from the database plumbing so it can be unit-tested without Docker.
    """
    failures: list[str] = []
    present: set[tuple[str, str]] = set()
    for table in sorted(schema.tables):
        if normalize_identifier(table) in ("databasechangelog", "databasechangeloglock"):
            continue
        for column, meta in sorted(schema.tables[table]["columns"].items()):
            if not (is_unbounded_text(meta.get("type")) or is_unbounded_varchar(meta.get("type"), meta.get("length"))):
                continue
            key = (normalize_identifier(table), normalize_identifier(column))
            present.add(key)
            if key not in grandfathered:
                failures.append(
                    f"{table}.{column} is unbounded text ({meta.get('type')}) and is not grandfathered. Give it a bounded "
                    f'type (varchar(n)), or, if it is deliberate, add ("{key[0]}", "{key[1]}") to '
                    f"GRANDFATHERED_UNBOUNDED_TEXT with a reason."
                )
    if check_stale:
        for key in sorted(grandfathered):
            if key not in present:
                failures.append(f"GRANDFATHERED_UNBOUNDED_TEXT entry {key} is no longer an unbounded-text column; remove it.")
    return failures


def check_column_types(engine: Engine, liquibase: Liquibase, port: int) -> list[str]:
    """The final applied schema must contain no unbounded-text column that is not grandfathered."""
    with Database(engine, "column-types", port) as database:
        liquibase.apply(database, MASTER)
        schema = database.schema()
    # The stale-entry check runs on PostgreSQL only. PostgreSQL has no inline enum type, so a column
    # declared ENUM(...) is stored as text there while MySQL keeps it as a bounded enum; such a column is
    # legitimately grandfathered (it is unbounded text on PostgreSQL) yet is not unbounded text on MySQL.
    # PostgreSQL is the superset -- every column that is unbounded text on MySQL is also text on
    # PostgreSQL -- so it is the right reference for "this grandfathered column is no longer unbounded".
    return evaluate_unbounded_text_columns(schema, GRANDFATHERED_UNBOUNDED_TEXT, check_stale=engine.name == "postgres")


CHECKS = {
    "replay": check_replay,
    "converge": check_converge,
    "fresh-vs-upgrade": check_fresh_vs_upgrade,
    "upgrade-from-floor": check_upgrade_from_floor,
    "seed": check_seed,
    "column-types": check_column_types,
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

        log("== text-introductions ==")
        introduction_failures = scan_text_introductions(changelog_files(), ALLOWED_TEXT_INTRODUCTIONS, UNVERIFIABLE_CHANGESETS)
        failures["text-introductions"] = introduction_failures
        log("   ok" if not introduction_failures else f"   {len(introduction_failures)} problem(s)")

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
