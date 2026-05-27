#!/bin/sh
# Posix and USS compatible shell script to test PuliziaCassaforte.groovy with a specific configuration and input file.
# Test script
# - fileOpsType: local
# - buildMapClientType: json
# - one file that does not match rules (should be skipped, not cause error)

set -e

ENV="ATO"
BUILD_GROUP="ATO"

# Create temporary directory
TEMP_DIR="${TMPDIR:-/tmp}/run-puliziacassaforte.$$"
mkdir -p "$TEMP_DIR"
trap "rm -rf $TEMP_DIR" EXIT


SOURCE_FILE="ATO/yn_r_01_ato_r1/src/mapasm/batch/TESTMEM.SZFSSWG"
mkdir -p "$(dirname "$TEMP_DIR/$SOURCE_FILE")"
touch "$TEMP_DIR/$SOURCE_FILE"

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
    printf 'buildMapPath=%s\n' "$(resource_file 'buildmap.json')" >>  "$_cfg"
    printf 'uxBasedir=%s\n'    "$TEMP_DIR"                        >> "$_cfg"
    printf 'rulesPath=%s\n'    "$(resource_file 'rules.csv')"     >> "$_cfg"
    printf 'stageMapPath=%s\n' "$(resource_file 'stage-map.csv')" >> "$_cfg"
    // Note hlq, userId, pwFilePath, db2ConfigPath required when using db2 client
    echo "$_cfg"
}

# Helper: write list file to TEMP_DIR, print its path
list_file() {
    _lista="$TEMP_DIR/lista.csv"
    printf '%s\n' "$1" > "$_lista"
    echo "$_lista"
}

config_file=$(write_config)
lista=$(list_file "C,$SOURCE_FILE")

result=0
# Groovy script reads also PuliziaCassaforte.properties from current directory
groovyz -Dorg.slf4j.simpleLogger -Dorg.slf4j.simpleLogger.defaultLogLevel=debug RunPuliziaCassaforte.groovy "$lista" "$ENV" "$BUILD_GROUP" || result=$?

if [ "$result" -eq 0 ]; then
    echo "Test passed: no errors"
else
    echo "Test failed: errors detected (exit code: $result)"
    exit 1
fi
