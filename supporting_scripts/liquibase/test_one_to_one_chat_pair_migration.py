"""Verify the one-to-one chat pair migration against populated PostgreSQL and MySQL databases.

Run with Docker available: python3 supporting_scripts/liquibase/test_one_to_one_chat_pair_migration.py
"""

import argparse
from pathlib import Path
import shutil
import tempfile
import unittest

from verify_schema import Database, ENGINES, LIQUIBASE_DIR, Liquibase, ensure_drivers, run


CHANGELOG = "20261024132235_changelog.xml"


class OneToOneChatPairMigrationTest(unittest.TestCase):
    databases = tuple(ENGINES)

    def test_merges_duplicate_chats_and_enforces_one_chat_per_pair(self):
        ensure_drivers()
        with tempfile.TemporaryDirectory(prefix="one-to-one-chat-pair-migration-") as directory:
            workspace = Path(directory)
            shutil.copy2(LIQUIBASE_DIR / "changelog" / CHANGELOG, workspace / CHANGELOG)
            for offset, name in enumerate(self.databases):
                engine = ENGINES[name]
                with self.subTest(database=engine.name), Database(engine, "one-to-one-chat-pair-data", 16300 + offset) as database:
                    self.sql(database, """
                        CREATE TABLE conversation (
                            id BIGINT PRIMARY KEY, course_id BIGINT, discriminator VARCHAR(31) NOT NULL, last_message_date TIMESTAMP NULL
                        );
                        CREATE TABLE conversation_participant (
                            id BIGINT PRIMARY KEY, conversation_id BIGINT, user_id BIGINT
                        );
                        CREATE UNIQUE INDEX conversation_participant_uq ON conversation_participant (conversation_id, user_id);
                        CREATE TABLE post (id BIGINT PRIMARY KEY, conversation_id BIGINT);

                        -- Course 1, users 10 and 20: three chats created by concurrent requests. The oldest has no message yet,
                        -- the two newer ones carry the messages. Written by either user first, so the participants are in both orders.
                        INSERT INTO conversation (id, course_id, discriminator, last_message_date) VALUES
                            (1, 1, 'O', NULL), (2, 1, 'O', '2026-01-02 10:00:00'), (3, 1, 'O', '2026-01-03 10:00:00');
                        INSERT INTO conversation_participant (id, conversation_id, user_id) VALUES
                            (1, 1, 10), (2, 1, 20), (3, 2, 20), (4, 2, 10), (5, 3, 10), (6, 3, 20);
                        INSERT INTO post (id, conversation_id) VALUES (1, 2), (2, 2), (3, 3);

                        -- Course 1, users 10 and 30: a pair with a single chat stays as it is.
                        INSERT INTO conversation (id, course_id, discriminator, last_message_date) VALUES (4, 1, 'O', '2026-02-01 10:00:00');
                        INSERT INTO conversation_participant (id, conversation_id, user_id) VALUES (7, 4, 10), (8, 4, 30);
                        INSERT INTO post (id, conversation_id) VALUES (4, 4);

                        -- Course 2, users 10 and 20: the same pair in another course is a different chat.
                        INSERT INTO conversation (id, course_id, discriminator, last_message_date) VALUES (5, 2, 'O', NULL);
                        INSERT INTO conversation_participant (id, conversation_id, user_id) VALUES (9, 5, 10), (10, 5, 20);

                        -- A chat whose other user was deleted, one without participants, a group chat and a channel keep no key.
                        INSERT INTO conversation (id, course_id, discriminator, last_message_date) VALUES
                            (6, 1, 'O', '2026-03-01 10:00:00'), (7, 1, 'O', NULL), (8, 1, 'G', NULL), (9, 1, 'C', NULL);
                        INSERT INTO conversation_participant (id, conversation_id, user_id) VALUES (11, 6, 10), (12, 8, 10), (13, 8, 20);
                        INSERT INTO post (id, conversation_id) VALUES (5, 6);

                        -- Course 1, users 40 and 50: the oldest chat has the later message than the duplicate, which has none.
                        INSERT INTO conversation (id, course_id, discriminator, last_message_date) VALUES
                            (10, 1, 'O', '2026-04-01 10:00:00'), (11, 1, 'O', NULL);
                        INSERT INTO conversation_participant (id, conversation_id, user_id) VALUES (14, 10, 40), (15, 10, 50), (16, 11, 50), (17, 11, 40);
                    """)
                    liquibase = Liquibase(workspace)
                    liquibase.apply(database, CHANGELOG)

                    self.assertEqual(self.sql(database, "SELECT id FROM conversation ORDER BY id;"), "1\n4\n5\n6\n7\n8\n9\n10")
                    self.assertEqual(self.sql(database, "SELECT id, participant_pair_key FROM conversation WHERE participant_pair_key IS NOT NULL ORDER BY id;"),
                                     "1\t10-20\n4\t10-30\n5\t10-20\n10\t40-50")
                    # The merged chat carries every message and the latest message date of the three.
                    self.assertEqual(self.sql(database, "SELECT conversation_id, COUNT(*) FROM post GROUP BY conversation_id ORDER BY conversation_id;"),
                                     "1\t3\n4\t1\n6\t1")
                    self.assertEqual(self.sql(database, "SELECT id, last_message_date FROM conversation WHERE id IN (1, 10) ORDER BY id;"),
                                     "1\t2026-01-03 10:00:00\n10\t2026-04-01 10:00:00")
                    self.assertEqual(self.sql(database, "SELECT conversation_id, COUNT(*) FROM conversation_participant GROUP BY conversation_id ORDER BY conversation_id;"),
                                     "1\t2\n4\t2\n5\t2\n6\t1\n8\t2\n10\t2")
                    self.assertEqual(self.sql(database, "SELECT COUNT(*) FROM post WHERE conversation_id NOT IN (SELECT id FROM conversation);"), "0")

                    # The migration leaves no helper table behind.
                    self.assertNotIn("tmp_one_to_one_chat_duplicate", database.schema().tables)

                    # A second chat for a pair is refused from now on, in any order of the users, while a chat written without a key, as the
                    # previous version of the application does, is still accepted.
                    refused = self.sql_result(database, "INSERT INTO conversation (id, course_id, discriminator, participant_pair_key) VALUES (20, 1, 'O', '10-20');")
                    self.assertNotEqual(refused.returncode, 0)
                    self.sql(database, """
                        INSERT INTO conversation (id, course_id, discriminator) VALUES (21, 1, 'O'), (22, 1, 'O');
                        INSERT INTO conversation (id, course_id, discriminator, participant_pair_key) VALUES (23, 3, 'O', '10-20');
                    """)

                    liquibase.apply(database, CHANGELOG)
                    self.assertEqual(self.sql(database, "SELECT COUNT(*) FROM conversation;"), "11")

                    # The unique index can be rolled back; the merge of the duplicates cannot.
                    rollback_index = liquibase.command(database, CHANGELOG, "rollback-count", command_args=("--count=1",))
                    self.assertEqual(rollback_index.returncode, 0, rollback_index.stdout + rollback_index.stderr)
                    rollback_merge = liquibase.command(database, CHANGELOG, "rollback-count", command_args=("--count=1",))
                    self.assertNotEqual(rollback_merge.returncode, 0)
                    self.assertIn("merged duplicate one-to-one chats cannot be split again", rollback_merge.stdout + rollback_merge.stderr)
                    self.assertEqual(self.sql(database, "SELECT COUNT(*) FROM conversation;"), "11")

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
        OneToOneChatPairMigrationTest.databases = (arguments.database,)
    unittest.main(argv=[__file__, *unittest_arguments], verbosity=2)
