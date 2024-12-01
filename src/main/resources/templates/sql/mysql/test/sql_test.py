import csv
import unittest
from pathlib import Path

import mysql.connector
from mysql.connector.abstracts import MySQLCursorAbstract

assignment_path = Path("../solution")


class MyTestCase(unittest.TestCase):
    def setUp(self):
        self.connection = mysql.connector.connect(host="localhost", user="root", password="", allow_local_infile=True)
        with self.connection.cursor() as cur:
            cur.execute("DROP DATABASE IF EXISTS db")
            cur.execute("CREATE DATABASE db")
            cur.execute("USE db")

    def tearDown(self):
        with self.connection.cursor() as cur:
            cur.execute("DROP DATABASE db")
        self.connection.close()

    def test_something(self):
        with self.connection.cursor() as cur:
            execute_file(cur, "test1/schema.sql")

            load_data(cur, "test1/01/data.csv")

            execute_file(cur, assignment_path / "query1.sql")

            with open("test1/01/result.csv") as f:
                reader = csv.reader(f, quoting=csv.QUOTE_NONNUMERIC, strict=True)
                result = [tuple(row) for row in reader]

            self.assertListEqual(result, cur.fetchall())

        self.connection.commit()


def execute_file(cur: MySQLCursorAbstract, filename: Path | str):
    with open(filename) as f:
        query = f.read()
    cur.execute(query)


def load_data(cur: MySQLCursorAbstract, filename: str):
    cur.execute("LOAD DATA LOCAL INFILE %s INTO TABLE test FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '\"'",
                (filename,))


if __name__ == '__main__':
    unittest.main()
