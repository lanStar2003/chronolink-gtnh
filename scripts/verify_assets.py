#!/usr/bin/env python3
"""Static resource validation only; does not start or validate the Minecraft renderer."""
import json
import struct
from pathlib import Path

root = Path(__file__).resolve().parents[1]
resources = root / 'src/main/resources'
assets = resources / 'assets/chronolink'
checks = 0

def check(condition, label):
    global checks
    if not condition:
        raise AssertionError(label)
    checks += 1
    print('PASS ' + label)

def lang_keys(path):
    lines = [line for line in path.read_text(encoding='utf-8').splitlines() if line and not line.startswith('#')]
    keys = [line.partition('=')[0] for line in lines]
    check(all('=' in line and line.partition('=')[2] for line in lines), f'{path.name}: nonempty values')
    check(len(keys) == len(set(keys)), f'{path.name}: unique keys')
    return set(keys)

zh = lang_keys(assets / 'lang/zh_CN.lang')
en = lang_keys(assets / 'lang/en_US.lang')
check(zh == en, f'language keys match ({len(zh)} keys each)')
info = json.loads((resources / 'mcmod.info').read_text())
check(info[0]['modid'] == 'chronolink', 'mod id')
check(info[0]['version'] == '0.1.0-alpha.1', 'alpha version')
check(info[0]['mcversion'] == '1.7.10', 'Minecraft target')
check(json.loads((resources / 'pack.mcmeta').read_text())['pack']['pack_format'] == 1, 'resource pack format')
for name, dims in {
    'blocks/connector_shell.png': (32, 32),
    'blocks/connector_idle.png': (32, 32),
    'blocks/connector_flow.png': (32, 256),
    'items/binder.png': (32, 32),
}.items():
    data = (assets / 'textures' / name).read_bytes()
    check(data[:8] == b'\x89PNG\r\n\x1a\n', name + ': PNG signature')
    check(struct.unpack('>II', data[16:24]) == dims, name + ': dimensions')
anim = json.loads((assets / 'textures/blocks/connector_flow.png.mcmeta').read_text())['animation']
check(anim['frametime'] >= 1, 'animation has positive frame time')
if 'frames' in anim:
    check(all(isinstance(i, int) and 0 <= i < 8 for i in anim['frames']), 'animation frame indexes within 8-frame atlas')
source = list((root / 'src').rglob('*.java'))
check(not any('/src/api/' in str(p) for p in source), 'no locally invented dependency API headers')
check(all('package dev.chronolink' in p.read_text() for p in source), 'all project Java sources use own namespace')
check(not list(root.glob('*.jar')), 'no source-root JAR presented as an installable mod')
check('required-after:gregtech@[5.09.51.482]' in (root / 'src/main/java/dev/chronolink/ChronoLink.java').read_text(), 'GT version pinned in mod declaration')
print(f'PASS: {checks} static resource/package checks. Actual in-game rendering and compatibility NOT tested.')
