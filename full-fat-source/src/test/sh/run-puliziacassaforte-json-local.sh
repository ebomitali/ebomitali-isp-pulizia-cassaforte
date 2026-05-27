#!/bin/sh
# Posix and USS compatible shell script to test PuliziaCassaforte.groovy with a specific configuration and input file.

set -e

ENV="ATO"
BUILD_GROUP="ATO"

# Create temporary directory
TEMP_DIR="${TMPDIR:-/tmp}/run-puliziacassaforte.$$"
mkdir -p "$TEMP_DIR"
trap "rm -rf $TEMP_DIR" EXIT

SOURCE_FILE="$TEMP_DIR/ATO/yn_r_01_ato_r1/src/mapasm/batch/TESTMEM.SZFSSWG"
mkdir -p "$(dirname "$SOURCE_FILE")"
touch "$SOURCE_FILE"

# Helper: absolute path to a test fixture
# script lives under test/sh; fixtures live under test/resources/fixtures
resource_file() {
    echo "$(cd "$(dirname "$0")" && pwd)/resources/$1"
}

# Helper: write config properties file to TEMP_DIR, print its path
write_config() {
    _cfg="$TEMP_DIR/PuliziaCassaforte.properties"
    printf 'buildMapPath=%s\n' "$(resource_file 'buildmap.json')" >  "$_cfg"
    printf 'uxBasedir=%s\n'    "$TEMP_DIR"                       >> "$_cfg"
    printf 'rulesPath=%s\n'    "$(resource_file 'rules.csv')"     >> "$_cfg"
    printf 'stageMapPath=%s\n' "$(resource_file 'stage-map.csv')" >> "$_cfg"
    echo "$_cfg"
}

# Helper: write list file to TEMP_DIR, print its path
list_file() {
    _lista="$TEMP_DIR/lista.csv"
    printf '%s\n' "$1" > "$_lista"
    echo "$_lista"
}

# - test: run with config file processes C action without error -

echo "Running test: run with config file processes C action without error"

config_file=$(write_config)
lista=$(list_file "C,$SOURCE_FILE")

result=0
groovyz -Dorg.slf4j.simpleLogger -Dorg.slf4j.simpleLogger.defaultLogLevel=trace RunPuliziaCassaforteJsonLocal.groovy "$lista" "$ENV" "$BUILD_GROUP" "$config_file" || result=$?

if [ "$result" -eq 0 ]; then
    echo "Test passed: no errors"
else
    echo "Test failed: errors detected (exit code: $result)"
    exit 1
fi
