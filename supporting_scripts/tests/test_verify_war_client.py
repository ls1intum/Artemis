import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

VERIFY = Path(__file__).resolve().parents[2] / ".github/scripts/verify-war-client.py"
STATIC = "WEB-INF/classes/static/"
OPTIMIZED = b"const optimized='" + b"a" * 4000 + b"';\n"
UNOPTIMIZED = b"let unoptimized = 1;\n" * 10


class VerifyWarClientTest(unittest.TestCase):
    def verify(self, index, bundles):
        with tempfile.TemporaryDirectory() as directory:
            war = Path(directory) / "Artemis-test.war"
            with ZipFile(war, "w") as archive:
                archive.writestr(STATIC + "index.html", index)
                for name, content in bundles.items():
                    archive.writestr(STATIC + name, content)
            return subprocess.run([sys.executable, VERIFY, war], capture_output=True, text=True, check=False)

    def test_rejects_unminified_entry_point_even_with_an_unused_minified_bundle(self):
        result = self.verify(
            '<script src="main-served.js" type="module"></script>',
            {"main-served.js": UNOPTIMIZED, "main-unused.js": OPTIMIZED},
        )
        self.assertEqual(result.returncode, 1)
        self.assertIn("main-served.js is not minified", result.stdout)

    def test_accepts_the_referenced_minified_bundle_with_an_unused_unminified_bundle(self):
        result = self.verify(
            "<script type='module' src='./main-served.js'></script>",
            {"main-served.js": OPTIMIZED, "main-unused.js": UNOPTIMIZED},
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_rejects_a_missing_referenced_bundle(self):
        result = self.verify('<script src="/main-missing.js" type="module"></script>', {"main-unused.js": OPTIMIZED})
        self.assertEqual(result.returncode, 1)
        self.assertIn("referenced by index.html", result.stdout)

    def test_rejects_an_index_without_a_main_script(self):
        result = self.verify('<!-- <script src="main-unused.js"></script> -->', {"main-unused.js": OPTIMIZED})
        self.assertEqual(result.returncode, 1)
        self.assertIn("found 0", result.stdout)

    def test_rejects_multiple_main_scripts(self):
        result = self.verify(
            '<script src="main-first.js"></script><script src="main-second.js"></script>',
            {"main-first.js": OPTIMIZED, "main-second.js": OPTIMIZED},
        )
        self.assertEqual(result.returncode, 1)
        self.assertIn("found 2", result.stdout)
