#!/bin/sh
# Local (Mac) script to test FullPuliziaCassaforte vai RunPuliziaCassaforte f/e
# Mirrors urunpct2.sh but uses: groovy + ScriptLoader mock instead of groovyz.
# Test: fileOpsType=local, buildMapClientType=json, one match / two no-match.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SUBPROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../../.." && pwd)"
# echo "Running test with SCRIPT_DIR: $SCRIPT_DIR"
# echo "Subproject root: $SUBPROJECT_ROOT"
# echo "Project root: $PROJECT_ROOT"

ENV="ST"
BUILD_GROUP="ST-MAIN"

SF1="ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLCA7/YO810BDD.SJCLCA7"


# Create temporary directory, $TMPDIR include trailing slash
TEMP_DIR="${TMPDIR:-/tmp/}run-puliziacassaforte.$$"
mkdir -p "$TEMP_DIR"

cleanup() {
    rm -f "$SCRIPT_DIR/FullPuliziaCassaforte.groovy"
    rm -f "$SCRIPT_DIR/PuliziaCassaforte.properties"
    rm -f "$SCRIPT_DIR/simplelogger.properties"
    rm -f "$SCRIPT_DIR/lista.csv"
    rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

# Simulated z/OS PDS
ZOSSTDST="$TEMP_DIR/LTM00.D9PXAD.PE000.@@@@.@@@@@@@@.@@.SJCL"
mkdir -p "$ZOSSTDST"
ZOSPRDST="$TEMP_DIR/LTM00.D9PXAE.PE000.@@@@.@@@@@@@@.@@.SJCL"
mkdir -p "$ZOSPRDST"
ZOSSTTCB="$TEMP_DIR/LTM00.D9PXAD.PE000.TO@@.COLB@@@@.@@.SJCL"
echo "Simulated z/OS dataset directory created at: $ZOSTDST"


for SF in "$SF1"; do
    BASENAMENOEXT=$(basename "$SF" | cut -d. -f1)
    touch "$ZOSSTDST/$BASENAMENOEXT"
    echo "st-content" > "$ZOSSTDST/$BASENAMENOEXT"
    touch "$ZOSPRDST/$BASENAMENOEXT"
    echo "pr-content" > "$ZOSPRDST/$BASENAMENOEXT"
done

# Helper: absolute path to a resource (local layout differs from USS deployment)
resource_file() {
    echo "$SUBPROJECT_ROOT/src/test/resources/fixtures/$1"
}

write_rules() {
    _rules="$SCRIPT_DIR/rules.csv"
    echo "SJCL*   ;LTM00.D9P\${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.SJCL;NO" > "$_rules"
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
    for SF in "$SF1"; do
        printf 'S,%s\n' "$SF" >> "$_lista"
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

# Check that ZOSSTDST/MEMBER was deleted, ZOSPRDST/MEMBER was not touched and ZOSSTTCB/MEMBER was created
if [ -f "$ZOSSTDST/YO810BDD" ]; then
    echo "Test failed: expected file $ZOSSTDST/YO810BDD to be deleted, but it exists"
    result=1
else
    echo "Verified: $ZOSSTDST/YO810BDD was deleted"
fi
if [ -f "$ZOSPRDST/YO810BDD" ]; then
    echo "Verified: $ZOSPRDST/YO810BDD exists (not deleted)"
else
    echo "Test failed: expected file $ZOSPRDST/YO810BDD to exist, but it does not"
    result=1
fi
if [ -f "$ZOSSTTCB/YO810BDD" ]; then
    echo "Verified: $ZOSSTTCB/YO810BDD exists (created)"
else
    echo "Test failed: expected file $ZOSSTTCB/YO810BDD to exist, but it does not"
    result=1
fi

if [ "$result" -eq 0 ]; then
    echo "Test passed: no errors"
else
    echo "Test failed: errors detected (exit code: $result)"
    exit 1
fi
