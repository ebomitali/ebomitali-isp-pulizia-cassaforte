#!/bin/sh
# Test FileService implementations on z/OS USS
# Uses groovyz to test JzosFileService against real MVS datasets
# POSIX shell compliant, USS compatible
#
# Usage: zos-test.sh <properties-file>
#
# Prerequisites: Run setup-zos.sh first to allocate datasets and create members
#
# Properties file should contain:
#   fileOpsType=jzos

set -e

# Validate arguments
if [ $# -lt 1 ]; then
    echo "Usage: $0 <properties-file>"
    echo ""
    echo "Prerequisites: run setup-zos.sh <properties-file> first"
    echo ""
    echo "Properties file should contain:"
    echo "  fileOpsType=jzos"
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
    echo "Error: fileOpsType must be 'jzos' for z/OS tests"
    exit 1
fi

# Get user ID for dataset naming
USERID=${LOGNAME:-${USER:-TESTUSER}}
USERID=$(echo "$USERID" | tr '[:lower:]' '[:upper:]')

# Set up temp directory in USS for test environment
TEMP_DIR="fileservice-test.$$"
mkdir -p "$TEMP_DIR"

cleanup() {
    # rm -rf "$TEMP_DIR"
    :
}
#trap cleanup EXIT

# Dataset names (must match setup-zos.sh)
TESTPDS="${USERID}.TEST.PDS"
TESTSEQ="${USERID}.TEST.SEQ"

echo "Test FileService: $FILE_OPS_TYPE"
echo "Temp directory: $TEMP_DIR"
echo "Test PDS: $TESTPDS"
echo "Test Sequential: $TESTSEQ"
echo ""

# Generate simplelogger.properties
cat > "$TEMP_DIR/simplelogger.properties" << 'PROPS_EOF'
org.slf4j.simpleLogger.defaultLogLevel=debug
org.slf4j.simpleLogger.showLogName=true
org.slf4j.simpleLogger.showThreadName=false
org.slf4j.simpleLogger.showDateTime=true
org.slf4j.simpleLogger.dateTimeFormat=HH:mm:ss.SSS
org.slf4j.simpleLogger.logFile=System.out
PROPS_EOF

# Create Groovy test script
TEST_SCRIPT="$TEMP_DIR/test-fileservice.groovy"

cat > "$TEST_SCRIPT" << 'GROOVY_EOF'
import java.nio.file.Paths
import groovy.util.logging.Slf4j
import groovy.lang.GroovyClassLoader

@Slf4j
class FileServiceTest {
    static void main(String[] args) {
        new FileServiceTest().run()
    }

    void run() {
        // Get system properties
        String fileOpsType = System.getProperty('fileOpsType')
        String testPds = System.getProperty('testPds')
        String testSeq = System.getProperty('testSeq')
        String tempDir = System.getProperty('tempDir')
        String fatSourceFile = System.getProperty('fatSourceFile')

        log.info("=" * 70)
        log.info("Testing FileService: $fileOpsType on z/OS")
        log.info("=" * 70)
        log.debug("Test PDS: $testPds")
        log.debug("Test Sequential: $testSeq")
        log.debug("Temp directory: $tempDir")
        log.debug("Loading fat source from: $fatSourceFile")

        // Load FatFileService dynamically using GroovyClassLoader
        GroovyClassLoader gcl = new GroovyClassLoader(this.class.classLoader)
        File fatSource = new File(fatSourceFile)

        if (!fatSource.exists()) {
            throw new FileNotFoundException("FatFileService not found: $fatSourceFile")
        }

        Class<?> fatServiceClass = gcl.parseClass(fatSource)
        log.debug("Parsed FatFileService from: $fatSourceFile")

        // Load the appropriate FileService implementation class
        Class<?> fileServiceClass = gcl.loadClass(fileOpsType.capitalize() + 'FileService')
        log.debug("Loaded class: ${fileServiceClass.name}")

        // Instantiate the FileService
        Object fileService

        switch (fileOpsType) {
            case 'jzos':
                log.debug("Creating JzosFileService for z/OS")
                fileService = fileServiceClass.getConstructor().newInstance()
                break
            default:
                throw new IllegalArgumentException("Unknown fileOpsType: $fileOpsType")
        }
        log.info("Instantiated FileService: ${fileService.class.simpleName}")
        log.debug("FileService class: ${fileService.class.name}")

        // Convert to z/OS format
        String pdsPath = "//$testPds(MEMBER1)"
        String seqPath = "//$testSeq"

        // Test 1: exists() on PDS member
        log.info("[TEST 1] exists(PDS member)")
        boolean exists = fileService.exists(pdsPath)
        log.info("  exists('$pdsPath'): $exists")
        if (!exists) {
            log.error("  FAILED: member should exist")
            throw new AssertionError("Member should exist: $pdsPath")
        }
        log.info("  PASSED")

        // Test 2: exists() on nonexistent member
        log.info("[TEST 2] exists(nonexistent member)")
        exists = fileService.exists("//$testPds(NOMEM)")
        log.info("  exists('//$testPds(NOMEM)'): $exists")
        if (exists) {
            log.error("  FAILED: nonexistent member should not exist")
            throw new AssertionError("Nonexistent member should not exist")
        }
        log.info("  PASSED")

        // Test 3: list() PDS members
        log.info("[TEST 3] list(PDS)")
        List<String> members = fileService.list(pdsPath)
        log.info("  list('//$testPds'): $members")
        if (!members.contains('MEMBER1') || !members.contains('MEMBER2')) {
            log.error("  FAILED: expected MEMBER1 and MEMBER2 in $members")
            throw new AssertionError("Members not found: MEMBER1, MEMBER2")
        }
        log.info("  PASSED")

        // Test 4: copy() member
        log.info("[TEST 4] copy(PDS member)")
        String copyDst = "//$testPds(MEMBER3)"
        fileService.copy(pdsPath, copyDst)
        exists = fileService.exists(copyDst)
        log.info("  Copied $pdsPath -> $copyDst")
        log.info("  exists('$copyDst'): $exists")
        if (!exists) {
            log.error("  FAILED: copied member should exist")
            throw new AssertionError("Copied member should exist: $copyDst")
        }
        log.info("  PASSED")

        // Test 5: delete() member
        log.info("[TEST 5] delete(PDS member)")
        fileService.delete(copyDst)
        exists = fileService.exists(copyDst)
        log.info("  Deleted $copyDst")
        log.info("  exists('$copyDst'): $exists")
        if (exists) {
            log.error("  FAILED: deleted member should not exist")
            throw new AssertionError("Deleted member should not exist: $copyDst")
        }
        log.info("  PASSED")

        log.info("=" * 70)
        log.info("All tests passed!")
        log.info("=" * 70)
    }
}

FileServiceTest.main(args)
GROOVY_EOF

# Setup datasets and members
echo "Setting up z/OS datasets and test members..."
"$SCRIPT_DIR/setup-zos.sh" "$PROPS_FILE"
if [ $? -ne 0 ]; then
    echo "Setup failed"
    exit 1
fi

# Get path same directory as script
FAT_SOURCE="$SCRIPT_DIR/FatFileService.groovy"

if [ ! -f "$FAT_SOURCE" ]; then
    echo "Error: FatFileService.groovy not found. Run: ./gradlew jzos:generateFatFileService"
    exit 1
fi

# Run Groovy test script from temp dir
echo ""
echo "Running Groovyz test script via groovyz..."
cd "$TEMP_DIR"

# Run groovyz (z/OS Groovy) with FatFileService
# Use basename since already cd'd into TEMP_DIR
groovyz \
    -Dfile.encoding=IBM-1047 \
    -DfileOpsType="jzos" \
    -DtestPds="$TESTPDS" \
    -DtestSeq="$TESTSEQ" \
    -DtempDir="$TEMP_DIR" \
    -DfatSourceFile="$FAT_SOURCE" \
    -Dorg.slf4j.simpleLogger \
    -cp "." \
    "test-fileservice.groovy"

RESULT=$?

echo ""
echo "Test datasets created by setup-zos.sh remain allocated for reuse."
echo "To clean up manually, run in TSO:"
echo "  DELETE '$TESTPDS'"
echo "  DELETE '$TESTSEQ'"

if [ $RESULT -eq 0 ]; then
    echo ""
    echo "Test completed successfully."
    exit 0
else
    echo ""
    echo "Test failed with exit code $RESULT"
    exit 1
fi
