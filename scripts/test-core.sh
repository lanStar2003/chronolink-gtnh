#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/offline-core verification
mapfile -d '' files < <(find src/main/java/dev/chronolink/core -name '*.java' -print0)
javac --release 8 -encoding UTF-8 -d build/offline-core "${files[@]}" src/test/java/dev/chronolink/core/CoreTests.java
java -ea -cp build/offline-core dev.chronolink.core.CoreTests | tee verification/core-tests.txt
