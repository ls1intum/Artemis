"""
Reads a live database schema into a canonical, comparable form.

Two databases that describe the same schema must produce equal models here, and the difference between
two models has to name the table and the object that differ, because that is the only output anyone
reads when a check fails.

Column *order* is deliberately not part of the model. A schema built from one baseline statement lists
its columns in declaration order, while the same schema replayed from incremental changelogs appends
every later column at the end. Both are the same schema, and comparing raw dumps reports the second as
a difference on nearly every table, which is noise that hides the one table that really did diverge.
"""

from __future__ import annotations

import subprocess
from dataclasses import dataclass, field

# Liquibase's own bookkeeping. Their contents are compared separately by the fresh-vs-upgrade check and
# their structure is Liquibase's business, not ours.
LIQUIBASE_TABLES = ("databasechangelog", "databasechangeloglock")


@dataclass
class SchemaModel:
    """A whole schema, keyed by table name. Compare with ``==`` and describe a failure with :func:`diff`."""

    tables: dict[str, dict] = field(default_factory=dict)


def _run_sql(container: str, argv: list[str], sql: str) -> str:
    """Runs a query inside a database container and returns its raw stdout."""
    result = subprocess.run(["docker", "exec", "-i", container, *argv], input=sql, capture_output=True, text=True, check=False)
    if result.returncode != 0:
        raise RuntimeError(f"query failed in {container}: {result.stderr.strip()}")
    return result.stdout


def _rows(raw: str, expected_columns: int) -> list[list[str]]:
    """
    Splits tab-separated query output, dropping blank lines and normalising NULL spellings.

    A row that does not have the expected number of fields is a value containing a tab or a newline --
    a multi-line CHECK clause is the realistic case. Dropping it silently would remove the object from
    both models, which compare equal afterwards, so this raises instead: a comparison that cannot see
    an object must not report the two schemas as matching.
    """
    rows = []
    for line in raw.splitlines():
        if not line.strip():
            continue
        parts = line.split("\t")
        if len(parts) != expected_columns:
            raise RuntimeError(f"cannot read a schema row: expected {expected_columns} fields, got {len(parts)} in {line!r}")
        rows.append(["" if value in ("NULL", "\\N") else value.strip() for value in parts])
    return rows


# --- PostgreSQL ------------------------------------------------------------------------------------

_PG_COLUMNS = """
SELECT c.table_name, c.column_name, c.data_type, COALESCE(c.character_maximum_length::text, ''),
       COALESCE(c.numeric_precision::text, ''), COALESCE(c.numeric_scale::text, ''),
       COALESCE(c.datetime_precision::text, ''), c.is_nullable,
       COALESCE(c.column_default, ''), c.is_identity
FROM information_schema.columns c
JOIN information_schema.tables t ON t.table_name = c.table_name AND t.table_schema = c.table_schema
WHERE c.table_schema = 'public' AND t.table_type = 'BASE TABLE';
"""

_PG_CONSTRAINTS = """
SELECT con.conrelid::regclass::text, con.conname, con.contype,
       pg_get_constraintdef(con.oid)
FROM pg_constraint con
JOIN pg_class rel ON rel.oid = con.conrelid
JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
WHERE nsp.nspname = 'public';
"""

_PG_INDEXES = """
SELECT tablename, indexname, indexdef FROM pg_indexes WHERE schemaname = 'public';
"""


def read_postgres(container: str, database: str, user: str) -> SchemaModel:
    psql = ["psql", "-U", user, "-d", database, "-t", "-A", "-F", "\t", "-v", "ON_ERROR_STOP=1", "-f", "-"]
    tables: dict[str, dict] = {}

    for table, column, data_type, char_len, precision, scale, dt_precision, nullable, default, identity in _rows(
        _run_sql(container, psql, _PG_COLUMNS), 10
    ):
        if table.lower() in LIQUIBASE_TABLES:
            continue
        entry = tables.setdefault(table, {"columns": {}, "constraints": {}, "indexes": {}})
        entry["columns"][column] = {
            "type": data_type,
            "length": char_len,
            "precision": precision,
            "scale": scale,
            "datetime_precision": dt_precision,
            "nullable": nullable == "YES",
            # A serial column's default is a nextval() over a sequence whose name is an implementation
            # detail of when the table was created, so record only that the column is generated.
            "default": "" if default.startswith("nextval(") else default,
            "identity": identity == "YES" or default.startswith("nextval("),
        }

    for table, name, contype, definition in _rows(_run_sql(container, psql, _PG_CONSTRAINTS), 4):
        table = table.strip('"')
        if table.lower() in LIQUIBASE_TABLES or table not in tables:
            continue
        tables[table]["constraints"][name] = {"type": contype, "definition": definition}

    for table, name, definition in _rows(_run_sql(container, psql, _PG_INDEXES), 3):
        if table.lower() in LIQUIBASE_TABLES or table not in tables:
            continue
        # An index backing a primary key or unique constraint is already recorded as that constraint.
        if name in tables[table]["constraints"]:
            continue
        tables[table]["indexes"][name] = definition.split(" USING ", 1)[-1]

    return SchemaModel(tables)


# --- MySQL -----------------------------------------------------------------------------------------

# COLUMN_TYPE, not just DATA_TYPE. DATA_TYPE for an enum is the word "enum"; the value list -- which is
# most of what the MySQL-specific changeset of a baseline says -- lives only in COLUMN_TYPE, along with
# the display width and the unsigned flag that distinguish tinyint(1) from tinyint.
_MYSQL_COLUMNS = """
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IFNULL(CHARACTER_MAXIMUM_LENGTH, ''),
       IFNULL(NUMERIC_PRECISION, ''), IFNULL(NUMERIC_SCALE, ''), IFNULL(DATETIME_PRECISION, ''),
       IS_NULLABLE, IFNULL(COLUMN_DEFAULT, ''), EXTRA
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE();
"""

# REFERENTIAL_CONSTRAINTS carries ON DELETE and ON UPDATE, which TABLE_CONSTRAINTS does not. Without
# them a foreign key written with the wrong referential action compares equal on MySQL while PostgreSQL,
# whose pg_get_constraintdef spells the action out, would report it.
_MYSQL_CONSTRAINTS = """
SELECT tc.TABLE_NAME, tc.CONSTRAINT_NAME, tc.CONSTRAINT_TYPE,
       GROUP_CONCAT(kcu.COLUMN_NAME ORDER BY kcu.ORDINAL_POSITION),
       IFNULL(MAX(kcu.REFERENCED_TABLE_NAME), ''),
       IFNULL(GROUP_CONCAT(kcu.REFERENCED_COLUMN_NAME ORDER BY kcu.ORDINAL_POSITION), ''),
       IFNULL(MAX(rc.DELETE_RULE), ''), IFNULL(MAX(rc.UPDATE_RULE), '')
FROM information_schema.TABLE_CONSTRAINTS tc
LEFT JOIN information_schema.KEY_COLUMN_USAGE kcu
       ON kcu.CONSTRAINT_NAME = tc.CONSTRAINT_NAME AND kcu.TABLE_SCHEMA = tc.TABLE_SCHEMA
      AND kcu.TABLE_NAME = tc.TABLE_NAME
LEFT JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
       ON rc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME AND rc.CONSTRAINT_SCHEMA = tc.TABLE_SCHEMA
      AND rc.TABLE_NAME = tc.TABLE_NAME
WHERE tc.TABLE_SCHEMA = DATABASE()
GROUP BY tc.TABLE_NAME, tc.CONSTRAINT_NAME, tc.CONSTRAINT_TYPE;
"""

_MYSQL_INDEXES = """
SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE;
"""

_MYSQL_CHECKS = """
SELECT tc.TABLE_NAME, cc.CONSTRAINT_NAME, cc.CHECK_CLAUSE
FROM information_schema.CHECK_CONSTRAINTS cc
JOIN information_schema.TABLE_CONSTRAINTS tc
       ON tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME AND tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA
WHERE cc.CONSTRAINT_SCHEMA = DATABASE();
"""


def read_mysql(container: str, database: str, user: str) -> SchemaModel:
    mysql = ["mysql", "--default-character-set=utf8mb4", "-u", user, "-N", "-B", "--database", database]
    tables: dict[str, dict] = {}

    for table, column, data_type, char_len, precision, scale, dt_precision, nullable, default, extra in _rows(
        _run_sql(container, mysql, _MYSQL_COLUMNS), 10
    ):
        if table.lower() in LIQUIBASE_TABLES:
            continue
        entry = tables.setdefault(table, {"columns": {}, "constraints": {}, "indexes": {}})
        entry["columns"][column] = {
            "type": data_type,
            "length": char_len,
            "precision": precision,
            "scale": scale,
            "datetime_precision": dt_precision,
            "nullable": nullable == "YES",
            "default": default,
            "identity": "auto_increment" in extra,
        }

    for table, name, contype, columns, ref_table, ref_columns, on_delete, on_update in _rows(_run_sql(container, mysql, _MYSQL_CONSTRAINTS), 8):
        if table.lower() in LIQUIBASE_TABLES or table not in tables:
            continue
        # CHECK constraints appear here without columns; their clause is read separately below.
        if contype == "CHECK":
            continue
        definition = f"{contype} ({columns})"
        if ref_table:
            definition += f" REFERENCES {ref_table} ({ref_columns}) ON DELETE {on_delete} ON UPDATE {on_update}"
        tables[table]["constraints"][name] = {"type": contype, "definition": definition}

    for table, name, clause in _rows(_run_sql(container, mysql, _MYSQL_CHECKS), 3):
        if table.lower() in LIQUIBASE_TABLES or table not in tables:
            continue
        tables[table]["constraints"][name] = {"type": "CHECK", "definition": clause}

    for table, name, non_unique, columns in _rows(_run_sql(container, mysql, _MYSQL_INDEXES), 4):
        if table.lower() in LIQUIBASE_TABLES or table not in tables:
            continue
        if name in tables[table]["constraints"]:
            continue
        unique = "UNIQUE " if non_unique == "0" else ""
        tables[table]["indexes"][name] = f"{unique}({columns})"

    return SchemaModel(tables)


# --- Comparison ------------------------------------------------------------------------------------


def diff(left: SchemaModel, right: SchemaModel, left_label: str, right_label: str) -> list[str]:
    """Returns one human-readable line per difference, or an empty list when the two schemas match."""
    differences: list[str] = []

    for table in sorted(set(left.tables) | set(right.tables)):
        if table not in left.tables:
            differences.append(f"table {table}: missing in {left_label}")
            continue
        if table not in right.tables:
            differences.append(f"table {table}: missing in {right_label}")
            continue

        for kind in ("columns", "constraints", "indexes"):
            left_objects = left.tables[table][kind]
            right_objects = right.tables[table][kind]
            for name in sorted(set(left_objects) | set(right_objects)):
                if name not in left_objects:
                    differences.append(f"{table}.{name} ({kind[:-1]}): only in {right_label}")
                elif name not in right_objects:
                    differences.append(f"{table}.{name} ({kind[:-1]}): only in {left_label}")
                elif left_objects[name] != right_objects[name]:
                    differences.append(f"{table}.{name} ({kind[:-1]}): {left_label}={left_objects[name]} {right_label}={right_objects[name]}")

    return differences
