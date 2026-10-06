#!/usr/bin/env python3
"""Pinned Gradle bootstrap, NOT a substitute compiler or an official Gradle Wrapper.
No Minecraft instance or credentials are read. Requires Python 3.9+ and a build JDK.
"""
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import stat
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def sha256(path: Path) -> str:
    result = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(chunk)
    return result.hexdigest()

def verify_checksum(path: Path, expected: str) -> None:
    if sha256(path).lower() != expected.lower():
        raise RuntimeError('Gradle SHA-256 mismatch; refusing to execute the download.')

def safe_extract(archive: Path, destination: Path, prefix: str) -> None:
    with zipfile.ZipFile(archive) as z:
        for member in z.infolist():
            p = PurePosixPath(member.filename)
            mode = member.external_attr >> 16
            if (not p.parts or p.is_absolute() or '..' in p.parts or p.parts[0] != prefix
                    or '\\' in member.filename or ':' in member.filename
                    or stat.S_ISLNK(mode)):
                raise RuntimeError('Unsafe path in Gradle archive: ' + member.filename)
        z.extractall(destination)

def install_gradle(config: dict, cache: Path) -> Path:
    cache.mkdir(parents=True, exist_ok=True)
    version = config['gradle']
    base = cache / ('gradle-' + version)
    exe = base / 'bin' / ('gradle.bat' if os.name == 'nt' else 'gradle')
    marker = base / '.chronolink-verified-sha256'
    expected = config['gradle_bin_sha256']
    if exe.is_file() and marker.is_file() and marker.read_text().strip() == expected:
        return exe
    archive = cache / ('gradle-' + version + '-bin.zip')
    if not archive.is_file():
        part = archive.with_suffix('.zip.part')
        try:
            request = urllib.request.Request(config['gradle_url'], headers={'User-Agent': 'ChronoLink-build-bootstrap'})
            with urllib.request.urlopen(request, timeout=30) as response, part.open('wb') as output:
                if not response.geturl().startswith('https://'):
                    raise RuntimeError('Refusing a non-HTTPS redirect.')
                shutil.copyfileobj(response, output)
            verify_checksum(part, expected)
            part.replace(archive)
        finally:
            if part.exists():
                part.unlink()
    verify_checksum(archive, expected)
    if base.exists():
        raise RuntimeError('Unverified tool directory exists; inspect or remove only ' + str(base))
    with tempfile.TemporaryDirectory(prefix='gradle-unpack-', dir=cache) as tmp:
        safe_extract(archive, Path(tmp), 'gradle-' + version)
        staging = Path(tmp) / ('gradle-' + version)
        if not (staging / 'bin' / 'gradle').is_file():
            raise RuntimeError('Gradle launcher missing from verified archive.')
        staging.rename(base)
    if os.name != 'nt':
        exe.chmod(exe.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    marker.write_text(expected + '\n')
    return exe

def main() -> int:
    config = json.loads((ROOT / 'ci/toolchain.json').read_text())
    if sys.argv[1:] == ['--print-config']:
        print(json.dumps(config, indent=2)); return 0
    if not shutil.which('java'):
        raise RuntimeError('Install a Java 17 or 21 JDK for build tools; Java 8 is the compile toolchain.')
    exe = install_gradle(config, ROOT / '.tools')
    args = sys.argv[1:] or ['--no-daemon', '--console=plain', '--stacktrace', 'clean', 'check', 'reobfJar', 'stageCloudArtifact']
    return subprocess.call([str(exe), *args], cwd=ROOT)

if __name__ == '__main__':
    try:
        sys.exit(main())
    except (OSError, RuntimeError, ValueError, urllib.error.URLError, zipfile.BadZipFile) as exc:
        print('BUILD NOT STARTED/COMPLETED: ' + str(exc), file=sys.stderr)
        sys.exit(1)
