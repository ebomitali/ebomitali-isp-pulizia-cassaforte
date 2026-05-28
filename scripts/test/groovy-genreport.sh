#!/bin/sh
# Generate a sample PuliziaCassaforte run report covering three C-action scenarios:
#
#   SF1 — matches rule %CPYCOB*, member PGMCOBOL exists  → deleted (deletedElement set)
#   SF2 — file type NOFILTYP matches no rule             → empty matches array
#   SF3 — matches rule %CPYCOB*, member ABSENT not found → match recorded, deletedElement null
#
# Setup: repository segment yo_y_01_ato_r1 → pathLo=01, env=ATO → C1STAGE=X2A.
# Resolved library: LTM00.D9PX2A.PE000.LING.COB@@@@@.@@.COPY
#
# Requires: USS environment (groovyz, z/OS)
# Note: config key 'reportPath' matches the current fat-source version of PuliziaCassaforteImpl.
#       Regenerate FullPuliziaCassaforte.groovy from library sources to pick up 'reportOutputPath'.

set -e

ENV="ATO"
BUILD_GROUP="ATO"

# Source files for the three report scenarios
SF1="ATO/yo_y_01_ato_r1/src/COBOL/BATCH/PGMCOBOL.ACPYCOB"  # match + member exists → deleted
SF2="ATO/yo_y_01_ato_r1/src/OTHER/BATCH/SOMEOBJ.NOFILTYP"  # no rule matches → empty matches
SF3="ATO/yo_y_01_ato_r1/src/COBOL/BATCH/ABSENT.ACPYCOB"    # match + member absent → null deleted

# Resolved cassaforte library for ACPYCOB rule in ATO (C1STAGE=X2A)
COBLIB="LTM00.D9PX2A.PE000.LING.COB@@@@@.@@.COPY"

# Resolve script and sibling directories
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
FIXTURES_DIR=$(cd "$SCRIPT_DIR/resources/fixtures" && pwd)

# Temporary working directory
TEMP_DIR="temp/genreport.$$"
mkdir -p "$TEMP_DIR"
trap "rm -rf $TEMP_DIR" EXIT
echo "Working directory: $TEMP_DIR"

# Simulated cassaforte library:
#   PGMCOBOL present → SF1 member will be deleted
#   ABSENT not created → SF3 will produce deletedElement=null
mkdir -p "$TEMP_DIR/$COBLIB"
touch "$TEMP_DIR/$COBLIB/PGMCOBOL"

# Configuration properties
REPORT_PATH="$TEMP_DIR/puliziacassaforte-report.json"
CONFIG_FILE="$TEMP_DIR/PuliziaCassaforte.properties"
printf 'fileOpsType=%s\n'        "local"                         >  "$CONFIG_FILE"
printf 'buildMapClientType=%s\n' "json"                          >> "$CONFIG_FILE"
printf 'buildMapPath=%s\n'       "$FIXTURES_DIR/buildmap.json"   >> "$CONFIG_FILE"
printf 'uxBasedir=%s\n'          "$TEMP_DIR"                     >> "$CONFIG_FILE"
printf 'rulesPath=%s\n'          "$FIXTURES_DIR/rules.csv"       >> "$CONFIG_FILE"
printf 'stageMapPath=%s\n'       "$FIXTURES_DIR/stagemap.csv"    >> "$CONFIG_FILE"
printf 'reportPath=%s\n'         "$REPORT_PATH"                  >> "$CONFIG_FILE"

# Input list file
LISTA="$TEMP_DIR/lista.csv"
printf 'C,%s\n' "$SF1" >  "$LISTA"
printf 'C,%s\n' "$SF2" >> "$LISTA"
printf 'C,%s\n' "$SF3" >> "$LISTA"

echo "Files to process:"
cat "$LISTA"

result=0
groovy -Dorg.slf4j.simpleLogger.defaultLogLevel=debug \
    "$SCRIPT_DIR/GenReport.groovy" "$LISTA" "$ENV" "$BUILD_GROUP" "$CONFIG_FILE" || result=$?

echo "Generated report ($REPORT_PATH):"
cat "$REPORT_PATH"

if [ "$result" -eq 0 ]; then
    echo "Done."
else
    printf 'Error: GenReport exited with code %s\n' "$result" >&2
    exit 1
fi
