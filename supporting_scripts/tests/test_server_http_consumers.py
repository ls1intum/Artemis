"""Keep real cross-module HTTP consumers that intentionally import no controller."""

import importlib.util
from pathlib import Path
import os
import unittest
from unittest.mock import patch


REPOSITORY = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("server_selector", REPOSITORY / "supporting_scripts/select_server_test_modules.py")
selector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(selector)


class RepositoryHttpConsumersTest(unittest.TestCase):
    def test_current_generic_json_and_uri_consumers_are_selected(self):
        original_git = selector.git
        cases = {
            "notification/web/CourseNotificationResource.java": (
                "lecture.AttachmentVideoUnitIntegrationTest", "text.TextExerciseIntegrationTest"),
            "assessment/web/ResultResource.java": ("programming.ProgrammingExerciseResultJenkinsIntegrationTest",),
            "text/web/TextExerciseResource.java": ("iris.IrisChatMessageIntegrationTest",),
        }
        previous = Path.cwd()
        self.addCleanup(os.chdir, previous)
        os.chdir(REPOSITORY)
        for relative, consumers in cases.items():
            with self.subTest(controller=relative):
                seed = selector.MODULE_ROOTS[0] + relative

                def changed_path(*arguments, **kwargs):
                    if arguments[0] == "diff":
                        return (seed + "\0").encode()
                    return original_git(*arguments, **kwargs)

                with patch.object(selector, "git", changed_path):
                    plan = selector.select_plan("HEAD")
                self.assertEqual(plan["mode"], "selected", plan)
                for consumer in consumers:
                    self.assertIn(selector.PACKAGE + "." + consumer, plan["tests"])
