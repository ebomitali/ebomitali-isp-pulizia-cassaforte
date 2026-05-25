#!/bin/sh
# This is a posix compliant script

set -e

ENV="ATO"
BUILD_GROUP="ATO"


# Create temporary directory
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT
SOURCE_PATH="$TEMP_DIR/ATO/yn_r_01_ato_r1/src/mapasm/batch"
mkdir -p "$SOURCE_PATH"
touch "${SOURCE_PATH}/TESTMEM.SZFSSWG"

# Helper function: get fixture file path
# the script is under test/sh, the fixture is under test/resources/fixtures
fixture_file() {
    local name="$1"
    echo "$(cd "$(dirname "$0")" && pwd)/../resources/fixtures/$name"
}

# Helper function: write config file
write_config() {
    local config_file="PuliziaCassaforte.properties"
    : > "$config_file"
    
    echo "buildMapPath=$(fixture_file 'buildmap.json')" >> "$config_file"
    echo "uxBasedir=$TEMP_DIR" >> "$config_file"
    echo "rulesPath=$(fixture_file 'rules.csv')" >> "$config_file"
    echo "stageMapPath=$(fixture_file 'stage-map.csv')" >> "$config_file"
    echo "uxBasedir=$TEMP_DIR" >> "$config_file"
    
    echo "$config_file"
}

# Helper function: create list file
list_file() {
    local content="$1"
    local lista_file="$TEMP_DIR/lista.csv"
    echo "$content" > "$lista_file"
    echo "$lista_file"
}

# Main test: run with config file processes C action without error
echo "Running test: run with config file processes C action without error"

config_file=$(write_config)
lista=$(list_file "C,$SOURCE_PATH")

# Run PuliziaCassaforte implementation
# Assuming PuliziaCassaforte is available as a command or script
groovyz PuliziaCassaforte.groovy "$ENV" "$BUILD_GROUP" "$lista"
result=$?

if [ $result -eq 0 ]; then
    echo "Test passed: no errors"
else
    echo "Test failed: errors detected (exit code: $result)"
    exit 1
fi
