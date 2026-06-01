#!/bin/sh
# Local (Mac) script to test FullPuliziaCassaforte via the ScriptLoader mock.
# Mirrors urunpct2.sh but uses: groovy + ScriptLoader mock instead of groovyz.
# Test: fileOpsType=local, buildMapClientType=json, one match / two no-match.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

ENV="ATO"
BUILD_GROUP="ATO"

SF1="ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLCA7/YO810BDD.SJCLCA7"
SF2="ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YO8AMBDD.SJCLINP"
SF3="ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLITT/YO84XS1.SJCLITT"

TEMP_DIR="${TMPDIR:-/tmp}/run-puliziacassaforte.$$"
mkdir -p "$TEMP_DIR"

cleanup() {
    rm -f "$SCRIPT_DIR/FullPuliziaCassaforte.groovy"
    rm -f "$SCRIPT_DIR/PuliziaCassaforte.properties"
    rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

# Simulated z/OS PDS
ZODDSSIM="$TEMP_DIR/U0G9700.D9PX1A.PE000.@@@@.JCL"
mkdir -p "$ZODDSSIM"
echo "Simulated z/OS dataset directory created at: $ZODDSSIM"

for SF in "$SF1" "$SF2" "$SF3"; do
    BASENAMENOEXT=$(basename "$SF" | cut -d. -f1)
    touch "$ZODDSSIM/$BASENAMENOEXT"
done

# Helper: absolute path to a resource (local layout differs from USS deployment)
resource_file() {
    echo "$SCRIPT_DIR/../resources/fixtures/$1"
}

write_config() {
    _cfg="$SCRIPT_DIR/PuliziaCassaforte.properties"
    printf 'fileOpsType=%s\n'        "local"                              >  "$_cfg"
    printf 'buildMapClientType=%s\n' "json"                               >> "$_cfg"
    printf 'buildMapPath=%s\n'       "$(resource_file 'buildmap.json')"   >> "$_cfg"
    printf 'uxBasedir=%s\n'          "$TEMP_DIR"                          >> "$_cfg"
    printf 'rulesPath=%s\n'          "$(resource_file 'rulest2.csv')"     >> "$_cfg"
    printf 'stageMapPath=%s\n'       "$(resource_file 'stagemap.csv')"    >> "$_cfg"
}

list_file() {
    _lista="$TEMP_DIR/lista.csv"
    for SF in "$SF1" "$SF2" "$SF3"; do
        printf 'C,%s\n' "$SF" >> "$_lista"
    done
    echo "$_lista"
}

write_config
lista=$(list_file)
echo "Files to be processed:"
cat "$lista"
echo "Directory content before script execution:"
ls -l "$ZODDSSIM"

# Compile IBM API stubs on-the-fly so FullPuliziaCassaforte.groovy can import them
STUBS_DIR="$TEMP_DIR/ibm-stubs"
mkdir -p "$STUBS_DIR"
find "$SCRIPT_DIR/../java" -name "*.java" | xargs javac -d "$STUBS_DIR"

# ScriptLoader mock sources (GroovyClassLoader compiles them dynamically)
MOCK_DIR="$SCRIPT_DIR/../../mock/groovy"

# slf4j-simple provider; run ./gradlew :full-fat-source:copyShLibs once to populate
SH_LIB="$SCRIPT_DIR/../../../build/sh-lib"

# loadScript(new File("FullPuliziaCassaforte.groovy")) resolves relative to CWD.
# Copy fat source alongside the runner script, then cd there.
cp "$SCRIPT_DIR/../../main/groovy/FullPuliziaCassaforte.groovy" "$SCRIPT_DIR/FullPuliziaCassaforte.groovy"

# DBB_CONF is checked by RunPuliziaCassaforte.groovy; set a dummy value for local runs
export DBB_CONF=/dev/null

result=0
cd "$SCRIPT_DIR"
# SCRIPT_DIR on classpath → simplelogger.properties picked up by slf4j-simple
groovy -cp "$MOCK_DIR:$STUBS_DIR:$SH_LIB/*:$SCRIPT_DIR" RunPuliziaCassaforte.groovy "$lista" "$ENV" "$BUILD_GROUP" || result=$?

echo "Directory content after script execution:"
ls -l "$ZODDSSIM"

if [ "$result" -eq 0 ]; then
    echo "Test passed: no errors"
else
    echo "Test failed: errors detected (exit code: $result)"
    exit 1
fi
