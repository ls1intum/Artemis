"""Unit tests for the unbounded-text safeguard in verify_schema.py.

These are pure tests: they drive the scanner and the final-state evaluator with temporary changelogs and
in-memory schema models, so they need neither Docker nor the JDBC drivers. Run them directly:

    python3 supporting_scripts/liquibase/test_unbounded_text_guard.py
"""

import tempfile
import unittest
from pathlib import Path

import db_schema
from verify_schema import (
    evaluate_unbounded_text_columns,
    is_unbounded_text,
    is_unbounded_text_source,
    is_unbounded_varchar,
    normalize_identifier,
    scan_text_introductions,
)

CHANGELOG_HEADER = '<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog">'
CHANGELOG_FOOTER = "</databaseChangeLog>"
FILE = "20260101000000_changelog.xml"


def write_changelog(directory: Path, body: str) -> list[tuple[str, Path]]:
    """Writes one changelog file and returns the (relative-name, path) list the scanner expects."""
    path = directory / FILE
    path.write_text(f"{CHANGELOG_HEADER}\n{body}\n{CHANGELOG_FOOTER}\n", encoding="utf-8")
    return [(FILE, path)]


def schema(tables: dict) -> db_schema.SchemaModel:
    return db_schema.SchemaModel(tables=tables)


def column(sql_type: str, length: str = "") -> dict:
    return {"type": sql_type, "length": length}


class NormalizeTest(unittest.TestCase):
    def test_strips_one_quote_pair_and_lowercases(self):
        self.assertEqual(normalize_identifier('"Foo"'), "foo")
        self.assertEqual(normalize_identifier("`Bar`"), "bar")
        self.assertEqual(normalize_identifier("[Baz]"), "baz")
        self.assertEqual(normalize_identifier("Plain"), "plain")
        self.assertEqual(normalize_identifier(None), "")

    def test_is_unbounded_text(self):
        yes = (
            "text", "TEXT", "longtext", "mediumtext", "clob", " text ", "text()", "ntext", "NTEXT", "nclob",
            "longvarchar", "longnvarchar", "LONGVARCHAR", "long varchar", "LONG VARCHAR", "long  nvarchar",
            "java.sql.Types.CLOB", "java.sql.Types.LONGVARCHAR", "java.sql.Types.LONGNVARCHAR", "java.sql.Types.NCLOB",
        )
        for value in yes:
            self.assertTrue(is_unbounded_text(value), value)
        # tinytext is bounded once MATERIALISED (MySQL TINYTEXT, 255), so the materialised check excludes it.
        no = (
            "varchar(255)", "nvarchar(255)", "character varying", "tinytext", "bigint", "", None, "integer",
            "java.sql.Types.VARCHAR", "my_schema.text", "myschema.clob",
        )
        for value in no:
            self.assertFalse(is_unbounded_text(value), value)

    def test_pg_catalog_text_is_unbounded(self):
        # pg_catalog.text is PostgreSQL's built-in unbounded text; a custom my_schema.text is not.
        self.assertTrue(is_unbounded_text("pg_catalog.text"))
        self.assertFalse(is_unbounded_text("pg_catalog.int4"))
        self.assertFalse(is_unbounded_text("my_schema.text"))

    def test_is_unbounded_varchar(self):
        yes = (
            "varchar", "VARCHAR", "character varying", "nvarchar", "national character varying",
            "pg_catalog.varchar", "java.sql.Types.VARCHAR",  # qualified no-length varchar is still unbounded
        )
        for value in yes:
            self.assertTrue(is_unbounded_varchar(value), value)  # no length -> unbounded
        self.assertTrue(is_unbounded_varchar("character varying", ""))
        self.assertTrue(is_unbounded_varchar("character varying", "0"))
        no = ("varchar(255)", "character varying(255)", "pg_catalog.varchar(255)", "text", "bigint", "", None, "my_schema.varchar")
        for value in no:
            self.assertFalse(is_unbounded_varchar(value), value)
        self.assertFalse(is_unbounded_varchar("character varying", "255"))

    def test_is_unbounded_text_source_also_flags_tinytext(self):
        # A declared tinytext is materialised as unbounded TEXT on PostgreSQL, so the SOURCE check must flag it.
        self.assertTrue(is_unbounded_text_source("tinytext"))
        self.assertTrue(is_unbounded_text_source("TINYTEXT"))
        # Everything the materialised check flags, the source check flags too.
        for value in ("text", "longtext", "clob", "long varchar", "java.sql.Types.CLOB"):
            self.assertTrue(is_unbounded_text_source(value), value)
        # Still bounded: real length-bounded types.
        for value in ("varchar(255)", "nvarchar(255)", "java.sql.Types.VARCHAR", "my_schema.text", None):
            self.assertFalse(is_unbounded_text_source(value), value)


class ScanTextIntroductionsTest(unittest.TestCase):
    def run_scan(self, body, allowed=None, unverifiable=None):
        with tempfile.TemporaryDirectory(prefix="guard-scan-") as directory:
            files = write_changelog(Path(directory), body)
            return scan_text_introductions(files, allowed or {}, unverifiable or {})

    def test_unlisted_add_column_text_fails(self):
        body = '<changeSet id="cs1" author="x"><addColumn tableName="foo"><column name="bar" type="text"/></addColumn></changeSet>'
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("foo.bar", failures[0])
        # Recommends a bounded type, not a CHECK (a CHECK on a text column does not satisfy the check).
        self.assertIn("varchar(n)", failures[0])
        self.assertNotIn("CHECK", failures[0])

    def test_unlisted_create_table_text_fails(self):
        body = '<changeSet id="cs1" author="x"><createTable tableName="foo"><column name="bar" type="text"/></createTable></changeSet>'
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("foo.bar", failures[0])

    def test_listed_add_column_text_passes(self):
        body = '<changeSet id="cs1" author="x"><addColumn tableName="foo"><column name="bar" type="text"/></addColumn></changeSet>'
        allowed = {(FILE, "cs1", "x", "foo", "bar"): "reason"}
        self.assertEqual(self.run_scan(body, allowed=allowed), [])

    def test_bounded_varchar_passes(self):
        body = '<changeSet id="cs1" author="x"><addColumn tableName="foo"><column name="bar" type="varchar(255)"/></addColumn></changeSet>'
        self.assertEqual(self.run_scan(body), [])

    def test_modify_data_type_to_text_flagged(self):
        body = '<changeSet id="cs1" author="x"><modifyDataType tableName="foo" columnName="bar" newDataType="longtext"/></changeSet>'
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("foo.bar", failures[0])

    def test_name_reuse_in_a_new_changeset_is_not_waved_through(self):
        # An allow-list entry for the SAME column but a DIFFERENT changeset must not authorise this one.
        body = '<changeSet id="cs2" author="x"><addColumn tableName="foo"><column name="bar" type="text"/></addColumn></changeSet>'
        allowed = {(FILE, "cs1", "x", "foo", "bar"): "an earlier, different introduction"}
        failures = self.run_scan(body, allowed=allowed)
        self.assertTrue(any("foo.bar" in f and "cs2" in f for f in failures))
        self.assertTrue(any("matches no current unbounded-text introduction" in f for f in failures))

    def test_same_id_different_author_does_not_inherit_approval(self):
        # Full Liquibase identity (id + author): a reused id under a different author is a different changeset.
        body = '<changeSet id="cs1" author="mallory"><addColumn tableName="foo"><column name="bar" type="text"/></addColumn></changeSet>'
        allowed = {(FILE, "cs1", "alice", "foo", "bar"): "alice's introduction"}
        failures = self.run_scan(body, allowed=allowed)
        self.assertTrue(any("mallory" in f and "foo.bar" in f for f in failures))

    def test_rollback_contents_are_ignored(self):
        # A <rollback> describes the reverse operation; its text column and sqlFile must not be flagged.
        body = (
            '<changeSet id="cs1" author="x">'
            '<addColumn tableName="foo"><column name="ok" type="varchar(10)"/></addColumn>'
            '<rollback><addColumn tableName="foo"><column name="bar" type="text"/></addColumn><sqlFile path="x.sql"/></rollback>'
            "</changeSet>"
        )
        self.assertEqual(self.run_scan(body), [])

    def test_property_typed_column_needs_review(self):
        body = '<changeSet id="cs1" author="x"><addColumn tableName="foo"><column name="bar" type="${payloadType}"/></addColumn></changeSet>'
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("UNVERIFIABLE_CHANGESETS", failures[0])
        exempt = {(FILE, "cs1", "x"): ("reviewed: resolves to a bounded type", ())}
        self.assertEqual(self.run_scan(body, unverifiable=exempt), [])

    def test_raw_sql_text_token_flagged_and_empty_attestation_passes(self):
        # The token appears only in a string literal: the author may attest it introduces nothing (()).
        body = '<changeSet id="cs1" author="x"><sql>UPDATE foo SET note = \'see text above\'</sql></changeSet>'
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("UNVERIFIABLE_CHANGESETS", failures[0])
        exempt = {(FILE, "cs1", "x"): ("reviewed: adds no column", ())}
        self.assertEqual(self.run_scan(body, unverifiable=exempt), [])

    def test_raw_sql_without_text_token_passes(self):
        body = '<changeSet id="cs1" author="x"><sql>UPDATE foo SET bar = 1 WHERE baz IS NULL</sql></changeSet>'
        self.assertEqual(self.run_scan(body), [])

    def test_raw_sql_with_property_substitution_needs_review(self):
        # A ${property} in raw SQL could resolve to a text type, so it must require attestation. Otherwise a
        # grandfathered column could be dropped and re-added via `ADD COLUMN description ${payloadType}`
        # unnoticed (the literal-token scan would miss it and the final-schema check accepts the old name).
        body = '<changeSet id="cs1" author="x"><sql>ALTER TABLE course ADD COLUMN description ${payloadType}</sql></changeSet>'
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("UNVERIFIABLE_CHANGESETS", failures[0])
        # With a changeset-scoped attestation of what it introduces plus the matching approval, it passes.
        exempt = {(FILE, "cs1", "x"): ("reviewed: re-adds course.description", (("course", "description"),))}
        allowed = {(FILE, "cs1", "x", "course", "description"): "re-add"}
        self.assertEqual(self.run_scan(body, allowed=allowed, unverifiable=exempt), [])

    def test_attested_opaque_introduction_requires_introduction_pairing(self):
        # Regression: a raw-SQL re-add attested as introducing a column must still carry the changeset-scoped
        # ALLOWED_TEXT_INTRODUCTIONS entry. Without it, it fails; with it, it passes. The final-schema list is
        # intentionally not required here (check_column_types covers it; coupling them would deadlock).
        body = '<changeSet id="cs1" author="x"><sql>ALTER TABLE foo ADD COLUMN bar text</sql></changeSet>'
        exempt = {(FILE, "cs1", "x"): ("re-adds bar", (("foo", "bar"),))}
        failures = self.run_scan(body, unverifiable=exempt)
        self.assertTrue(any("ALLOWED_TEXT_INTRODUCTIONS" in f for f in failures))
        allowed = {(FILE, "cs1", "x", "foo", "bar"): "re-add"}
        self.assertEqual(self.run_scan(body, allowed=allowed, unverifiable=exempt), [])

    def test_sql_file_flagged_unless_exempted(self):
        body = '<changeSet id="cs1" author="x"><sqlFile path="foo.sql"/></changeSet>'
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("UNVERIFIABLE_CHANGESETS", failures[0])
        exempt = {(FILE, "cs1", "x"): ("reviewed", ())}
        self.assertEqual(self.run_scan(body, unverifiable=exempt), [])

    def test_ntext_add_column_is_flagged(self):
        # Reported gap: a grandfathered column dropped and re-added as ntext slipped through. ntext is an
        # unbounded national-charset text type, so re-adding it must be caught at the source like text.
        body = '<changeSet id="cs1" author="x"><addColumn tableName="course"><column name="description" type="ntext"/></addColumn></changeSet>'
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("course.description", failures[0])

    def test_nclob_create_table_is_flagged(self):
        body = '<changeSet id="cs1" author="x"><createTable tableName="foo"><column name="bar" type="nclob"/></createTable></changeSet>'
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("foo.bar", failures[0])

    def test_modify_sql_replacing_bounded_type_with_text_needs_review(self):
        # Reported gap: a column declared varchar(255) but rewritten to TEXT via <modifySql> must not pass.
        body = (
            '<changeSet id="cs1" author="x">'
            '<createTable tableName="foo"><column name="bar" type="varchar(255)"/></createTable>'
            '<modifySql dbms="postgresql,mysql"><replace replace="VARCHAR(255)" with="TEXT"/></modifySql>'
            "</changeSet>"
        )
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("UNVERIFIABLE_CHANGESETS", failures[0])
        exempt = {(FILE, "cs1", "x"): ("reviewed: modifySql keeps a bounded type", ())}
        self.assertEqual(self.run_scan(body, unverifiable=exempt), [])

    def test_sql_after_nested_comment_is_inspected(self):
        # Reported gap: SQL written after a nested <comment> lives in the comment's tail, which element.text
        # misses. The CREATE TABLE ... TEXT must still be seen.
        body = (
            '<changeSet id="cs1" author="x">'
            "<sql><comment>Manual schema test</comment>CREATE TABLE zz (payload TEXT)</sql>"
            "</changeSet>"
        )
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("UNVERIFIABLE_CHANGESETS", failures[0])

    def test_sql_comment_prose_alone_does_not_trip(self):
        # A <comment>'s own text is excluded, so the word "text" in prose does not force review when the SQL
        # itself introduces nothing.
        body = '<changeSet id="cs1" author="x"><sql><comment>free text migration</comment>UPDATE foo SET bar = 1</sql></changeSet>'
        self.assertEqual(self.run_scan(body), [])

    def test_longvarchar_add_column_is_flagged(self):
        body = '<changeSet id="cs1" author="x"><addColumn tableName="foo"><column name="bar" type="longvarchar"/></addColumn></changeSet>'
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("foo.bar", failures[0])

    def test_java_sql_types_clob_is_flagged(self):
        body = '<changeSet id="cs1" author="x"><addColumn tableName="foo"><column name="bar" type="java.sql.Types.CLOB"/></addColumn></changeSet>'
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("foo.bar", failures[0])

    def test_rename_column_to_text_is_flagged(self):
        # renameColumn carries columnDataType; some databases re-emit the type, so an unbounded one must be caught.
        body = '<changeSet id="cs1" author="x"><renameColumn tableName="foo" oldColumnName="a" newColumnName="bar" columnDataType="text"/></changeSet>'
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("foo.bar", failures[0])

    def test_merge_columns_to_text_is_flagged(self):
        # mergeColumns creates a new column finalColumnName of finalColumnType.
        body = '<changeSet id="cs1" author="x"><mergeColumns tableName="foo" column1Name="a" column2Name="b" finalColumnName="bar" finalColumnType="longtext" joinString=" "/></changeSet>'
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("foo.bar", failures[0])

    def test_merge_columns_bounded_type_passes(self):
        body = '<changeSet id="cs1" author="x"><mergeColumns tableName="foo" column1Name="a" column2Name="b" finalColumnName="bar" finalColumnType="varchar(255)" joinString=" "/></changeSet>'
        self.assertEqual(self.run_scan(body), [])

    def test_tinytext_source_declaration_is_flagged(self):
        # A declared tinytext materialises as unbounded TEXT on PostgreSQL, so re-adding a grandfathered column
        # as tinytext must be caught at the source (the materialised MySQL TINYTEXT stays bounded separately).
        body = '<changeSet id="cs1" author="x"><addColumn tableName="course"><column name="description" type="tinytext"/></addColumn></changeSet>'
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("course.description", failures[0])

    def test_spaced_long_varchar_add_column_is_flagged(self):
        body = '<changeSet id="cs1" author="x"><addColumn tableName="foo"><column name="bar" type="long varchar"/></addColumn></changeSet>'
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("foo.bar", failures[0])

    def test_no_length_varchar_source_declaration_is_flagged(self):
        # PostgreSQL 'varchar'/'character varying' with no length is unbounded; a declared no-length varchar must
        # be flagged, while varchar(255) stays bounded (test_bounded_varchar_passes).
        body = '<changeSet id="cs1" author="x"><addColumn tableName="foo"><column name="bar" type="varchar"/></addColumn></changeSet>'
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("foo.bar", failures[0])

    def test_pg_catalog_text_source_declaration_is_flagged(self):
        body = '<changeSet id="cs1" author="x"><addColumn tableName="course"><column name="description" type="pg_catalog.text"/></addColumn></changeSet>'
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("course.description", failures[0])

    def test_raw_sql_no_length_varchar_reintroduction_needs_review(self):
        # Raw SQL re-adding a grandfathered column as a no-length (unbounded) varchar must require attestation.
        body = ('<changeSet id="cs1" author="x"><sql>ALTER TABLE course DROP COLUMN description; '
                "ALTER TABLE course ADD COLUMN description varchar;</sql></changeSet>")
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("UNVERIFIABLE_CHANGESETS", failures[0])
        exempt = {(FILE, "cs1", "x"): ("reviewed: bounded in practice", ())}
        self.assertEqual(self.run_scan(body, unverifiable=exempt), [])

    def test_raw_sql_bounded_varchar_passes(self):
        body = '<changeSet id="cs1" author="x"><sql>ALTER TABLE foo ADD COLUMN bar varchar(255)</sql></changeSet>'
        self.assertEqual(self.run_scan(body), [])

    def test_modify_sql_dropping_varchar_length_needs_review(self):
        # <modifySql> rewriting varchar(255) to a bare varchar removes the bound; must require attestation.
        body = (
            '<changeSet id="cs1" author="x">'
            '<createTable tableName="foo"><column name="bar" type="varchar(255)"/></createTable>'
            '<modifySql dbms="postgresql"><replace replace="VARCHAR(255)" with="VARCHAR"/></modifySql>'
            "</changeSet>"
        )
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("UNVERIFIABLE_CHANGESETS", failures[0])

    def test_pg_catalog_varchar_source_declaration_is_flagged(self):
        body = '<changeSet id="cs1" author="x"><addColumn tableName="course"><column name="description" type="pg_catalog.varchar"/></addColumn></changeSet>'
        failures = self.run_scan(body)
        self.assertEqual(len(failures), 1)
        self.assertIn("course.description", failures[0])

    def test_pg_catalog_varchar_with_length_passes(self):
        body = '<changeSet id="cs1" author="x"><addColumn tableName="foo"><column name="bar" type="pg_catalog.varchar(255)"/></addColumn></changeSet>'
        self.assertEqual(self.run_scan(body), [])

    def test_stale_allowed_entry_is_reported(self):
        body = '<changeSet id="cs1" author="x"><addColumn tableName="foo"><column name="bar" type="varchar(10)"/></addColumn></changeSet>'
        allowed = {(FILE, "cs1", "x", "foo", "bar"): "no longer text"}
        failures = self.run_scan(body, allowed=allowed)
        self.assertEqual(len(failures), 1)
        self.assertIn("matches no current unbounded-text introduction", failures[0])

    def test_stale_unverifiable_entry_is_reported(self):
        body = '<changeSet id="cs1" author="x"><addColumn tableName="foo"><column name="bar" type="varchar(10)"/></addColumn></changeSet>'
        unverifiable = {(FILE, "cs1", "x"): ("no longer opaque", ())}
        failures = self.run_scan(body, unverifiable=unverifiable)
        self.assertEqual(len(failures), 1)
        self.assertIn("matches no current reviewed changeset", failures[0])


class EvaluateColumnTypesTest(unittest.TestCase):
    def test_unbounded_column_not_grandfathered_fails(self):
        model = schema({"foo": {"columns": {"bar": column("text")}}})
        failures = evaluate_unbounded_text_columns(model, set(), check_stale=True)
        self.assertEqual(len(failures), 1)
        self.assertIn("foo.bar", failures[0])
        self.assertIn("varchar(n)", failures[0])
        self.assertNotIn("CHECK", failures[0])

    def test_grandfathered_column_passes(self):
        model = schema({"foo": {"columns": {"bar": column("text")}}})
        self.assertEqual(evaluate_unbounded_text_columns(model, {("foo", "bar")}, check_stale=True), [])

    def test_bounded_column_ignored(self):
        model = schema({"foo": {"columns": {"bar": column("character varying", "255")}}})
        self.assertEqual(evaluate_unbounded_text_columns(model, set(), check_stale=True), [])

    def test_no_length_character_varying_is_unbounded(self):
        # A materialised 'character varying' with no length limit is unbounded and must be flagged.
        model = schema({"foo": {"columns": {"bar": column("character varying", "")}}})
        failures = evaluate_unbounded_text_columns(model, set(), check_stale=True)
        self.assertEqual(len(failures), 1)
        self.assertIn("foo.bar", failures[0])

    def test_pg_catalog_text_materialised_is_unbounded(self):
        model = schema({"foo": {"columns": {"bar": column("pg_catalog.text")}}})
        failures = evaluate_unbounded_text_columns(model, set(), check_stale=True)
        self.assertEqual(len(failures), 1)
        self.assertIn("foo.bar", failures[0])

    def test_materialized_tinytext_is_bounded(self):
        # A materialised MySQL TINYTEXT is bounded (255), so the final-schema check must not flag it, even though
        # the source scan treats a declared tinytext as unbounded.
        model = schema({"foo": {"columns": {"bar": column("tinytext")}}})
        self.assertEqual(evaluate_unbounded_text_columns(model, set(), check_stale=False), [])

    def test_materialized_spaced_long_varchar_is_unbounded(self):
        model = schema({"foo": {"columns": {"bar": column("long varchar")}}})
        failures = evaluate_unbounded_text_columns(model, set(), check_stale=True)
        self.assertEqual(len(failures), 1)
        self.assertIn("foo.bar", failures[0])

    def test_liquibase_bookkeeping_tables_skipped(self):
        model = schema({"databasechangelog": {"columns": {"description": column("text")}}})
        self.assertEqual(evaluate_unbounded_text_columns(model, set(), check_stale=True), [])

    def test_stale_grandfather_entry_reported_when_checked(self):
        model = schema({"foo": {"columns": {"bar": column("varchar(10)")}}})
        failures = evaluate_unbounded_text_columns(model, {("foo", "bar")}, check_stale=True)
        self.assertEqual(len(failures), 1)
        self.assertIn("no longer an unbounded-text column", failures[0])

    def test_stale_grandfather_entry_suppressed_when_not_checked(self):
        # On MySQL (check_stale=False) an enum-as-text column grandfathered for PostgreSQL must not be
        # reported as stale just because MySQL stores it as a bounded enum.
        model = schema({"foo": {"columns": {"bar": column("enum('a','b')")}}})
        self.assertEqual(evaluate_unbounded_text_columns(model, {("foo", "bar")}, check_stale=False), [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
