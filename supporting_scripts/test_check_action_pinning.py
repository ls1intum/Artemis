"""Test that check_action_pinning.py accepts exactly the references the pinning policy allows.

Run: python3 supporting_scripts/test_check_action_pinning.py
"""

import io
from contextlib import redirect_stdout
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))

import check_action_pinning as pinning  # noqa: E402

SHA = "d23441a48e516b6c34aea4fa41551a30e30af803"
DIGEST = "a" * 64


class CheckLineTest(unittest.TestCase):
    """The decision for a single line of a workflow or composite action."""

    def assert_accepted(self, line: str) -> None:
        self.assertIsNone(pinning.check_line(line), line)

    def assert_rejected(self, line: str, reason_part: str) -> None:
        reason = pinning.check_line(line)
        self.assertIsNotNone(reason, f"expected a violation for: {line}")
        self.assertIn(reason_part, reason)

    def test_accepts_sha_with_version_comment(self):
        self.assert_accepted(f"        uses: actions/checkout@{SHA} # v6.1.0")
        self.assert_accepted(f"      - uses: actions/checkout@{SHA}  # v6")
        self.assert_accepted(f"      - uses: gradle/actions/setup-gradle@{SHA} # v6")

    def test_accepts_versions_tagged_without_the_v_prefix(self):
        self.assert_accepted(f"      - uses: MaximilianAnzinger/issue-labeler@{SHA}  # 1.0.2")

    def test_accepts_pre_release_versions(self):
        self.assert_accepted(f"        uses: owner/repo@{SHA} # v2.1.0-rc.1")

    def test_accepts_a_quoted_reference(self):
        self.assert_accepted(f"        uses: 'actions/checkout@{SHA}' # v6")

    def test_accepts_actions_in_a_sub_directory(self):
        self.assert_accepted(f"        uses: github/codeql-action/upload-sarif@{SHA} # v4.31.2")

    def test_accepts_pinned_reusable_workflows(self):
        self.assert_accepted(f"    uses: ls1intum/.github/.github/workflows/build-and-push-docker-image.yml@{SHA} # v1.2.0")

    def test_accepts_local_actions_and_workflows(self):
        self.assert_accepted("        uses: ./.github/actions/e2e-setup")
        self.assert_accepted("    uses: ./.github/workflows/ci-build.yml")

    def test_accepts_docker_images_pinned_by_digest(self):
        self.assert_accepted(f"        uses: docker://alpine@sha256:{DIGEST}")

    def test_ignores_lines_that_are_not_uses_entries(self):
        self.assert_accepted("        run: echo uses actions/checkout@v6")
        self.assert_accepted("        # uses: actions/checkout@v6")
        self.assert_accepted("        name: uses: is part of this name")
        self.assert_accepted("")

    def test_rejects_a_major_version_tag(self):
        self.assert_rejected("        uses: actions/checkout@v6", "commit SHA")

    def test_rejects_a_full_version_tag(self):
        self.assert_rejected("      - uses: actions/setup-node@v4.4.0", "commit SHA")

    def test_rejects_a_branch(self):
        self.assert_rejected("        uses: actions/checkout@main", "commit SHA")

    def test_rejects_an_abbreviated_sha(self):
        self.assert_rejected("        uses: actions/checkout@d23441a # v6", "commit SHA")

    def test_rejects_an_action_without_any_reference(self):
        self.assert_rejected("        uses: actions/checkout", "commit SHA")

    def test_rejects_a_tagged_reusable_workflow(self):
        self.assert_rejected("    uses: ls1intum/.github/.github/workflows/build-and-push-docker-image.yml@v1.2.0", "commit SHA")

    def test_rejects_a_sha_without_a_version_comment(self):
        self.assert_rejected(f"        uses: actions/checkout@{SHA}", "# vX.Y.Z")

    def test_rejects_a_sha_with_a_comment_that_names_no_version(self):
        self.assert_rejected(f"        uses: actions/checkout@{SHA} # latest", "# vX.Y.Z")

    def test_rejects_a_docker_image_referenced_by_tag(self):
        self.assert_rejected("        uses: docker://alpine:3.20", "sha256 digest")


class RepositoryScanTest(unittest.TestCase):
    """Scanning a repository layout and the exit codes the CI step relies on."""

    def setUp(self):
        self.workspace = tempfile.TemporaryDirectory(prefix="artemis-pinning-test-")
        self.addCleanup(self.workspace.cleanup)
        self.root = Path(self.workspace.name)

    def write(self, relative_path: str, content: str) -> None:
        path = self.root / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def run_main(self) -> tuple[int, str]:
        output = io.StringIO()
        with redirect_stdout(output):
            exit_code = pinning.main(["check_action_pinning.py", str(self.root)])
        return exit_code, output.getvalue()

    def test_passes_when_every_reference_is_pinned(self):
        self.write(".github/workflows/ci.yml", f"jobs:\n  a:\n    steps:\n      - uses: actions/checkout@{SHA} # v6\n      - uses: ./.github/actions/setup\n")
        exit_code, output = self.run_main()
        self.assertEqual(0, exit_code)
        self.assertIn("1 workflow and action files", output)

    def test_reports_file_line_and_reference_of_each_violation(self):
        self.write(".github/workflows/ci.yml", f"jobs:\n  a:\n    steps:\n      - uses: actions/checkout@v6\n      - uses: actions/setup-node@{SHA} # v6\n")
        exit_code, output = self.run_main()
        self.assertEqual(1, exit_code)
        self.assertIn("::error file=.github/workflows/ci.yml,line=4::actions/checkout@v6:", output)
        self.assertNotIn("setup-node", output)
        self.assertIn("1 unpinned action reference(s)", output)

    def test_scans_composite_actions(self):
        self.write(".github/actions/setup/action.yml", "runs:\n  using: composite\n  steps:\n    - uses: actions/cache@v4\n")
        exit_code, output = self.run_main()
        self.assertEqual(1, exit_code)
        self.assertIn("file=.github/actions/setup/action.yml,line=4", output)

    def test_scans_yaml_files_with_both_suffixes(self):
        self.write(".github/workflows/a.yaml", "jobs:\n  a:\n    steps:\n      - uses: actions/checkout@v6\n")
        self.write(".github/workflows/b.yml", "jobs:\n  a:\n    steps:\n      - uses: actions/checkout@v6\n")
        exit_code, output = self.run_main()
        self.assertEqual(1, exit_code)
        self.assertIn("2 unpinned action reference(s)", output)

    def test_ignores_files_that_are_not_workflows(self):
        self.write(".github/workflows/README.md", "uses: actions/checkout@v6\n")
        self.write(".github/workflows/ci.yml", f"jobs:\n  a:\n    steps:\n      - uses: actions/checkout@{SHA} # v6\n")
        self.write("src/other.yml", "uses: actions/checkout@v6\n")
        exit_code, _ = self.run_main()
        self.assertEqual(0, exit_code)

    def test_fails_when_there_is_nothing_to_scan(self):
        exit_code, output = self.run_main()
        self.assertEqual(2, exit_code)
        self.assertIn("No workflow or action files found", output)


if __name__ == "__main__":
    unittest.main()
