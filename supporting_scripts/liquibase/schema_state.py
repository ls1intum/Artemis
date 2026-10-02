"""
An in-memory model of a schema, built by replaying Liquibase change elements onto it.

This is how a baseline is folded. Reading the shape of the database out of a *running* database and
writing it back out produces types spelled the way that one engine spells them -- ``TIMESTAMP(3)
WITHOUT TIME ZONE``, ``FLOAT8`` -- which is why the manual procedure forbade generating a baseline from
a dump. Replaying the changelogs' own change elements instead keeps the types exactly as the changelogs
wrote them, which is the portable spelling Liquibase maps per database.

Anything this model does not understand raises :class:`UnsupportedChange` rather than being ignored. A
silently skipped change is a baseline that is wrong in a way no check phrased in terms of the baseline
can see, so the only safe default is to stop and make a person look.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field

NAMESPACE = "{http://www.liquibase.org/xml/ns/dbchangelog}"

# Raw SQL that changes the shape of the database has to be carried into the baseline; raw SQL that moves
# data around must not be, because a baseline only ever runs against a database that has none.
CARRIED_DDL = re.compile(r"\b(ALTER\s+TABLE|CREATE\s+TABLE|DROP\s+TABLE|CREATE\s+(UNIQUE\s+)?INDEX|DROP\s+INDEX|RENAME\s+TABLE)\b", re.IGNORECASE)

# Anything else that changes a definition. Carrying these is not implemented, and silently dropping one
# would leave the baseline missing an object, so they are refused instead.
OTHER_DDL = re.compile(r"\b(ALTER|CREATE|DROP|RENAME|TRUNCATE)\s+", re.IGNORECASE)

# A helper the same migration creates and drops again: PostgreSQL's temporary namespace, or the tmp_
# prefix this repository gives them. It exists for the length of one migration, so a baseline running
# against an empty database has nothing for it to do and nothing to carry forward.
TRANSIENT_OBJECT = re.compile(r"\b(pg_temp\.|tmp_)", re.IGNORECASE)


class UnsupportedChange(Exception):
    """Raised for a change element the model cannot apply, naming the changeset it came from."""


@dataclass
class Column:
    name: str
    type: str
    attributes: dict[str, str] = field(default_factory=dict)
    constraints: dict[str, str] = field(default_factory=dict)

    # Whether this column became NOT NULL after it existed, rather than being declared so.
    #
    # It decides how the baseline has to write the constraint back out. Declared inside createTable, a
    # notNullConstraintName is dropped on the floor and PostgreSQL invents a name from the table and
    # column; declared on addColumn, PostgreSQL uses the given name. Two Artemis tables invent the same
    # name, and PostgreSQL resolves that by appending a digit to whichever is created second -- so a
    # column constrained by a later ALTER has to be written back as a later ALTER, or the two names
    # swap tables. Not part of identity(): it says how the column got here, not what it is.
    not_null_via_alter: bool = False

    def identity(self) -> tuple:
        return (self.type, tuple(sorted(self.attributes.items())), tuple(sorted(self.constraints.items())))


@dataclass
class Table:
    name: str
    columns: list[Column] = field(default_factory=list)

    def column(self, name: str) -> Column | None:
        return next((column for column in self.columns if column.name == name), None)

    def require(self, name: str, context: str) -> Column:
        column = self.column(name)
        if column is None:
            raise UnsupportedChange(f"{context}: column {self.name}.{name} does not exist")
        return column


@dataclass
class Index:
    name: str
    table: str
    columns: list[tuple[str, str]]  # (column name, descending marker or "")
    unique: bool = False


@dataclass
class ForeignKey:
    name: str
    table: str
    columns: str
    referenced_table: str
    referenced_columns: str
    on_delete: str = ""
    on_update: str = ""


@dataclass
class UniqueConstraint:
    name: str
    table: str
    columns: str


@dataclass
class LoadData:
    """
    A seed load carried into the baseline, kept whole.

    Every attribute matters -- the table it loads, the separator, the encoding, and the per-column type
    hints that tell Liquibase how to read the CSV -- so the element is recorded as it was written rather
    than summarised and reconstructed from a filename.
    """

    attributes: dict[str, str]
    columns: list[dict[str, str]]

    def identity(self) -> tuple:
        return (tuple(sorted(self.attributes.items())), tuple(tuple(sorted(column.items())) for column in self.columns))


class Schema:
    """The shape of one database engine's schema, and the operations that evolve it."""

    def __init__(self) -> None:
        self.tables: dict[str, Table] = {}
        self.indexes: dict[str, Index] = {}
        self.foreign_keys: dict[str, ForeignKey] = {}
        self.unique_constraints: dict[str, UniqueConstraint] = {}
        self.carried_sql: list[str] = []
        self.load_data: list[LoadData] = []

    # --- helpers ---

    def require_table(self, name: str, context: str) -> Table:
        table = self.tables.get(name)
        if table is None:
            raise UnsupportedChange(f"{context}: table {name} does not exist")
        return table

    @staticmethod
    def _column_from_element(element) -> Column:
        attributes = {key: value for key, value in element.attrib.items() if key not in ("name", "type")}
        constraints: dict[str, str] = {}
        for child in element:
            if child.tag == f"{NAMESPACE}constraints":
                constraints.update(child.attrib)
        return Column(name=element.get("name"), type=element.get("type", ""), attributes=attributes, constraints=constraints)

    # --- operations ---

    def apply(self, element, context: str) -> None:
        tag = element.tag.removeprefix(NAMESPACE)
        handler = getattr(self, f"_{_snake(tag)}", None)
        if handler is None:
            raise UnsupportedChange(f"{context}: no rule for <{tag}>")
        handler(element, context)

    def _create_table(self, element, context: str) -> None:
        name = element.get("tableName")
        table = Table(name)
        for child in element:
            if child.tag == f"{NAMESPACE}column":
                table.columns.append(self._column_from_element(child))
        self.tables[name] = table

    def _drop_table(self, element, context: str) -> None:
        name = element.get("tableName")
        self.tables.pop(name, None)
        # Dropping a table takes everything that hangs off it with it, on both engines.
        self.indexes = {key: value for key, value in self.indexes.items() if value.table != name}
        self.foreign_keys = {key: value for key, value in self.foreign_keys.items() if value.table != name and value.referenced_table != name}
        self.unique_constraints = {key: value for key, value in self.unique_constraints.items() if value.table != name}

    def _rename_table(self, element, context: str) -> None:
        old, new = element.get("oldTableName"), element.get("newTableName")
        table = self.require_table(old, context)
        table.name = new
        self.tables = {new if key == old else key: value for key, value in self.tables.items()}
        for index in self.indexes.values():
            if index.table == old:
                index.table = new
        for key in self.foreign_keys.values():
            if key.table == old:
                key.table = new
            if key.referenced_table == old:
                key.referenced_table = new
        for constraint in self.unique_constraints.values():
            if constraint.table == old:
                constraint.table = new

    def _add_column(self, element, context: str) -> None:
        table = self.require_table(element.get("tableName"), context)
        for child in element:
            if child.tag != f"{NAMESPACE}column":
                continue
            column = self._column_from_element(child)
            existing = table.column(column.name)
            if existing is not None:
                table.columns[table.columns.index(existing)] = column
            else:
                # Appending matches what the database does, so a replayed schema and a folded one agree.
                table.columns.append(column)

    def _drop_column(self, element, context: str) -> None:
        table = self.require_table(element.get("tableName"), context)
        names = [element.get("columnName")] if element.get("columnName") else [child.get("name") for child in element if child.tag == f"{NAMESPACE}column"]
        for name in names:
            column = table.column(name)
            if column is not None:
                table.columns.remove(column)
            # Both databases drop whatever indexed or constrained the column along with it, and a baseline
            # that still declared them would try to index a column it never created.
            self._forget_dependents(table.name, name)

    def _forget_dependents(self, table_name: str, column_name: str) -> None:
        def names(value: str) -> set[str]:
            return {part.strip() for part in (value or "").split(",")}

        self.indexes = {
            key: index for key, index in self.indexes.items() if not (index.table == table_name and any(name == column_name for name, _ in index.columns))
        }
        self.foreign_keys = {
            key: foreign_key
            for key, foreign_key in self.foreign_keys.items()
            if not (
                (foreign_key.table == table_name and column_name in names(foreign_key.columns))
                or (foreign_key.referenced_table == table_name and column_name in names(foreign_key.referenced_columns))
            )
        }
        self.unique_constraints = {
            key: constraint
            for key, constraint in self.unique_constraints.items()
            if not (constraint.table == table_name and column_name in names(constraint.columns))
        }

    def _rename_column(self, element, context: str) -> None:
        table = self.require_table(element.get("tableName"), context)
        old, new = element.get("oldColumnName"), element.get("newColumnName")
        column = table.require(old, context)
        column.name = new
        if element.get("columnDataType"):
            column.type = element.get("columnDataType")

        # Indexes and constraints follow the rename on both databases, so the model has to as well.
        def renamed(value: str) -> str:
            return ", ".join(new if part.strip() == old else part.strip() for part in (value or "").split(","))

        for index in self.indexes.values():
            if index.table == table.name:
                index.columns = [(new if name == old else name, descending) for name, descending in index.columns]
        for foreign_key in self.foreign_keys.values():
            if foreign_key.table == table.name:
                foreign_key.columns = renamed(foreign_key.columns)
            if foreign_key.referenced_table == table.name:
                foreign_key.referenced_columns = renamed(foreign_key.referenced_columns)
        for constraint in self.unique_constraints.values():
            if constraint.table == table.name:
                constraint.columns = renamed(constraint.columns)

    def _modify_data_type(self, element, context: str) -> None:
        table = self.require_table(element.get("tableName"), context)
        table.require(element.get("columnName"), context).type = element.get("newDataType")

    def _add_not_null_constraint(self, element, context: str) -> None:
        table = self.require_table(element.get("tableName"), context)
        column = table.require(element.get("columnName"), context)
        # Only a column that was nullable until now takes its name from this statement; re-asserting
        # NOT NULL on a column that already has it renames nothing.
        if column.constraints.get("nullable") != "false":
            column.not_null_via_alter = True
        column.constraints["nullable"] = "false"
        if element.get("constraintName"):
            column.constraints["notNullConstraintName"] = element.get("constraintName")
        if element.get("columnDataType"):
            column.type = element.get("columnDataType")
        # defaultNullValue is what existing NULL rows are backfilled with. It is not a column default,
        # and treating it as one gives the baseline a DEFAULT the replayed schema does not have.

    def _drop_not_null_constraint(self, element, context: str) -> None:
        table = self.require_table(element.get("tableName"), context)
        column = table.require(element.get("columnName"), context)
        column.constraints.pop("nullable", None)
        column.constraints.pop("notNullConstraintName", None)
        if element.get("columnDataType"):
            column.type = element.get("columnDataType")

    def _add_default_value(self, element, context: str) -> None:
        table = self.require_table(element.get("tableName"), context)
        column = table.require(element.get("columnName"), context)
        for key in ("defaultValue", "defaultValueNumeric", "defaultValueBoolean", "defaultValueComputed", "defaultValueDate"):
            if element.get(key) is not None:
                column.attributes = {name: value for name, value in column.attributes.items() if not name.startswith("defaultValue")}
                column.attributes[key] = element.get(key)

    def _drop_default_value(self, element, context: str) -> None:
        table = self.require_table(element.get("tableName"), context)
        column = table.require(element.get("columnName"), context)
        column.attributes = {name: value for name, value in column.attributes.items() if not name.startswith("defaultValue")}

    def _create_index(self, element, context: str) -> None:
        name = element.get("indexName")
        columns = [(child.get("name"), child.get("descending", "")) for child in element if child.tag == f"{NAMESPACE}column"]
        self.indexes[name] = Index(name, element.get("tableName"), columns, element.get("unique") == "true")

    def _drop_index(self, element, context: str) -> None:
        self.indexes.pop(element.get("indexName"), None)

    def _add_foreign_key_constraint(self, element, context: str) -> None:
        name = element.get("constraintName")
        self.foreign_keys[name] = ForeignKey(
            name=name,
            table=element.get("baseTableName"),
            columns=element.get("baseColumnNames"),
            referenced_table=element.get("referencedTableName"),
            referenced_columns=element.get("referencedColumnNames"),
            on_delete=element.get("onDelete", ""),
            on_update=element.get("onUpdate", ""),
        )

    def _drop_foreign_key_constraint(self, element, context: str) -> None:
        self.foreign_keys.pop(element.get("constraintName"), None)

    def _add_unique_constraint(self, element, context: str) -> None:
        name = element.get("constraintName")
        self.unique_constraints[name] = UniqueConstraint(name, element.get("tableName"), element.get("columnNames"))

    def _drop_unique_constraint(self, element, context: str) -> None:
        name = element.get("constraintName")
        if self.unique_constraints.pop(name, None) is not None:
            return
        column = self._auto_named_unique_column(name, element.get("tableName"))
        if column is not None:
            column.constraints.pop("unique", None)
            column.constraints.pop("uniqueConstraintName", None)

    def _auto_named_unique_column(self, name: str, table_name: str | None) -> Column | None:
        """
        Finds the column whose ``unique="true"`` the database named ``name`` for itself.

        A uniqueness declared inside createTable has no name in the changelog, so each engine invents
        one -- PostgreSQL ``<table>_<column>_key``, MySQL ``<column>`` -- and the changelogs that replace
        those with a named constraint drop them by exactly those spellings.
        """
        table = self.tables.get(table_name) if table_name else None
        if table is None:
            return None
        for column in table.columns:
            if column.constraints.get("unique") == "true" and name in (f"{table_name}_{column.name}_key", column.name):
                return column
        return None

    def _index_like_exists(self, name: str, table_name: str | None) -> bool:
        """Whether anything backed by an index answers to this name: a plain index, a unique constraint, or a column's own unique."""
        if name in self.indexes or name in self.unique_constraints:
            return True
        return self._auto_named_unique_column(name, table_name) is not None

    def _sql(self, element, context: str) -> None:
        statement = (element.text or "").strip()

        if CARRIED_DDL.search(statement):
            # This records a state, not a log. The same statement can reach one engine family from more
            # than one changeset -- a check constraint written once for MySQL and PostgreSQL and again
            # for H2 is the usual case -- and running it twice in the baseline fails on the duplicate.
            if statement not in self.carried_sql:
                self.carried_sql.append(statement)
            return

        if OTHER_DDL.search(statement) and not TRANSIENT_OBJECT.search(statement):
            raise UnsupportedChange(f"{context}: <sql> changes a definition this model cannot carry into a baseline:\n    {statement.splitlines()[0]}")

        # Data migration, or a transient helper. A baseline runs against an empty database, so there is
        # nothing for either to do.

    def _load_data(self, element, context: str) -> None:
        # Seed data gated by a context is refused before it reaches here, so what arrives is a reference
        # table the previous baseline loaded, which the new one has to load too.
        self.load_data.append(
            LoadData(
                attributes=dict(element.attrib),
                columns=[dict(child.attrib) for child in element if child.tag == f"{NAMESPACE}column"],
            )
        )

    # Data migration. A baseline runs against an empty database, so there is nothing for these to move.
    def _update(self, element, context: str) -> None:
        return

    def _delete(self, element, context: str) -> None:
        return

    def _insert(self, element, context: str) -> None:
        return

    def _add_auto_increment(self, element, context: str) -> None:
        table = self.require_table(element.get("tableName"), context)
        table.require(element.get("columnName"), context).attributes["autoIncrement"] = "true"

    # --- preconditions ---

    def satisfies(self, preconditions, engine: str, unknown: set[str]) -> bool:
        """
        Evaluates a changeset's preconditions against this schema.

        A fold has to skip exactly the changesets a real run skips. Several changelogs drop a column from
        a table an earlier changelog already dropped, guarded by a columnExists precondition that fails;
        applying those anyway would fold a schema no database ever has.

        Only the structural preconditions can be answered here. A sqlCheck asks about data, and a fold
        runs against no database at all, so it is taken as satisfied -- which is the answer that applies
        the change, and the converge check is what reports it if that was the wrong guess.
        """
        return all(self._precondition(child, engine, unknown) for child in preconditions)

    def _precondition(self, element, engine: str, unknown: set[str]) -> bool:
        tag = element.tag.removeprefix(NAMESPACE)

        if tag == "and":
            return all(self._precondition(child, engine, unknown) for child in element)
        if tag == "or":
            return any(self._precondition(child, engine, unknown) for child in element)
        if tag == "not":
            # Liquibase's NotPrecondition fails as soon as any nested precondition passes, so this is
            # NOT(OR), not NOT(AND). The two agree for a single child, which is every <not> in the tree
            # today, and disagree the moment someone writes a second one.
            return not any(self._precondition(child, engine, unknown) for child in element)
        if tag == "dbms":
            named = {value.strip().lower() for value in (element.get("type") or "").split(",") if value.strip()}
            families = {"mysql", "mariadb", "h2"} if engine == "mysql" else {"postgresql"}
            return bool(named & families)
        if tag == "tableExists":
            return element.get("tableName") in self.tables
        if tag == "columnExists":
            table = self.tables.get(element.get("tableName"))
            return table is not None and table.column(element.get("columnName")) is not None
        if tag == "indexExists":
            name = element.get("indexName")
            return self._index_like_exists(name, element.get("tableName")) if name else True
        if tag == "foreignKeyConstraintExists":
            name = element.get("foreignKeyName")
            return name in self.foreign_keys if name else True
        if tag == "uniqueConstraintExists":
            name = element.get("constraintName")
            return name in self.unique_constraints if name else True
        if tag in ("sqlCheck", "changeLogPropertyDefined", "runningAs", "changeSetExecuted"):
            return True

        unknown.add(tag)
        return True


def _snake(tag: str) -> str:
    return re.sub(r"(?<!^)(?=[A-Z])", "_", tag).lower()
