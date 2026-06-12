#!/bin/sh
# Local (Mac) script to test FullPuliziaCassaforte vai RunPuliziaCassaforte f/e
# Env PR, C flag, only delete from PR

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SUBPROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../../.." && pwd)"
# echo "Running test with SCRIPT_DIR: $SCRIPT_DIR"
# echo "Subproject root: $SUBPROJECT_ROOT"
# echo "Project root: $PROJECT_ROOT"

ENV="PR"
BUILD_GROUP="PROD-JOBZ"

SLIST="edux0-jobz/\$HXL007.STWSNCS"


# Create temporary directory, $TMPDIR include trailing slash
TEMP_DIR="${TMPDIR:-/tmp/}run-puliziacassaforte.$$"
mkdir -p "$TEMP_DIR"

cleanup() {
    rm -f "$SCRIPT_DIR/FullPuliziaCassaforte.groovy"
    rm -f "$SCRIPT_DIR/PuliziaCassaforte.properties"
    rm -f "$SCRIPT_DIR/simplelogger.properties"
    rm -f "$SCRIPT_DIR/lista.csv"
    rm -f "$SCRIPT_DIR/rules.csv"
    rm -rf "$TEMP_DIR"
}
cleanup
# trap cleanup EXIT

# Simulated z/OS PDS
ZOSPRDST="$TEMP_DIR/LTM00.D9PXPE.PE000.@@@@.@@@@@@@@.@@.JNCS"
mkdir -p "$ZOSPRDST"
echo "Simulated z/OS dataset directory created at: $ZOSPRDST"


for SF in "$SLIST"; do
    BASENAMENOEXT=$(basename "$SF" | cut -d. -f1)
    touch "$ZOSPRDST/$BASENAMENOEXT"
    echo "pr-content" > "$ZOSPRDST/$BASENAMENOEXT"
done

# Helper: absolute path to a resource (local layout differs from USS deployment)
resource_file() {
    echo "$SUBPROJECT_ROOT/src/test/resources/fixtures/$1"
}

write_rules() {
    _rules="$SCRIPT_DIR/rules.csv"
    echo "STWSNCS   ;LTM00.D9P\${C1STAGEP}.PE000.@@@@.@@@@@@@@.@@.JNCS;NO" > "$_rules"
}

write_config() {
    _cfg="$SCRIPT_DIR/PuliziaCassaforte.properties"
    printf 'fileOpsType=%s\n'        "macos"                              >  "$_cfg"
    printf 'buildMapClientType=%s\n' "json"                               >> "$_cfg"
    printf 'buildMapPath=%s\n'       "$(resource_file 'buildmap.json')"   >> "$_cfg"
    printf 'uxBasedir=%s\n'          "$TEMP_DIR"                          >> "$_cfg"
    printf 'rulesPath=%s\n'          "$SCRIPT_DIR/rules.csv"              >> "$_cfg"
    printf 'stageMapPath=%s\n'       "$(resource_file 'stagemap.csv')"    >> "$_cfg"
}

write_simplelogger_config() {
    _slf4j_cfg="$SCRIPT_DIR/simplelogger.properties"
    printf 'org.slf4j.simpleLogger.defaultLogLevel=%s\n' "debug"         >  "$_slf4j_cfg"
    printf 'org.slf4j.simpleLogger.showLogName=%s\n' "true"              >> "$_slf4j_cfg"
    printf 'org.slf4j.simpleLogger.showThreadName=%s\n' "true"           >> "$_slf4j_cfg"
    printf 'org.slf4j.simpleLogger.showDateTime=%s\n' "true"             >> "$_slf4j_cfg"
    printf 'org.slf4j.simpleLogger.dateTimeFormat=%s\n' "yyyy-MM-dd HH:mm:ss:SSS" >> "$_slf4j_cfg"
    printf 'org.slf4j.simpleLogger.logFile=%s\n' "System.out"            >> "$_slf4j_cfg"
}

list_file() {
    _lista="$SCRIPT_DIR/lista.csv"
    for SF in "$SLIST"; do
        printf 'C,%s\n' "$SF" >> "$_lista"
    done
    echo "$_lista"
}

write_rules
write_config
write_simplelogger_config
lista=$(list_file)
echo "Files to be processed:"
cat "$lista"

# Compile IBM API stubs on-the-fly so FullPuliziaCassaforte.groovy can import them
STUBS_DIR="$TEMP_DIR/stubs"
mkdir -p "$STUBS_DIR"
find "$PROJECT_ROOT/stubs/src/main/java" -name "*.java" | xargs javac -d "$STUBS_DIR"

# grab org.slf4j:slf4j-simple:2.0.16 and copy
SH_LIB="$SUBPROJECT_ROOT/build/sh-lib"

# GroovyClassLoader.parseClass(new File("FullPuliziaCassaforte.groovy")) resolves relative to CWD.
# Copy fat source alongside the runner script, then cd there.
cp "$SUBPROJECT_ROOT/src/main/groovy/FullPuliziaCassaforte.groovy" "$SCRIPT_DIR/FullPuliziaCassaforte.groovy"

result=0
cd "$SCRIPT_DIR"
# SCRIPT_DIR on classpath → simplelogger.properties picked up by slf4j-simple
groovy -cp "$STUBS_DIR:$SH_LIB/*:$SCRIPT_DIR" RunPuliziaCassaforte.groovy "$lista" "$ENV" "$BUILD_GROUP" || result=$?

# Check ZOSPRDST/MEMBER was deleted
if [ -f "$ZOSPRDST/\$HXL007" ]; then
    echo "Test failed: expected file $ZOSPRDST/\$HXL007 to be deleted, but it exists"
    result=1
else
    echo "Verified: $ZOSPRDST/\$HXL007 was deleted"
fi

if [ "$result" -eq 0 ]; then
    echo "Test passed: no errors"
else
    echo "Test failed: errors detected (exit code: $result)"
    exit 1
fi
