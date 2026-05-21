#!/bin/sh
# POSIX-compliant. Designed to run on z/OS USS.
# Compiles src/zos/groovy sources (requires $DBB_HOME set) and merges the resulting
# classes into an existing pulizia-cassaforte.jar.
#
# Usage: ./add-zos-to-cassaforte-jar.sh <path-to-pulizia-cassaforte.jar>
# Must be run from the cassaforte project root.

set -e

CURRENT_DIR=$(pwd)
trap 'cd "$CURRENT_DIR"' EXIT

# Validate arguments
[ -n "$1" ] || { echo "Usage: $0 <path-to-pulizia-cassaforte.jar>" >&2; exit 1; }
[ -f "$1" ] || { echo "ERROR: File not found: $1" >&2; exit 1; }

# Guard: must run from cassaforte project root
[ -d "src/zos/groovy" ] || { echo "ERROR: Must run from cassaforte project root (src/zos/groovy not found)" >&2; exit 1; }

# Guard: $DBB_HOME must be set
[ -n "$DBB_HOME" ] || { echo "ERROR: DBB_HOME not set" >&2; exit 1; }

# Get canonical path to jar (POSIX-compatible; readlink -f not available on z/OS USS)
PULIZIA_CASSAFORTE_JAR=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")

# Check all src/zos/groovy files are tagged IBM-1047
ENCODING_ERRORS=$(
    find src/zos/groovy -name "*.groovy" | while IFS= read -r f; do
        tag=$(chtag -p "$f" 2>/dev/null | awk '{print $2}')
        if [ "$tag" != "IBM-1047" ]; then
            printf 'WARNING: %s tagged as %s (expected IBM-1047)\n' "$f" "$tag"
        fi
    done
)
if [ -n "$ENCODING_ERRORS" ]; then
    echo "$ENCODING_ERRORS" >&2
    exit 1
fi
echo "OK: All .groovy files tagged IBM-1047"

# Extract existing jar into build/classes/groovy/pulizia-cassaforte
mkdir -p build/classes/groovy/pulizia-cassaforte
cd build/classes/groovy/pulizia-cassaforte
jar xf "$PULIZIA_CASSAFORTE_JAR"

# Return to project root
cd "$CURRENT_DIR"

# Compile z/OS groovy sources using IBM jars from $DBB_HOME
export JAVA_OPTS="-Dfile.encoding=COMPAT -Dcom.ibm.autocvt=false -Dsun.jnu.encoding=IBM-1047"
groovyc -cp "$DBB_HOME/lib/*:$PULIZIA_CASSAFORTE_JAR" \
    -d ./build/classes/groovy/pulizia-cassaforte \
    --encoding IBM-1047 \
    src/zos/groovy/*.groovy

# Reassemble jar preserving original manifest
cd build/classes/groovy/pulizia-cassaforte
jar cfm "$PULIZIA_CASSAFORTE_JAR" META-INF/MANIFEST.MF .
echo "Updated $PULIZIA_CASSAFORTE_JAR with z/OS specific classes from src/zos/groovy"
