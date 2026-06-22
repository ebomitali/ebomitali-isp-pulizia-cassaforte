#!/bin/sh
# Posix and USS compatible shell script to test PuliziaCassaforte.groovy with a specific configuration and input file.
# Test script
# - fileOpsType: zos (real z/OS PDS operations via ZFile)
# - buildMapClientType: json
# - SF1 (SJCLCA7) and SF2 (SJCLINP) match rules and are deleted; SF3 (SJCLITT) does not match

set -e

ENV="ATO"
BUILD_GROUP="ATO"

SF1="ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLCA7/YO810BDD.SJCLCA7"
SF2="ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YO8AMBDD.SJCLINP"
SF3="ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLITT/YO84XS1.SJCLITT"

ZOSDS="U0G9700.D9PX1A.PE000.@@@@.JCL"

# Create temporary directory for list file and config
TEMP_DIR="${TMPDIR:-/tmp}/run-puliziacassaforte.$$"
mkdir -p "$TEMP_DIR"
trap 'rm -rf "$TEMP_DIR"; tsocmd "DELETE '"'"'${ZOSDS}'"'"'" 2>/dev/null || true' EXIT

# Allocate PDS
tsocmd "ALLOC DSN('${ZOSDS}') DIR(5) TRACKS SPACE(1,1) RECFM(F,B) LRECL(80) BLKSIZE(3200) DSORG(PO) NEW CATALOG"
echo "PDS allocated: //'${ZOSDS}'"

# Create members
for MEMBER in YO810BDD YO8AMBDD YO84XS1; do
    cp /dev/null "//'${ZOSDS}(${MEMBER})'"
    echo "Member created: //'${ZOSDS}(${MEMBER})'"
done

# Helper: absolute path to a resource
# script lives under test/sh; resource files live under test/resources
resource_file() {
    echo "$(cd "$(dirname "$0")" && pwd)/resources/$1"
}

# Helper: write config properties file to current directory, print its path
write_config() {
    _cfg="PuliziaCassaforte.properties"
    printf 'fileOpsType=%s\n'        "zos"                              >  "$_cfg"
    printf 'buildMapClientType=%s\n' "json"                             >> "$_cfg"
    printf 'buildMapPath=%s\n'       "$(resource_file 'buildmap.json')" >> "$_cfg"
    printf 'rulesPath=%s\n'          "$(resource_file 'rulest2.csv')"   >> "$_cfg"
    printf 'stageMapPath=%s\n'       "$(resource_file 'stagemap.csv')"  >> "$_cfg"
    echo "$_cfg"
}

# Helper: write list file to TEMP_DIR, print its path
list_file() {
    _lista="$TEMP_DIR/lista.csv"
    for SF in "$SF1" "$SF2" "$SF3"; do
        printf 'C,%s\n' "$SF" >> "$_lista"
    done
    echo "$_lista"
}

config_file=$(write_config)
lista=$(list_file)

echo "Files to be processed:"
cat "$lista"
echo "PDS members before execution:"
tsocmd "LISTDS '${ZOSDS}' MEMBERS" 2>/dev/null || true

result=0
groovyz -Dorg.slf4j.simpleLogger -Dorg.slf4j.simpleLogger.defaultLogLevel=debug RunPuliziaCassaforte.groovy "$lista" "$ENV" "$BUILD_GROUP" || result=$?

echo "PDS members after execution:"
tsocmd "LISTDS '${ZOSDS}' MEMBERS" 2>/dev/null || true

# Verify YO84XS1 (SJCLITT — no matching rule) was NOT deleted
if tsocmd "LISTDS '${ZOSDS}' MEMBERS" 2>/dev/null | grep -q "YO84XS1"; then
    echo "OK: YO84XS1 still present (SJCLITT matched no rule)"
else
    echo "FAIL: YO84XS1 was deleted but should not have been"
    result=1
fi

if [ "$result" -eq 0 ]; then
    echo "Test passed: no errors"
else
    echo "Test failed: errors detected (exit code: $result)"
    exit 1
fi
