import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from analyze_java_files import analyze_java_files


class ConstructorAnalysisTest(unittest.TestCase):
    def analyze(self, source):
        with tempfile.TemporaryDirectory() as directory:
            Path(directory, "ExampleService.java").write_text(source, encoding="utf-8")
            return analyze_java_files(directory, max_params=2)[1]

    def test_nested_value_constructor_is_not_a_bean_dependency(self):
        self.assertEqual([], self.analyze("""
            @Service public class ExampleService {
                public ExampleService(Dependency one) {}
                public record Snapshot(String a, String b, String c) {
                    public Snapshot(String a, String b, String c) {}
                }
            }
        """))

    def test_actual_bean_constructor_still_exceeds_the_limit(self):
        violations = self.analyze("""
            @Service public class ExampleService {
                public ExampleService(First one, Second two, Third three) {}
            }
        """)
        self.assertEqual(1, len(violations))
        self.assertEqual(3, violations[0][1])

    def test_annotated_static_nested_bean_constructor_exceeds_the_limit(self):
        violations = self.analyze("""
            @Service public class ExampleService {
                public ExampleService(Dependency one) {}
                public static class Value {
                    public Value(String a, String b, String c) {}
                }
                @Component("nestedBean")
                @Lazy
                public static final class NestedBean {
                    public NestedBean(First one, Second two, Third three, Fourth four) {}
                }
            }
        """)
        self.assertEqual(1, len(violations))
        self.assertEqual(4, violations[0][1])

    def test_repository_exclusion_remains_unchanged(self):
        self.assertEqual([], self.analyze("""
            @Service public class ExampleService {
                public ExampleService(First one, Second two, ExampleRepository repository) {}
            }
        """))


class AnalyzerCliEventTest(unittest.TestCase):
    def run_cli(self, directory, event):
        return subprocess.run([
            sys.executable, str(Path(__file__).with_name("analyze_java_files.py")),
            "--dir", directory, "--event-type", event, "--max-lines", "2",
            "--max-large-classes", "0", "--max-complex-beans", "0",
        ], capture_output=True, text=True, check=False)

    def test_supported_events_accept_small_source_tree(self):
        for event in ("workflow_dispatch", "merge_group", "release"):
            with self.subTest(event=event), tempfile.TemporaryDirectory() as directory:
                source = Path(directory, "Small.java")
                source.write_text("class Small {}\n", encoding="utf-8")
                result = self.run_cli(directory, event)
                self.assertEqual(0, result.returncode, result.stderr)

    def test_supported_events_reject_over_budget_source_tree(self):
        for event in ("workflow_dispatch", "merge_group", "release"):
            with self.subTest(event=event), tempfile.TemporaryDirectory() as directory:
                source = Path(directory, "Small.java")
                source.write_text("class Small {\n    int value;\n}\n", encoding="utf-8")
                result = self.run_cli(directory, event)
                self.assertEqual(1, result.returncode, result.stderr)
                self.assertIn("Too many large classes: 1 > 0", result.stdout)

    def test_unknown_event_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            result = self.run_cli(directory, "merge_grop")
            self.assertEqual(2, result.returncode)
            self.assertIn("invalid choice", result.stderr)


if __name__ == "__main__":
    unittest.main()
