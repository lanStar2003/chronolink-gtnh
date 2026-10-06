import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('publisher', ROOT / 'scripts/publish_release.py')
PUB = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PUB)
SHA = 'a' * 40

class ReleaseValidationTests(unittest.TestCase):
    def fixture(self, folder):
        evidence = dict(version='0.1.0-alpha.2', commit=SHA, target='GTNH 2.8.4',
                        fullCompile=True, coreTest=True, reobfuscation=True,
                        packagingVerified=True, runtimeDependencyChecked=True, gtnhStartupTested=False)
        jar = folder / 'chronolink-gtnh2.8.4-0.1.0-alpha.2.jar'
        # Only tests the publisher's identity/hash gate; not a fabricated mod build.
        jar.write_bytes(b'publisher hash test fixture - NOT a Minecraft JAR')
        (folder/'SHA256SUMS').write_text(hashlib.sha256(jar.read_bytes()).hexdigest()+'  '+jar.name+'\n')
        (folder/'BUILD_EVIDENCE.json').write_text(json.dumps(evidence))
        (folder/'DEPENDENCY_CHECK.txt').write_text('fixture, not an actual dependency test')
        (folder/'READ_BEFORE_INSTALL.txt').write_text('fixture')
        return evidence, jar

    def test_valid_identity_and_hash(self):
        with tempfile.TemporaryDirectory() as t:
            folder=Path(t); self.fixture(folder)
            evidence, assets=PUB.validate_artifact(folder,SHA)
            self.assertEqual(len(assets),5)

    def test_wrong_source_commit_rejected(self):
        with tempfile.TemporaryDirectory() as t:
            folder=Path(t); self.fixture(folder)
            with self.assertRaises(RuntimeError): PUB.validate_artifact(folder,'b'*40)

    def test_corrupted_payload_rejected(self):
        with tempfile.TemporaryDirectory() as t:
            folder=Path(t); _,jar=self.fixture(folder); jar.write_bytes(b'corruption')
            with self.assertRaises(RuntimeError): PUB.validate_artifact(folder,SHA)

    def test_unrun_dependency_probe_rejected(self):
        with tempfile.TemporaryDirectory() as t:
            folder=Path(t); evidence,_=self.fixture(folder); evidence['runtimeDependencyChecked']=False
            (folder/'BUILD_EVIDENCE.json').write_text(json.dumps(evidence))
            with self.assertRaises(RuntimeError): PUB.validate_artifact(folder,SHA)

    def test_unsafe_version_rejected(self):
        with tempfile.TemporaryDirectory() as t:
            folder=Path(t); evidence,_=self.fixture(folder); evidence['version']='../../other'
            (folder/'BUILD_EVIDENCE.json').write_text(json.dumps(evidence))
            with self.assertRaises(RuntimeError): PUB.validate_artifact(folder,SHA)

    def test_release_requires_successful_builds(self):
        workflow=(ROOT/'.github/workflows/build.yml').read_text()
        self.assertIn('needs: [core, build]',workflow)
        self.assertIn('contents: write',workflow)
        self.assertNotIn('pull_request_target',workflow)
        self.assertNotIn('--clobber',(ROOT/'scripts/publish_release.py').read_text())
