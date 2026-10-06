#!/usr/bin/env python3
"""Publish only this run's verified JAR; never replace a published version/tag."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate_artifact(directory, commit):
    if not re.fullmatch(r'[0-9a-f]{40}', commit):
        raise RuntimeError('A full source commit SHA is required')
    evidence = json.loads((directory / 'BUILD_EVIDENCE.json').read_text(encoding='utf-8'))
    version = evidence.get('version', '')
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.]+)?', version):
        raise RuntimeError('Invalid release version')
    if evidence.get('commit') != commit or evidence.get('target') != 'GTNH 2.8.4':
        raise RuntimeError('Build identity does not match this run')
    for gate in ('fullCompile', 'coreTest', 'reobfuscation', 'packagingVerified', 'runtimeDependencyChecked'):
        if evidence.get(gate) is not True:
            raise RuntimeError('Missing successful gate: ' + gate)
    if evidence.get('gtnhStartupTested') is not False:
        raise RuntimeError('This release must not claim unperformed game startup tests')
    jar = directory / ('chronolink-gtnh2.8.4-' + version + '.jar')
    names = [jar.name, 'SHA256SUMS', 'BUILD_EVIDENCE.json', 'DEPENDENCY_CHECK.txt', 'READ_BEFORE_INSTALL.txt']
    assets = [directory / name for name in names]
    if not all(p.is_file() and not p.is_symlink() for p in assets):
        raise RuntimeError('Required release assets missing or symbolic')
    expected = sha256(jar) + '  ' + jar.name
    if (directory / 'SHA256SUMS').read_text().strip() != expected:
        raise RuntimeError('JAR checksum mismatch')
    return evidence, assets


def gh(*args, allow_missing=False):
    result = subprocess.run(['gh'] + list(args), text=True, capture_output=True, check=False)
    if result.returncode:
        if allow_missing and '(HTTP 404)' in result.stderr:
            return None
        raise RuntimeError('GitHub CLI failed: ' + result.stderr.strip())
    return result.stdout


def main():
    repo = os.environ['GITHUB_REPOSITORY']
    commit = os.environ['GITHUB_SHA']
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repo):
        raise RuntimeError('Invalid repository')
    if os.environ.get('GITHUB_REF') != 'refs/heads/main':
        raise RuntimeError('Publishing is allowed only from main')
    evidence, assets = validate_artifact(ROOT / 'release-input', commit)
    version = evidence['version']
    tag = 'v' + version
    notes = ROOT / 'docs/releases' / (version + '.md')
    if not notes.is_file():
        raise RuntimeError('Version-specific release notes are missing')
    endpoint = 'repos/' + repo + '/releases/tags/' + tag
    existing_text = gh('api', endpoint, allow_missing=True)
    if existing_text is not None:
        existing = json.loads(existing_text)
        ref = json.loads(gh('api', 'repos/' + repo + '/git/ref/tags/' + tag))['object']
        if ref['type'] == 'tag':
            ref = json.loads(gh('api', 'repos/' + repo + '/git/tags/' + ref['sha']))['object']
        if ref['sha'] != commit or existing['draft']:
            raise RuntimeError('Version already exists for another commit or an incomplete draft; nothing overwritten')
        print('Release already exists for the identical commit; verifying, not replacing.')
    else:
        args = ['release', 'create', tag] + [str(p) for p in assets]
        args += ['--repo', repo, '--target', commit, '--title', 'ChronoLink ' + version + ' | GTNH 2.8.4',
                 '--notes-file', str(notes)]
        if '-' in version:
            args += ['--prerelease', '--latest=false']
        gh(*args)
    release = json.loads(gh('api', endpoint))
    if release['draft']:
        raise RuntimeError('Release was not published')
    receipt_assets = []
    with tempfile.TemporaryDirectory(prefix='chronolink-release-') as tmp:
        for source in assets:
            gh('release', 'download', tag, '--repo', repo, '--pattern', source.name, '--dir', tmp)
            fetched = Path(tmp) / source.name
            if not fetched.is_file() or sha256(fetched) != sha256(source):
                raise RuntimeError('Published asset differs from verified artifact: ' + source.name)
            metadata = next(a for a in release['assets'] if a['name'] == source.name)
            receipt_assets.append({'name': source.name, 'sha256': sha256(fetched),
                                   'url': metadata['browser_download_url']})
    receipt = {'release': release['html_url'], 'tag': tag, 'commit': commit,
               'prerelease': release['prerelease'], 'published': True,
               'releaseAssetsDownloadedAndVerified': True, 'assets': receipt_assets}
    destination = ROOT / 'verification/release-result.json'
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(receipt, indent=2))


if __name__ == '__main__':
    main()
