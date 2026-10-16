"""Exercise reset-key invalidation against populated PostgreSQL and MySQL databases.

Run with Docker available: python3 supporting_scripts/liquibase/test_reset_key_migration.py
Uses the same pinned Liquibase image and JDBC drivers as verify_schema.py.
"""

import argparse
from pathlib import Path
import shutil
import tempfile
import unittest

from verify_schema import Database, ENGINES, LIQUIBASE_DIR, Liquibase, ensure_drivers, run


class ResetKeyMigrationTest(unittest.TestCase):
    databases = tuple(ENGINES)

    def test_invalidates_legacy_resets_without_losing_activation_keys_or_accounts(self):
        ensure_drivers()
        with tempfile.TemporaryDirectory(prefix="reset-key-migration-") as directory:
            workspace = Path(directory)
            changelog = "20260830210000_changelog.xml"
            shutil.copy2(LIQUIBASE_DIR / "changelog" / changelog, workspace / changelog)
            for offset, name in enumerate(self.databases):
                engine = ENGINES[name]
                with self.subTest(database=engine.name), Database(engine, "reset-key-data", 15800 + offset) as database:
                    self.sql(database, """
                        CREATE TABLE jhi_user (id BIGINT PRIMARY KEY);
                        CREATE TABLE user_recovery_key (
                            user_id BIGINT PRIMARY KEY REFERENCES jhi_user(id),
                            activation_key VARCHAR(20), reset_key VARCHAR(20), reset_date TIMESTAMP
                        );
                        INSERT INTO jhi_user (id) VALUES (1), (2), (3), (4), (5), (6), (7);
                        INSERT INTO user_recovery_key (user_id, activation_key, reset_key, reset_date) VALUES
                            (1, NULL, 'reset-only', '2026-08-29 12:00:00'),
                            (2, 'activation-only', NULL, NULL),
                            (3, 'activation-both', 'reset-both', '2026-08-29 12:00:00'),
                            (4, NULL, NULL, '2026-08-29 12:00:00'),
                            (5, NULL, NULL, NULL),
                            (6, 'activation-stale', NULL, '2026-08-29 12:00:00');
                    """)
                    Liquibase(workspace).apply(database, changelog)
                    # Test SQL NULL explicitly: the string 'null' must fail this check.
                    self.assertEqual(self.sql(database, """
                        SELECT COUNT(*) FROM user_recovery_key
                        WHERE reset_key_id IS NOT NULL OR reset_key_hash IS NOT NULL;
                    """), "0")
                    self.assertEqual(self.sql(database, """
                        SELECT COUNT(*) FROM user_recovery_key WHERE reset_date IS NOT NULL;
                    """), "0")
                    self.assertEqual(self.sql(database, """
                        SELECT user_id, activation_key FROM user_recovery_key ORDER BY user_id;
                    """), "2\tactivation-only\n3\tactivation-both\n6\tactivation-stale")
                    self.assertEqual(self.sql(database, "SELECT COUNT(*) FROM jhi_user;"), "7")

    def sql(self, database, query):
        if database.engine.name == "postgres":
            client = ["psql", "-U", "artemis", "-d", "Artemis", "-t", "-A", "-F", "\t", "-v", "ON_ERROR_STOP=1"]
        else:
            client = ["mysql", "--default-character-set=utf8mb4", "-u", "root", "-N", "-B", "--database", "Artemis"]
        result = run(["docker", "exec", "-i", database.container, *client], input=query)
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout.strip()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--database", choices=ENGINES)
    arguments, unittest_arguments = parser.parse_known_args()
    if arguments.database:
        ResetKeyMigrationTest.databases = (arguments.database,)
    unittest.main(argv=[__file__, *unittest_arguments], verbosity=2)
