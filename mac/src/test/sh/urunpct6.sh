#!/bin/sh
# Posix and USS compatible shell script to test PuliziaCassaforte.groovy with a specific configuration and input file.
# Test script
# - fileOpsType: local
# - buildMapClientType: json
# - one file match rules, the other does not
# No Build Map rules

set -e

# Verify USS environment
_os=$(uname -s 2>/dev/null)
case "$_os" in
    OS/390|z/OS) ;;
    *) printf 'ERROR: USS environment required (uname -s: %s)\n' "${_os:-unavailable}" >&2; exit 1 ;;
esac
unset _os

if [ -z "$HLQ" ]; then
    export HLQ="U0G9700"
fi

ENV="ST"
BUILD_GROUP="ST"

SF1="ST/yo_y_01_st_r1/src/JCL/BATCH/SJCLCA7/YO810BDD.SJCLCA7"
# Stage ST -> XAD, PR -> XPE
ZOSDSST="${HLQ}.LTM00.D9PXAD.PE000.@@@@.@@@@@@@@.@@.JJJJ"
ZOSDSPR="${HLQ}.LTM00.D9PXPE.PE000.@@@@.@@@@@@@@.@@.JJJJ"
# TOCOLB directoru for ST
ZODDSTC="${HLQ}.LTM00.D9PXAD.PE000.TO@@.COLB@@@@.@@.JJJJ"


# Create temporary directory
TEMP_DIR="tmp/run-puliziacassaforte.$$"
mkdir -p "$TEMP_DIR"
trap "rm -rf $TEMP_DIR" EXIT
echo "Temporary directory created at: $TEMP_DIR"

# loop on [ZOSDSST, ZOSDSPR] to create directory and file for each
for ZOSDS in "$ZOSDSST" "$ZOSDSPR" "$ZODDSTC"; do
    mkdir -p "$TEMP_DIR/$ZOSDS"
    echo "Simulated z/OS dataset directory created at: $TEMP_DIR/$ZOSDS"
done

# Create two files under the same directories, filaneme is basename of SF1 without extension, e.g. YO810BDD
for ZOSDS in "$ZOSDSST" "$ZOSDSPR"; do
    BASENAMENOEXT=$(basename "$SF1" | cut -d. -f1)
    touch "$TEMP_DIR/$ZOSDS/$BASENAMENOEXT"
done

# Helper: absolute path to a resource
# script lives under test; resource files live under test/resources
resource_file() {
    echo "$(cd "$(dirname "$0")" && pwd)/resources/$1"
}

# Helper: write config properties file to TEMP_DIR, print its path
write_config() {
    _cfg="PuliziaCassaforte.properties"
    printf 'fileOpsType=%s\n' "local"                             >  "$_cfg"
    printf 'buildMapClientType=%s\n' "json"                       >> "$_cfg"
    printf 'buildMapPath=%s\n' "$(resource_file 'buildmap.json')" >> "$_cfg"
    printf 'uxBasedir=%s\n'    "$TEMP_DIR"                        >> "$_cfg"
    printf 'rulesPath=%s\n'    "$(resource_file 'rulest6.csv')"   >> "$_cfg"
    printf 'stageMapPath=%s\n' "$(resource_file 'stagemap.csv')" >> "$_cfg"
    # Note hlq, userId, pwFilePath, db2ConfigPath required when using db2 client
    echo "$_cfg"
}

# Helper: write list file to TEMP_DIR, print its path
list_file() {
    _lista="$TEMP_DIR/lista.csv"
    for SF in "$SF1"; do
        printf 'S,%s\n' "$SF" >> "$_lista"
    done
    echo "$_lista"
}

config_file=$(write_config)
lista=$(list_file)
echo "File to be processed:"
cat "$lista"
echo "Directory content before script execution:"
echo "$TEMP_DIR/$ZOSDSST"
ls -l "$TEMP_DIR/$ZOSDSST"
echo "$TEMP_DIR/$ZODDSTC"
ls -l "$TEMP_DIR/$ZODDSTC"

result=0
# Groovy script reads also PuliziaCassaforte.properties from current directory
groovyz -Dorg.slf4j.simpleLogger -Dorg.slf4j.simpleLogger.defaultLogLevel=debug RunPuliziaCassaforte.groovy "$lista" "$ENV" "$BUILD_GROUP" || result=$?

# Show dir content
echo "Directory content after script execution:"
echo "$TEMP_DIR/$ZOSDSST"
ls -l "$TEMP_DIR/$ZOSDSST"
echo "$TEMP_DIR/$ZODDSTC"
ls -l "$TEMP_DIR/$ZODDSTC"

if [ "$result" -eq 0 ]; then
    echo "Test passed: no errors"
else
    echo "Test failed: errors detected (exit code: $result)"
    exit 1
fi
