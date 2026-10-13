#!/usr/bin/env python3
"""
Unit test for the test requirement handling of the C and C++ programming exercise test templates.

Both templates ship their own copy of `testUtils/AbstractTest.py`. A test can list other tests as
`requirements`; it must only run when every one of them finished with `Result.SUCCESS`, and must be
skipped otherwise (missing requirement, failed requirement, skipped requirement).

The test lives here and not next to the template because everything under
`src/main/resources/templates/<language>/test/` is copied into every test repository that Artemis
creates for a new programming exercise. The template files are only imported, never modified.

Run it from the repository root (standard library only, Linux or macOS):

    python3 supporting_scripts/test_template_test_requirements.py
"""

import importlib
import inspect
import sys
import unittest
from pathlib import Path
from typing import Dict, List, Optional

# Importing the templates must not leave __pycache__ directories inside them.
sys.dont_write_bytecode = True

REPO_ROOT = Path(__file__).resolve().parent.parent
TEMPLATES = REPO_ROOT / "src" / "main" / "resources" / "templates"
TEMPLATE_TEST_DIRS = {
    "c/gcc": TEMPLATES / "c" / "gcc" / "test",
    "c_plus_plus": TEMPLATES / "c_plus_plus" / "test",
}


def load_template(test_dir: Path):
    """
    Imports the template's `testUtils` package in isolation and returns (AbstractTest, TestSuite, Result).
    Every template uses the same top-level package name, so the modules of a previously loaded template
    have to be dropped first.
    """
    for module_name in [name for name in sys.modules if name == "testUtils" or name.startswith("testUtils.")]:
        del sys.modules[module_name]
    sys.path.insert(0, str(test_dir))
    try:
        abstract_test = importlib.import_module("testUtils.AbstractTest")
        test_case = importlib.import_module("testUtils.junit.TestCase")
        test_suite = importlib.import_module("testUtils.junit.TestSuite")
    finally:
        sys.path.remove(str(test_dir))
    return abstract_test.AbstractTest, test_suite.TestSuite, test_case.Result


class TemplateTestRequirements(unittest.TestCase):
    """Base class; one concrete subclass per template is generated below."""

    template_dir: Path

    def setUp(self):
        self.AbstractTest, self.TestSuite, self.Result = load_template(self.template_dir)

        class RecordingTest(self.AbstractTest):
            def __init__(self, name: str, requirements: Optional[List[str]] = None):
                super().__init__(name, requirements)
                self.ran = False

            def _initOutputDirectory(self):
                # The real implementation creates a directory below /tmp.
                pass

            def _run(self):
                self.ran = True

            def _onTimeout(self):
                pass

            def _onFailed(self):
                pass

        self.RecordingTest = RecordingTest
        self.suite = self.TestSuite("requirements")

    def start(self, test, results: Dict[str, object]):
        # The C++ template additionally passes a list of additional suites to `start`.
        if "additionalSuites" in inspect.signature(test.start).parameters:
            test.start(results, self.suite, [])
        else:
            test.start(results, self.suite)

    def assertSkipped(self, test):
        self.assertFalse(test.ran, "the test must not run when a requirement is not fulfilled")
        self.assertEqual(self.Result.SKIPPED, test.case.result)

    def test_runs_without_requirements(self):
        test = self.RecordingTest("TestOutput")
        self.start(test, {})
        self.assertTrue(test.ran)
        self.assertEqual(self.Result.SUCCESS, test.case.result)

    def test_runs_when_all_requirements_succeeded(self):
        test = self.RecordingTest("TestOutput", ["TestCompile", "TestOther"])
        self.start(test, {"TestCompile": self.Result.SUCCESS, "TestOther": self.Result.SUCCESS})
        self.assertTrue(test.ran)
        self.assertEqual(self.Result.SUCCESS, test.case.result)

    def test_skipped_when_requirement_failed(self):
        test = self.RecordingTest("TestOutput", ["TestCompile"])
        self.start(test, {"TestCompile": self.Result.FAILURE})
        self.assertSkipped(test)

    def test_skipped_when_requirement_errored(self):
        test = self.RecordingTest("TestOutput", ["TestCompile"])
        self.start(test, {"TestCompile": self.Result.ERROR})
        self.assertSkipped(test)

    def test_skipped_when_requirement_was_skipped(self):
        test = self.RecordingTest("TestOutput", ["TestCompile"])
        self.start(test, {"TestCompile": self.Result.SKIPPED})
        self.assertSkipped(test)

    def test_skipped_when_requirement_has_no_result_yet(self):
        # Must be skipped and must not raise a KeyError.
        test = self.RecordingTest("TestOutput", ["TestCompile"])
        self.start(test, {})
        self.assertSkipped(test)

    def test_skipped_when_only_one_of_several_requirements_succeeded(self):
        test = self.RecordingTest("TestOutput", ["TestCompile", "TestOther"])
        self.start(test, {"TestCompile": self.Result.SUCCESS, "TestOther": self.Result.FAILURE})
        self.assertSkipped(test)

    def test_skipped_test_is_recorded_in_the_suite(self):
        test = self.RecordingTest("TestOutput", ["TestCompile"])
        self.start(test, {"TestCompile": self.Result.FAILURE})
        self.assertEqual(1, self.suite.tests)
        self.assertEqual(1, self.suite.skipped)


for _label, _directory in TEMPLATE_TEST_DIRS.items():
    _class_name = "TestRequirements_" + _label.replace("/", "_")
    globals()[_class_name] = type(_class_name, (TemplateTestRequirements,), {"template_dir": _directory})

# The abstract base itself must not be collected: it has no template directory.
del TemplateTestRequirements

if __name__ == "__main__":
    unittest.main()
