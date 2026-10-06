import hashlib
import importlib.util
import json
from pathlib import Path
import stat
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('bootstrap', ROOT / 'scripts/gradle_bootstrap.py')
BOOT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BOOT)

class CloudBuildTests(unittest.TestCase):
    def test_valid_checksum(self):
        with tempfile.TemporaryDirectory() as t:
            p = Path(t) / 'blob'; p.write_bytes(b'example')
            BOOT.verify_checksum(p, hashlib.sha256(b'example').hexdigest())

    def test_invalid_checksum_rejected(self):
        with tempfile.TemporaryDirectory() as t:
            p = Path(t) / 'blob'; p.write_bytes(b'corrupted')
            with self.assertRaises(RuntimeError): BOOT.verify_checksum(p, '0'*64)

    def make_zip(self, path, name, symlink=False):
        with zipfile.ZipFile(path, 'w') as z:
            info = zipfile.ZipInfo(name)
            if symlink: info.external_attr = (stat.S_IFLNK | 0o777) << 16
            z.writestr(info, b'test fixture, not Gradle or Minecraft')

    def test_safe_archive_extracts(self):
        with tempfile.TemporaryDirectory() as t:
            d = Path(t); self.make_zip(d/'a.zip', 'gradle-8.8/bin/gradle')
            BOOT.safe_extract(d/'a.zip', d/'out', 'gradle-8.8')
            self.assertTrue((d/'out/gradle-8.8/bin/gradle').is_file())

    def test_parent_traversal_rejected(self):
        self.reject('gradle-8.8/../../escape')
    def test_absolute_path_rejected(self):
        self.reject('/gradle-8.8/bin/gradle')
    def test_wrong_prefix_rejected(self):
        self.reject('other/bin/gradle')
    def test_backslash_rejected(self):
        self.reject('gradle-8.8/..\\escape')
    def test_windows_drive_rejected(self):
        self.reject('gradle-8.8/C:/escape')
    def test_symlink_rejected(self):
        self.reject('gradle-8.8/link', True)
    def reject(self, name, symlink=False):
        with tempfile.TemporaryDirectory() as t:
            d=Path(t); self.make_zip(d/'a.zip',name,symlink)
            with self.assertRaises(RuntimeError): BOOT.safe_extract(d/'a.zip',d/'out','gradle-8.8')

    def test_toolchain_pins(self):
        c=json.loads((ROOT/'ci/toolchain.json').read_text())
        self.assertEqual(c['gradle'],'8.8')
        self.assertEqual(c['gtnh'],'2.8.4')
        self.assertEqual(c['gregtech'],'com.github.GTNewHorizons:GT5-Unofficial:5.09.51.482:dev')
        self.assertEqual(c['cofh'],'curse.maven:cofh-core-69162:2388751')
        self.assertEqual(len(c['gradle_bin_sha256']),64)

    def test_no_instance_dependency(self):
        text=(ROOT/'build.gradle').read_text()
        self.assertNotIn('cofhPath',text)
        self.assertNotIn('cofhJar',text)
        self.assertNotIn('InstanceDir',(ROOT/'scripts/Build.ps1').read_text())
        self.assertIn("rfg.deobf('curse.maven:cofh-core-69162:2388751')",text)

    def test_success_artifact_is_gated(self):
        text=(ROOT/'.github/workflows/build.yml').read_text()
        self.assertNotIn('continue-on-error',text)
        self.assertNotIn('pull_request_target',text)
        self.assertIn('needs: core',text)
        self.assertIn('path: build/cloud-artifact/',text)
        self.assertIn('if-no-files-found: error',text)
        self.assertIn('set -euo pipefail',text)
        self.assertIn('contents: read',text)

    def test_no_token_or_instance_in_cloud_setup(self):
        text=(ROOT/'.github/workflows/build.yml').read_text()
        for value in ('secrets.', 'InstanceDir', 'COFH_JAR', '--scan'):
            self.assertNotIn(value,text)
        self.assertEqual(text.count('persist-credentials: false'),3)
        self.assertIn('GH_TOKEN: ${{ github.token }}',text)
        self.assertIn("github.event_name == 'push' && github.ref == 'refs/heads/main'",text)

    def test_artifact_own_namespace_only(self):
        text=(ROOT/'build.gradle').read_text()
        self.assertIn("exclude 'cofh/**', 'gregtech/**', 'net/minecraft/**'",text)
        self.assertIn('gtnhStartupTested: false',text)
        self.assertIn("dependsOn tasks.named('check'), tasks.named('verifyReleaseJar')",text)

if __name__ == '__main__': unittest.main()
