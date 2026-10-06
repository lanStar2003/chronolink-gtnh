#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/v2-core verification
javac --release 8 -d build/v2-core src/main/java/dev/chronolink/v2/core/*.java src/test/java/dev/chronolink/v2/V2CoreTests.java
java -ea -cp build/v2-core dev.chronolink.v2.V2CoreTests | tee verification/network-core-tests.txt
