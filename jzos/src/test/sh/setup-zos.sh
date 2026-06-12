#!/bin/sh
# Setup z/OS datasets and create test members
# Creates PDS and sequential datasets, then populates members via ZFile
#
# Usage: setup-zos.sh <properties-file>
#
# Properties file should contain:
#   fileOpsType=jzos

set -e

if [ $# -lt 1 ]; then
    echo "Usage: $0 <properties-file>"
    exit 1
fi

PROPS_FILE="$1"
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

if [ ! -f "$PROPS_FILE" ]; then
    echo "Error: properties file not found: $PROPS_FILE"
    exit 1
fi

# Read properties
FILE_OPS_TYPE=$(grep '^fileOpsType=' "$PROPS_FILE" | cut -d= -f2)

if [ -z "$FILE_OPS_TYPE" ] || [ "$FILE_OPS_TYPE" != "jzos" ]; then
    echo "Error: fileOpsType must be 'jzos' for z/OS setup"
    exit 1
fi

# Get user ID for dataset naming
USERID=${LOGNAME:-${USER:-TESTUSER}}
USERID=$(echo "$USERID" | tr '[:lower:]' '[:upper:]')

TESTPDS="${USERID}.TEST.PDS"
TESTSEQ="${USERID}.TEST.SEQ"

echo "Setup z/OS datasets for FileService test"
echo "Test PDS: $TESTPDS"
echo "Test Sequential: $TESTSEQ"
echo ""

# Try to allocate datasets via TSOCMD or mvscmd
echo "Allocating z/OS datasets..."

# Check which command is available
ALLOC_CMD=""
if command -v tsocmd >/dev/null 2>&1; then
    ALLOC_CMD="tsocmd"
elif command -v mvscmd >/dev/null 2>&1; then
    ALLOC_CMD="mvscmd"
else
    echo "Warning: TSOCMD/mvscmd not found. Datasets must be pre-allocated."
    echo "To pre-allocate, run:"
    echo "  ALLOCATE DATASET('$TESTPDS') NEW CATALOG DSORG(PO) RECFM(F,B) LRECL(80) SPACE(1,1) DIR(10)"
    echo "  ALLOCATE DATASET('$TESTSEQ') NEW CATALOG DSORG(PS) RECFM(F,B) LRECL(80) SPACE(1,1)"
    exit 1
fi

# Allocate PDS
$ALLOC_CMD "ALLOCATE DATASET('$TESTPDS') NEW CATALOG DSORG(PO) RECFM(F,B) LRECL(80) BLKSIZE(3120) SPACE(1,1) DIR(10)" 2>&1
if [ $? -ne 0 ]; then
    echo "Warning: PDS allocation may have failed"
fi

# Allocate sequential dataset
$ALLOC_CMD "ALLOCATE DATASET('$TESTSEQ') NEW CATALOG DSORG(PS) RECFM(F,B) LRECL(80) SPACE(1,1)" 2>&1
if [ $? -ne 0 ]; then
    echo "Warning: Sequential dataset allocation may have failed"
fi

echo "Datasets allocated. Creating members via ZFile..."

# Create temporary Groovy script to populate members
TEMP_DIR="setup-zos.$$"
mkdir -p "$TEMP_DIR"

SETUP_SCRIPT="$TEMP_DIR/setup-members.groovy"

cat > "$SETUP_SCRIPT" << 'GROOVY_EOF'
import com.ibm.jzos.ZFile
import com.ibm.jzos.ZFileException

String testPds = System.getProperty('testPds')
String testSeq = System.getProperty('testSeq')

println("Creating test members in $testPds...")

try {
    // Create MEMBER1 with sample data
    ZFile m1 = new ZFile("//'$testPds(MEMBER1)'", 'wb,type=record')
    m1.write("member 1 content\n".getBytes())
    m1.close()
    println("Created MEMBER1")
} catch (ZFileException e) {
    println("Failed to create MEMBER1: ${e.message}")
    System.exit(1)
}

try {
    // Create MEMBER2 with sample data
    ZFile m2 = new ZFile("//'$testPds(MEMBER2)'", 'wb,type=record')
    m2.write("member 2 content\n".getBytes())
    m2.close()
    println("Created MEMBER2")
} catch (ZFileException e) {
    println("Failed to create MEMBER2: ${e.message}")
    System.exit(1)
}

println("Setup complete - members created successfully")
GROOVY_EOF

# Run groovyz to create members
groovyz \
    -Dfile.encoding=IBM-1047 \
    -DtestPds="$TESTPDS" \
    -DtestSeq="$TESTSEQ" \
    "$SETUP_SCRIPT"

RESULT=$?

# Cleanup temp script
rm -rf "$TEMP_DIR"

if [ $RESULT -eq 0 ]; then
    echo ""
    echo "Setup completed successfully."
    exit 0
else
    echo ""
    echo "Setup failed with exit code $RESULT"
    exit 1
fi
