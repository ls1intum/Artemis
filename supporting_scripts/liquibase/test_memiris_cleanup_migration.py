"""Verify the Memiris contract migration against populated PostgreSQL and MySQL databases.

Run with Docker available: python3 supporting_scripts/liquibase/test_memiris_cleanup_migration.py
"""

import argparse
from pathlib import Path
import shutil
import tempfile
import unittest

from verify_schema import Database, ENGINES, LIQUIBASE_DIR, Liquibase, ensure_drivers, run


CHANGELOG = "20261022231936_changelog.xml"


class MemirisCleanupMigrationTest(unittest.TestCase):
    databases = tuple(ENGINES)

    def test_removes_memory_storage_and_empty_preferences_but_preserves_consent_and_messages(self):
        ensure_drivers()
        with tempfile.TemporaryDirectory(prefix="memiris-cleanup-migration-") as directory:
            workspace = Path(directory)
            shutil.copy2(LIQUIBASE_DIR / "changelog" / CHANGELOG, workspace / CHANGELOG)
            for offset, name in enumerate(self.databases):
                engine = ENGINES[name]
                with self.subTest(database=engine.name), Database(engine, "memiris-cleanup-data", 16280 + offset) as database:
                    self.sql(database, """
                        CREATE TABLE jhi_user (id BIGINT PRIMARY KEY);
                        CREATE TABLE user_ai_preference (
                            user_id BIGINT PRIMARY KEY REFERENCES jhi_user(id),
                            selection_decision VARCHAR(20), selection_decision_date TIMESTAMP,
                            memiris_enabled BOOLEAN NOT NULL DEFAULT TRUE
                        );
                        CREATE TABLE iris_message (
                            id BIGINT PRIMARY KEY, sender VARCHAR(8),
                            accessed_memories JSON, created_memories JSON
                        );
                        CREATE TABLE iris_message_content (
                            id BIGINT PRIMARY KEY, message_id BIGINT REFERENCES iris_message(id),
                            discriminator VARCHAR(20) NOT NULL
                        );
                        CREATE TABLE iris_text_message_content (
                            id BIGINT PRIMARY KEY REFERENCES iris_message_content(id), text_content TEXT NOT NULL
                        );
                        INSERT INTO jhi_user (id) VALUES (1), (2), (3), (4), (5), (6), (7);
                        INSERT INTO user_ai_preference (user_id, selection_decision, selection_decision_date, memiris_enabled) VALUES
                            (1, 'CLOUD_AI', '2026-10-20 12:00:00', FALSE),
                            (2, 'LOCAL_AI', '2026-10-20 13:00:00', TRUE),
                            (3, 'NO_AI', NULL, FALSE),
                            (4, NULL, NULL, FALSE),
                            (5, NULL, NULL, TRUE),
                            (6, NULL, '2026-10-20 12:00:00', FALSE);
                        INSERT INTO iris_message (id, sender, accessed_memories, created_memories) VALUES
                            (1, 'LLM', '[{"id":"m1","title":"Old memory","content":"Accessed content"}]',
                                       '[{"id":"m2","title":"New memory","content":"Created content"}]'),
                            (2, 'USER', NULL, NULL);
                        INSERT INTO iris_message_content (id, message_id, discriminator) VALUES (1, 1, 'text'), (2, 2, 'text');
                        INSERT INTO iris_text_message_content (id, text_content) VALUES (1, 'Answer'), (2, 'Question');
                    """)
                    liquibase = Liquibase(workspace)
                    liquibase.apply(database, CHANGELOG)

                    self.assertEqual(self.sql(database, """
                        SELECT user_id, selection_decision FROM user_ai_preference ORDER BY user_id;
                    """), "1\tCLOUD_AI\n2\tLOCAL_AI\n3\tNO_AI")
                    self.assertEqual(self.sql(database, """
                        SELECT COUNT(*) FROM user_ai_preference
                        WHERE (user_id = 1 AND selection_decision_date = '2026-10-20 12:00:00')
                           OR (user_id = 2 AND selection_decision_date = '2026-10-20 13:00:00');
                    """), "2")
                    self.assertEqual(self.sql(database, "SELECT user_id FROM user_ai_preference WHERE selection_decision_date IS NULL;"), "3")
                    self.assertEqual(self.sql(database, "SELECT COUNT(*) FROM jhi_user;"), "7")
                    self.assertEqual(self.sql(database, "SELECT id, sender FROM iris_message ORDER BY id;"), "1\tLLM\n2\tUSER")
                    self.assertEqual(self.sql(database, """
                        SELECT content.message_id, text_content.text_content
                        FROM iris_message_content content JOIN iris_text_message_content text_content ON text_content.id = content.id
                        ORDER BY content.message_id;
                    """), "1\tAnswer\n2\tQuestion")
                    schema = database.schema()
                    self.assertNotIn("memiris_enabled", schema.tables["user_ai_preference"]["columns"])
                    self.assertNotIn("accessed_memories", schema.tables["iris_message"]["columns"])
                    self.assertNotIn("created_memories", schema.tables["iris_message"]["columns"])
                    self.assertFalse(schema.tables["user_ai_preference"]["columns"]["selection_decision"]["nullable"])

                    # The current application writes only the consent fields; dropping the old default must not break inserts.
                    self.sql(database, """
                        INSERT INTO user_ai_preference (user_id, selection_decision, selection_decision_date)
                        VALUES (7, 'NO_AI', '2026-10-21 12:00:00');
                    """)
                    # A row without a decision is the one state the cleanup removed; the column now refuses it.
                    rejected = self.sql_result(database, "INSERT INTO user_ai_preference (user_id) VALUES (6);")
                    self.assertNotEqual(rejected.returncode, 0)
                    liquibase.apply(database, CHANGELOG)
                    self.assertEqual(self.sql(database, "SELECT COUNT(*) FROM user_ai_preference;"), "4")

                    rollback = liquibase.command(database, CHANGELOG, "rollback-count", command_args=("--count=1",))
                    self.assertNotEqual(rollback.returncode, 0)
                    # The newest changeset refuses first, so the cleanup's own refusal is never reached here.
                    self.assertIn("The required AI selection decision belongs to the Memiris cleanup", rollback.stdout + rollback.stderr)
                    self.assertEqual(self.sql(database, "SELECT COUNT(*) FROM user_ai_preference;"), "4")
                    self.assertEqual(database.schema(), schema)

    def sql(self, database, query):
        result = self.sql_result(database, query)
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout.strip()

    def sql_result(self, database, query):
        if database.engine.name == "postgres":
            client = ["psql", "-U", "artemis", "-d", "Artemis", "-t", "-A", "-F", "\t", "-v", "ON_ERROR_STOP=1"]
        else:
            client = ["mysql", "--default-character-set=utf8mb4", "-u", "root", "-N", "-B", "--database", "Artemis"]
        return run(["docker", "exec", "-i", database.container, *client], input=query)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--database", choices=ENGINES)
    arguments, unittest_arguments = parser.parse_known_args()
    if arguments.database:
        MemirisCleanupMigrationTest.databases = (arguments.database,)
    unittest.main(argv=[__file__, *unittest_arguments], verbosity=2)
