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


def column(sql_type: str) -> dict:
    return {"type": sql_type, "length": ""}


class NormalizeTest(unittest.TestCase):
    def test_strips_one_quote_pair_and_lowercases(self):
        self.assertEqual(normalize_identifier('"Foo"'), "foo")
        self.assertEqual(normalize_identifier("`Bar`"), "bar")
        self.assertEqual(normalize_identifier("[Baz]"), "baz")
        self.assertEqual(normalize_identifier("Plain"), "plain")
        self.assertEqual(normalize_identifier(None), "")

    def test_is_unbounded_text(self):
        for yes in ("text", "TEXT", "longtext", "mediumtext", "clob", " text ", "text()"):
            self.assertTrue(is_unbounded_text(yes), yes)
        for no in ("varchar(255)", "character varying", "tinytext", "bigint", "", None, "integer"):
            self.assertFalse(is_unbounded_text(no), no)


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
        model = schema({"foo": {"columns": {"bar": column("character varying")}}})
        self.assertEqual(evaluate_unbounded_text_columns(model, set(), check_stale=True), [])

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
