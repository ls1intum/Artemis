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

    def test_repository_exclusion_remains_unchanged(self):
        self.assertEqual([], self.analyze("""
            @Service public class ExampleService {
                public ExampleService(First one, Second two, ExampleRepository repository) {}
            }
        """))


if __name__ == "__main__":
    unittest.main()
