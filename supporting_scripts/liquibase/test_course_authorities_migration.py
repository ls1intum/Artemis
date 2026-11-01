"""Verify teaching-authority removal against populated PostgreSQL and MySQL databases.

Run with Docker available: python3 supporting_scripts/liquibase/test_course_authorities_migration.py
"""

import argparse
from pathlib import Path
import shutil
import tempfile
import unittest

from verify_schema import Database, ENGINES, LIQUIBASE_DIR, Liquibase, ensure_drivers, run


CHANGELOG = "20261024201101_changelog.xml"


class CourseAuthoritiesMigrationTest(unittest.TestCase):
    databases = tuple(ENGINES)

    def test_preserves_login_and_course_roles_when_removing_teaching_authorities(self):
        ensure_drivers()
        with tempfile.TemporaryDirectory(prefix="course-authorities-migration-") as directory:
            workspace = Path(directory)
            shutil.copy2(LIQUIBASE_DIR / "changelog" / CHANGELOG, workspace / CHANGELOG)
            for offset, name in enumerate(self.databases):
                engine = ENGINES[name]
                with self.subTest(database=engine.name), Database(engine, "course-authorities-data", 16380 + offset) as database:
                    self.sql(database, """
                        CREATE TABLE jhi_user (id BIGINT PRIMARY KEY);
                        CREATE TABLE jhi_authority (name VARCHAR(50) PRIMARY KEY);
                        CREATE TABLE jhi_user_authority (
                            user_id BIGINT REFERENCES jhi_user(id),
                            authority_name VARCHAR(50) REFERENCES jhi_authority(name),
                            PRIMARY KEY (user_id, authority_name)
                        );
                        CREATE TABLE user_course_role (
                            user_id BIGINT REFERENCES jhi_user(id), course_id BIGINT, course_role VARCHAR(20),
                            PRIMARY KEY (user_id, course_id, course_role)
                        );
                        INSERT INTO jhi_user (id) VALUES (1), (2), (3), (4), (5), (6), (7), (8);
                        INSERT INTO jhi_authority (name) VALUES
                            ('ROLE_USER'), ('ROLE_TA'), ('ROLE_EDITOR'), ('ROLE_INSTRUCTOR'), ('ROLE_ADMIN'), ('ROLE_SUPER_ADMIN');
                        INSERT INTO jhi_user_authority (user_id, authority_name) VALUES
                            (1, 'ROLE_TA'), (2, 'ROLE_EDITOR'), (3, 'ROLE_INSTRUCTOR'),
                            (4, 'ROLE_USER'), (4, 'ROLE_TA'), (4, 'ROLE_EDITOR'),
                            (5, 'ROLE_ADMIN'), (5, 'ROLE_INSTRUCTOR'), (7, 'ROLE_SUPER_ADMIN'),
                            (8, 'ROLE_TA'), (8, 'ROLE_EDITOR'), (8, 'ROLE_INSTRUCTOR');
                        INSERT INTO user_course_role (user_id, course_id, course_role) VALUES
                            (1, 10, 'TEACHING_ASSISTANT'), (2, 10, 'EDITOR'), (3, 10, 'INSTRUCTOR'), (4, 20, 'STUDENT');
                    """)
                    liquibase = Liquibase(workspace)
                    liquibase.apply(database, CHANGELOG)

                    # Teaching authorities used to imply USER, even if it was not assigned explicitly.
                    self.assertEqual(self.sql(database, """
                        SELECT user_id, authority_name FROM jhi_user_authority ORDER BY user_id, authority_name;
                    """), "1\tROLE_USER\n2\tROLE_USER\n3\tROLE_USER\n4\tROLE_USER\n5\tROLE_ADMIN\n5\tROLE_USER\n7\tROLE_SUPER_ADMIN\n8\tROLE_USER")
                    self.assertEqual(self.sql(database, "SELECT name FROM jhi_authority ORDER BY name;"),
                                     "ROLE_ADMIN\nROLE_SUPER_ADMIN\nROLE_USER")
                    self.assertEqual(self.sql(database, "SELECT COUNT(*) FROM jhi_user;"), "8")
                    self.assertEqual(self.sql(database, """
                        SELECT user_id, course_id, course_role FROM user_course_role ORDER BY user_id;
                    """), "1\t10\tTEACHING_ASSISTANT\n2\t10\tEDITOR\n3\t10\tINSTRUCTOR\n4\t20\tSTUDENT")

                    liquibase.apply(database, CHANGELOG)
                    self.assertEqual(self.sql(database, "SELECT COUNT(*) FROM jhi_user_authority;"), "8")

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
        CourseAuthoritiesMigrationTest.databases = (arguments.database,)
    unittest.main(argv=[__file__, *unittest_arguments], verbosity=2)
