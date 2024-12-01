import csv
import subprocess
from pathlib import Path
from typing import Iterable

import mysql.connector
import pytest
from mysql.connector.abstracts import MySQLConnectionAbstract
from pytest import Item, Collector

assignment_path = Path("../solution")


def pytest_collect_directory(path, parent):
    return QueryTestDirectory.from_parent(parent=parent, path=path)


class QueryTestDirectory(pytest.Directory):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.setup_script_file = self.path / "setup.sql"
        self.assignment_query_path = assignment_path / f"{self.name}.sql"


    def collect(self) -> Iterable[Item | Collector]:
        for child in self.path.iterdir():
            child: Path
            if child.is_dir():
                yield SQLTestItem.from_parent(parent=self, name=f"{self.name}_{child.name}", item_dir=child)


class SQLTestItem(pytest.Item):
    connection: MySQLConnectionAbstract
    parent: QueryTestDirectory
    def __init__(self, *, item_dir: Path, **kwargs):
        super().__init__(**kwargs)
        self.data_script_file = item_dir / "data.sql"
        self.query_result_file = item_dir / "query_result.csv"
        self.test_query_file = item_dir / "test.sql"
        self.test_result_file = item_dir / "test_result.csv"


    def setup(self):
        execute_script(self.parent.setup_script_file)
        self.connection = mysql.connector.connect(host="localhost", user="root", password="", database="test", autocommit=False, raise_on_warnings=True)


    def runtest(self) -> None:
        execute_script(self.data_script_file)

        assignment_query_result = self.execute_query(self.parent.assignment_query_path)

        if self.query_result_file.exists():
            expected_result = load_result(self.query_result_file)
            assert expected_result == assignment_query_result

        if self.test_result_file.exists() and self.test_query_file.exists():
            expected_result = load_result(self.test_result_file)
            test_result = self.execute_query(self.test_query_file)
            assert expected_result == test_result


    def teardown(self):
        with self.connection.cursor() as cursor:
            cursor.execute("DROP DATABASE IF EXISTS test")
        self.connection.commit()
        self.connection.close()


    def execute_query(self, filename: Path) -> list[tuple]:
        with open(filename) as f:
            query = f.read()

        with self.connection.cursor(raw=True) as cursor:
            cursor.execute(query)
            return [tuple(value.decode() if value is not None else "NULL" for value in row) for row in cursor]


def execute_script(filename: Path) -> None:
    # cursor.execute(..., mutli=True) is not reliable enough
    subprocess.run(["mysql", "--host=127.0.0.1", "--user=root"], stdin=filename.open(), check=True)


def load_result(filepath: Path) -> list[tuple]:
    with open(filepath) as f:
        reader = csv.reader(f, strict=True)
        return [tuple(row) for row in reader]
