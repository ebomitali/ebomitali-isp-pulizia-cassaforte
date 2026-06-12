#!/bin/sh
# Posix and USS compatible shell script to test PuliziaCassaforte.groovy with a specific configuration and input file.
# Test script
# - fileOpsType: local
# - buildMapClientType: db2
# - two file match rules with build map matches two files

set -e

ENV="ATO"
BUILD_GROUP="ATO"
# Check that DBB_CONF environment variable is set, as it's required by the db2 client
if [ -z "$DBB_CONF" ]; then
    echo "Error: DBB_CONF environment variable is not set. It is required for db2 client configuration."
    exit 1
fi

# Create temporary directory
TEMP_DIR="tmp/run-puliziacassaforte.$$"
mkdir -p "$TEMP_DIR"
trap "rm -rf $TEMP_DIR" EXIT

SF1="ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YO8AMADD.SJCLINP"
ZOSDS="U0G9700.PULCAS.PE000.@@@@.JCL"

# Allocate PDS
tsocmd "ALLOC DSN('${ZOSDS}') DIR(5) TRACKS SPACE(1,1) RECFM(F,B) LRECL(80) BLKSIZE(3200) DSORG(PO) NEW CATALOG"
echo "PDS allocated: //'${ZOSDS}'"

# Create members
for MEMBER in YO8AMADD; do
    cp /dev/null "//'${ZOSDS}(${MEMBER})'"
    echo "Member created: //'${ZOSDS}(${MEMBER})'"
done

# Helper: absolute path to a resource
# script lives under test; resource files live under test/resources
resource_file() {
    echo "$(cd "$(dirname "$0")" && pwd)/resources/$1"
}

# Helper: write config properties file to TEMP_DIR, print its path
write_config() {
    _cfg="PuliziaCassaforte.properties"
    # optional, may be omitted
    printf 'fileOpsType=%s\n' "zos"                                 >  "$_cfg"
    # optional, may be omitted
    printf 'buildMapClientType=%s\n' "db2"                          >> "$_cfg"
    printf 'userId=%s\n' "GADBB01"                                  >> "$_cfg"
    printf 'pwFilePath=%s\n' "$DBB_CONF/DB01PSW.txt"                >> "$_cfg"
    printf 'db2ConfigPath=%s\n' "$DBB_CONF/db2Connection.conf"      >> "$_cfg"
    printf 'rulesPath=%s\n'    "$(resource_file 'rulest4.csv')"     >> "$_cfg"
    printf 'stageMapPath=%s\n' "$(resource_file 'stagemap.csv')"    >> "$_cfg"
    # Note hlq, userId, pwFilePath, db2ConfigPath required when using db2 client
    echo "$_cfg"
}

# Helper: write list file to TEMP_DIR, print its path
list_file() {
    _lista="$TEMP_DIR/lista.csv"
    for SF in "$SF1"; do
        printf 'C,%s\n' "$SF" >> "$_lista"
    done
    echo "$_lista"
}

config_file=$(write_config)
lista=$(list_file)
echo "File to be processed:"
cat "$lista"
echo "Directory content before script execution:"
tsocmd "LISTDS '${ZOSDS}' MEMBERS" 2>/dev/null || true

result=0
# Groovy script reads also PuliziaCassaforte.properties from current directory
groovyz -Dorg.slf4j.simpleLogger -Dorg.slf4j.simpleLogger.defaultLogLevel=debug RunPuliziaCassaforte.groovy "$lista" "$ENV" "$BUILD_GROUP" || result=$?

# Show dir content
echo "Directory content after script execution:"
tsocmd "LISTDS '${ZOSDS}' MEMBERS" 2>/dev/null || true

if [ "$result" -eq 0 ]; then
    echo "Test passed: no errors"
else
    echo "Test failed: errors detected (exit code: $result)"
    exit 1
fi
