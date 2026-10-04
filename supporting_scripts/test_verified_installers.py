"""Test installer verification without network access, privilege escalation or cluster changes.

Run: python3 supporting_scripts/test_verified_installers.py
"""

import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


INSTALLER = Path(__file__).resolve().parents[1] / "install-localci-kubernetes-ubuntu.sh"


class VerifiedInstallerTest(unittest.TestCase):
    def setUp(self):
        self.workspace = tempfile.TemporaryDirectory(prefix="artemis-installer-test-")
        self.addCleanup(self.workspace.cleanup)
        self.root = Path(self.workspace.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.downloads = self.root / "downloads"
        self.downloads.mkdir()
        self.payload = self.root / "payload"
        self.payload.write_text('printf "executed:%s\\n" "$TEST_RELEASE"\nexit "$TEST_INSTALLER_EXIT"\n')
        self.checksum = hashlib.sha256(self.payload.read_bytes()).hexdigest()
        self.marker = self.root / "sudo-called"
        self.stub("curl", '''#!/usr/bin/env bash
if [[ "$TEST_DOWNLOAD_FAIL" == 1 ]]; then exit 22; fi
while [[ $# -gt 0 ]]; do
    if [[ "$1" == --output ]]; then cp "$TEST_PAYLOAD" "$2"; exit 0; fi
    shift
done
exit 2
''')
        self.stub("sudo", '''#!/usr/bin/env bash
touch "$TEST_SUDO_MARKER"
exec "$@"
''')

    def stub(self, name, contents):
        executable = self.bin / name
        executable.write_text(contents)
        executable.chmod(0o755)

    def run_installer(self, checksum=None, download_fail=False, installer_exit=0):
        environment = dict(os.environ)
        environment.update(
            PATH=f"{self.bin}{os.pathsep}{environment['PATH']}",
            TMPDIR=str(self.downloads),
            TEST_PAYLOAD=str(self.payload),
            TEST_SUDO_MARKER=str(self.marker),
            TEST_DOWNLOAD_FAIL=str(int(download_fail)),
            TEST_INSTALLER_EXIT=str(installer_exit),
        )
        result = subprocess.run(
            ["bash", "-c", '''set -- help
source "$TEST_SCRIPT" >/dev/null
run_verified_installer https://example.invalid/installer "$TEST_HASH" env TEST_RELEASE=pinned sh
'''],
            env=environment | {"TEST_SCRIPT": str(INSTALLER), "TEST_HASH": checksum or self.checksum},
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(list(self.downloads.iterdir()), [], "Temporary installer must be removed")
        return result

    def test_verified_download_executes_with_release_environment(self):
        result = self.run_installer()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "executed:pinned\n")
        self.assertTrue(self.marker.exists())

    def test_tampered_download_never_reaches_sudo(self):
        result = self.run_installer(checksum="0" * 64)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Installer checksum mismatch", result.stderr)
        self.assertFalse(self.marker.exists())
        self.assertNotIn("executed:", result.stdout)

    def test_failed_download_never_reaches_sudo(self):
        result = self.run_installer(download_fail=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Installer download failed", result.stderr)
        self.assertFalse(self.marker.exists())

    def test_installer_failure_is_propagated_and_cleaned_up(self):
        result = self.run_installer(installer_exit=17)
        self.assertEqual(result.returncode, 17)
        self.assertTrue(self.marker.exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
