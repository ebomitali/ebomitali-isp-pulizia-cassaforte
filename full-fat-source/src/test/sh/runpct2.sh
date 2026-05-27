#!/bin/sh
# Posix and USS compatible shell script to test PuliziaCassaforte.groovy with a specific configuration and input file.
# Test script
# - fileOpsType: local
# - buildMapClientType: json
# - one file match rules, the other does not
# No Build Map rules

set -e

ENV="ATO"
BUILD_GROUP="ATO"

# Create temporary directory
TEMP_DIR="${TMPDIR:-/tmp}/run-puliziacassaforte.$$"
mkdir -p "$TEMP_DIR"
trap "rm -rf $TEMP_DIR" EXIT

SF1="ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLCA7/YO810BDD.SJCLCA7"
SF2="ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YO8AMBDD.SJCLINP"
SF3="ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLITT/YO84XS1.SJCLITT"

# Create simulated zos env
ZOSDS="//'U0G9700.D9PX1A.PE000.@@@@.JCL'"
ZODDSSIM="$TEMP_DIR/U0G9700.D9PX1A.PE000.@@@@.JCL"
mkdir -p "$ZODDSSIM"

# loop on [SF1, SF2, SF3] to create directory and file for each
for SF in "$SF1" "$SF2" "$SF3"; do
    # extract basename without extension, e.g. YO810BDD from YO810BDD.SJCLCA7
    BASENAME=$(basename "$SF" | cut -d. -f1)
    # create file with name $BASENAME in $ZODDSSIM, e.g. $ZODDSSIM/YO810BDD
    touch "$ZODDSSIM/$SF"
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
    printf 'rulesPath=%s\n'    "$(resource_file 'rulest2.csv')"   >> "$_cfg"
    printf 'stageMapPath=%s\n' "$(resource_file 'stage-map.csv')" >> "$_cfg"
    # Note hlq, userId, pwFilePath, db2ConfigPath required when using db2 client
    echo "$_cfg"
}

# Helper: write list file to TEMP_DIR, print its path
list_file() {
    _lista="$TEMP_DIR/lista.csv"
    for SF in "$SF1" "$SF2" "$SF3"; do
        BASENAMENOEXT=$(basename "$SF" | cut -d. -f1)
        printf '%s\n' "$BASENAMENOEXT" > "$_lista"
    done
    echo "$_lista"
}

config_file=$(write_config)
lista=$(list_file "C,$SF1
C,$SF2")


result=0
# Groovy script reads also PuliziaCassaforte.properties from current directory
groovyz -Dorg.slf4j.simpleLogger -Dorg.slf4j.simpleLogger.defaultLogLevel=debug RunPuliziaCassaforte.groovy "$lista" "$ENV" "$BUILD_GROUP" || result=$?

if [ "$result" -eq 0 ]; then
    echo "Test passed: no errors"
else
    echo "Test failed: errors detected (exit code: $result)"
    exit 1
fi
