#!/usr/bin/env bash
#
# Build TubeWinder.jar, a single double-clickable jar containing both modules.
#
# Run it from Git Bash:
#     ./tools/build-jar.sh
#
# Needs javac and jar on PATH. If Git Bash cannot find them, point JAVA_BIN at a JDK's bin
# directory first, for example:
#     JAVA_BIN="/c/Program Files/JetBrains/IntelliJ IDEA 2026.1/jbr/bin" ./tools/build-jar.sh

set -euo pipefail

cd "$(dirname "$0")/.."

JAVAC="${JAVA_BIN:+$JAVA_BIN/}javac"
JAR="${JAVA_BIN:+$JAVA_BIN/}jar"

if ! command -v "$JAVAC" >/dev/null 2>&1; then
    echo "javac not found." >&2
    echo "Set JAVA_BIN to a JDK bin directory and try again, e.g." >&2
    echo "  JAVA_BIN=\"/c/Program Files/JetBrains/IntelliJ IDEA 2026.1/jbr/bin\" $0" >&2
    exit 1
fi

echo "Compiling..."
rm -rf target/classes
mkdir -p target/classes
"$JAVAC" -nowarn -d target/classes --release 17 \
    $(find backend/src/main/java ui/src/main/java -name '*.java')

echo "Packaging..."
"$JAR" --create --file TubeWinder.jar \
    --main-class com.berkeleybikebuilders.tubewinder.ui.TubeWinderApp \
    -C target/classes .

echo "Built $(pwd)/TubeWinder.jar"
echo "Double-click TubeWinder.bat to run it, or make a desktop shortcut with"
echo "tools/create-desktop-shortcut.ps1."
