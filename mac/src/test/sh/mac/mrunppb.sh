#!/bin/sh
# Local (Mac) script to test FullPuliziaCassaforte via the ScriptLoader mock.
# Mirrors urunpct2.sh but uses: groovy + ScriptLoader mock instead of groovyz.
# Test: fileOpsType=local, buildMapClientType=json, one match / two no-match.

set -e

CWD="$(pwd)"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# echo "Running test with SCRIPT_DIR: $SCRIPT_DIR"
# echo "Subproject root: $SUBPROJECT_ROOT"
# echo "Project root: $PROJECT_ROOT"

# File paths passed as arguments from Gradle
FAT_SOURCE_FILE="${1}"
FE_SCRIPT="${2}"

ENV="ST"
BUILD_GROUP="ST"

SF1="ST/yo_y_01_st_r1/src/JCL/BATCH/SJCLCA7/YO810BDD.SJCLCA7"
SF2="ST/yo_y_01_st_r1/src/JCL/BATCH/SJCLINP/YO8AMBDD.SJCLINP"
SF3="ST/yo_y_01_st_r1/src/JCL/BATCH/SJCLITT/YO84XS1.SJCLITT"

# Create temporary directory, $TMPDIR include trailing slash
TEMP_DIR="${TMPDIR:-/tmp/}run-pc.$$"
mkdir -p "$TEMP_DIR"

cleanup() {
    rm -f "$SCRIPT_DIR/FullPuliziaCassaforte.groovy"
    rm -f "$SCRIPT_DIR/RunPuliziaCassaforte.groovy"
    rm -f "$SCRIPT_DIR/PuliziaCassaforte.properties"
    rm -f "$SCRIPT_DIR/simplelogger.properties"
    rm -f "$SCRIPT_DIR/lista.csv"
    rm -f "$SCRIPT_DIR/rules.csv"
    rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

# Simulated z/OS PDS
ST01DS="$TEMP_DIR/U0G9700.D9PXAD.PE000.@@@@.JCL" # ST 01
ATO01DS="$TEMP_DIR/U0G9700.D9PX2A.PE000.@@@@.JCL" # ATO 01
mkdir -p "$ST01DS"
mkdir -p "$ATO01DS"
echo "Simulated z/OS dataset directory created"

for SF in "$SF1" "$SF2" "$SF3"; do
    BASENAMENOEXT=$(basename "$SF" | cut -d. -f1)
    touch "$ST01DS/$BASENAMENOEXT"
    touch "$ATO01DS/$BASENAMENOEXT"
done

# Helper: absolute path to a resource (local layout differs from USS deployment)
resource_file() {
    echo "$SCRIPT_DIR/../../resources/fixtures/$1"
}

write_config() {
    _cfg="$SCRIPT_DIR/PuliziaCassaforte.properties"
    printf 'fileOpsType=%s\n'        "macos"                              >  "$_cfg"
    printf 'buildMapClientType=%s\n' "json"                               >> "$_cfg"
    printf 'buildMapPath=%s\n'       "$(resource_file 'buildmap.json')"   >> "$_cfg"
    printf 'uxBasedir=%s\n'          "$TEMP_DIR"                          >> "$_cfg"
    printf 'rulesPath=%s\n'          "$(resource_file 'rulest2.csv')"     >> "$_cfg"
    printf 'stageMapPath=%s\n'       "$(resource_file 'stagemap.csv')"    >> "$_cfg"
}

write_simplelogger_config() {
    _slf4j_cfg="$SCRIPT_DIR/simplelogger.properties"
    printf 'org.slf4j.simpleLogger.defaultLogLevel=%s\n' "debug"         >  "$_slf4j_cfg"
    printf 'org.slf4j.simpleLogger.showLogName=%s\n' "true"              >> "$_slf4j_cfg"
    printf 'org.slf4j.simpleLogger.showThreadName=%s\n' "true"           >> "$_slf4j_cfg"
    printf 'org.slf4j.simpleLogger.showDateTime=%s\n' "true"             >> "$_slf4j_cfg"
    printf 'org.slf4j.simpleLogger.dateTimeFormat=%s\n' "yyyy-MM-dd HH:mm:ss:SSS" >> "$_slf4j_cfg"
#    printf 'org.slf4j.simpleLogger.logFile=%s\n' "System.out"            >> "$_slf4j_cfg"
}

write_rules() {
    _rules="$SCRIPT_DIR/rules.csv"
    printf 'SJCL*   ;LTM00.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.SJCL;NO' > "$_rules"
}

list_file() {
    _lista="$SCRIPT_DIR/lista.csv"
    for SF in "$SF1" "$SF2" "$SF3"; do
        printf 'C,%s\n' "$SF" >> "$_lista"
    done
    echo "$_lista"
}

write_config
write_simplelogger_config
write_rules
lista=$(list_file)
echo "Files to be processed:"
cat "$lista"
echo "Directory content before script execution:"
echo "Before listing $ST01DS"
ls -l "$ST01DS"
echo "Before listing $ATO01DS"
ls -l "$ATO01DS"

# Use classpath from Gradle if provided, otherwise construct fallback
if [ -z "$GROOVY_CLASSPATH" ]; then
    # Fallback: compile IBM API stubs on-the-fly and build classpath manually
    STUBS_DIR="$TEMP_DIR/ibm-stubs"
    mkdir -p "$STUBS_DIR"
    find "$PROJECT_ROOT/stubs/src/main/java" -name "*.java" | xargs javac -d "$STUBS_DIR"
    SH_LIB="$SUBPROJECT_ROOT/build/sh-lib"
    GROOVY_CLASSPATH="$STUBS_DIR:$SH_LIB/*:$SUBPROJECT_ROOT/src/test/mac"
fi

# GroovyClassLoader.parseClass(new File("FullPuliziaCassaforte.groovy")) resolves relative to CWD.
# Copy fat source and runner script alongside the test script, then cd there.
cp "$FAT_SOURCE_FILE" "$SCRIPT_DIR/FullPuliziaCassaforte.groovy"
cp "$FE_SCRIPT" "$SCRIPT_DIR/PuliziaPostBuild.groovy"

result=0
cd "$SCRIPT_DIR"
# SCRIPT_DIR on classpath → simplelogger.properties picked up by slf4j-simple
for SF in "$SF1" "$SF2" "$SF3"; do
    groovy -cp "$GROOVY_CLASSPATH" PuliziaPostBuild.groovy "$SF" "$ENV" "$BUILD_GROUP" || result=$?
done

echo "Directory content after script execution:"
echo "After listing $ST01DS"
ls -l "$ST01DS"
echo "After listing $ATO01DS"
ls -l "$ATO01DS"

if [ "$result" -eq 0 ]; then
    echo "Test passed: no errors"
else
    echo "Test failed: errors detected (exit code: $result)"
    exit 1
fi
