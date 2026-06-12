#!/bin/sh
# Test FileService implementations (JzosFileService, MacosFileService, UssFileService)
# Usage: ./test-fileservice.sh <properties-file> [fileOpsType]
#
# The properties file should contain:
#   fileOpsType=jzos|macos|uss
#   uxBasedir=/tmp/zos-sim  (required for macos and uss)

set -e

if [ $# -lt 1 ]; then
    echo "Usage: $0 <properties-file> [fileOpsType]"
    echo ""
    echo "Properties file should contain:"
    echo "  fileOpsType=jzos|macos|uss"
    echo "  uxBasedir=/path/to/sim (for macos/uss)"
    exit 1
fi

PROPS_FILE="$1"
OVERRIDE_TYPE="$2"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ ! -f "$PROPS_FILE" ]; then
    echo "Error: properties file not found: $PROPS_FILE"
    exit 1
fi

# Read properties
FILE_OPS_TYPE=$(grep '^fileOpsType=' "$PROPS_FILE" | cut -d= -f2)

if [ -z "$FILE_OPS_TYPE" ]; then
    echo "Error: fileOpsType not set in properties file"
    exit 1
fi

# Set up temp directory for test
TEMP_DIR="$SCRIPT_DIR/fileservice-test.$$"
mkdir -p "$TEMP_DIR"

cleanup() {
    rm -rf "$TEMP_DIR"
    rm -f "$SCRIPT_DIR/simplelogger.properties"
}
cleanup
#trap cleanup EXIT

# Create simulated z/OS datasets
TEST_PDS="$TEMP_DIR/TEST.PDS"
TEST_MEMBER1="$TEST_PDS/MEMBER1"
TEST_MEMBER2="$TEST_PDS/MEMBER2"
TEST_SEQ="$TEMP_DIR/TEST.SEQ"

mkdir -p "$TEST_PDS"

# Create test members
echo "test content 1" > "$TEST_MEMBER1"
echo "test content 2" > "$TEST_MEMBER2"
echo "sequential content" > "$TEST_SEQ"

echo "Test FileService: $FILE_OPS_TYPE"
echo "Temp directory: $TEMP_DIR"
echo "Test PDS: $TEST_PDS"
echo ""

# Get paths to Groovy sources
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../../" && pwd)"
echo "Project root: $PROJECT_ROOT"
STUBS_DIR="$(cd "$PROJECT_ROOT/stubs/build/classes/java/main" && pwd)"
JZOS_SRC="$(cd "$PROJECT_ROOT/jzos/src/main/groovy" && pwd)"
LIBRARY_SRC="$(cd "$PROJECT_ROOT/library/src/main/groovy" && pwd)"
SH_LIB="$(cd "$PROJECT_ROOT/jzos/build/sh-lib" && pwd 2>/dev/null || echo "")"

# Build classpath (include SLF4J provider for logging)
CLASSPATH="$STUBS_DIR:$JZOS_SRC:$LIBRARY_SRC:$SCRIPT_DIR"
if [ -d "$SH_LIB" ]; then
    CLASSPATH="$CLASSPATH:$SH_LIB/*"
fi

# Generate simplelogger.properties in temp dir for SLF4J to find it
cat > "$TEMP_DIR/simplelogger.properties" << 'PROPS_EOF'
org.slf4j.simpleLogger.defaultLogLevel=debug
org.slf4j.simpleLogger.showLogName=true
org.slf4j.simpleLogger.showThreadName=false
org.slf4j.simpleLogger.showDateTime=true
org.slf4j.simpleLogger.dateTimeFormat=HH:mm:ss.SSS
org.slf4j.simpleLogger.logFile=System.out
PROPS_EOF

# Create test Groovy script
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
        String uxBasedir = System.getProperty('uxBasedir')
        String tempDir = System.getProperty('tempDir')
        String fatSourceFile = System.getProperty('fatSourceFile')

        log.info("=" * 70)
        log.info("Testing FileService: $fileOpsType")
        log.info("=" * 70)
        log.debug("Using tempDir: $tempDir")
        log.debug("Using uxBasedir: ${uxBasedir ?: tempDir}")
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
            case 'macos':
                log.debug("Creating MacosFileService with baseDir: ${uxBasedir ?: tempDir}")
                fileService = fileServiceClass.getConstructor(String.class).newInstance(uxBasedir ?: tempDir)
                break
            case 'uss':
                log.debug("Creating UssFileService with baseDir: ${uxBasedir ?: tempDir}")
                fileService = fileServiceClass.getConstructor(String.class).newInstance(uxBasedir ?: tempDir)
                break
            case 'jzos':
                log.debug("Creating JzosFileService")
                fileService = fileServiceClass.getConstructor().newInstance()
                break
            default:
                throw new IllegalArgumentException("Unknown fileOpsType: $fileOpsType")
        }
        log.info("Instantiated FileService: ${fileService.class.simpleName}")
        log.debug("FileService class: ${fileService.class.name}")

        // Convert paths to z/OS format
        String pdsPath = "//${Paths.get(tempDir, 'TEST.PDS').fileName}(MEMBER1)"
        String seqPath = "//${Paths.get(tempDir, 'TEST.SEQ').fileName}"

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
        exists = fileService.exists("//${Paths.get(tempDir, 'TEST.PDS').fileName}(NOMEM)")
        log.info("  exists('//TEST.PDS(NOMEM)'): $exists")
        if (exists) {
            log.error("  FAILED: nonexistent member should not exist")
            throw new AssertionError("Nonexistent member should not exist")
        }
        log.info("  PASSED")

        // Test 3: list() PDS members
        log.info("[TEST 3] list(PDS)")
        String pdsOnlyPath = "//${Paths.get(tempDir, 'TEST.PDS').fileName}"
        List<String> members = fileService.list(pdsOnlyPath)
        log.info("  list('$pdsOnlyPath'): $members")
        if (!members.contains('MEMBER1') || !members.contains('MEMBER2')) {
            log.error("  FAILED: expected MEMBER1 and MEMBER2 in $members")
            throw new AssertionError("Members not found: MEMBER1, MEMBER2")
        }
        log.info("  PASSED")

        // Test 4: copy() member
        log.info("[TEST 4] copy(PDS member)")
        String copyDst = "//${Paths.get(tempDir, 'TEST.PDS').fileName}(MEMBER3)"
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

# Get path to FatFileService (generated by jzos subproject)
JZOS_DIR=$(cd "$PROJECT_ROOT/jzos" && pwd)
FAT_SOURCE="$JZOS_DIR/src/main/groovy/FatFileService.groovy"

if [ ! -f "$FAT_SOURCE" ]; then
    echo "Error: FatFileService.groovy not found. Run: ./gradlew jzos:generateFatFileService"
    exit 1
fi

# Run Groovy test script from temp dir so simplelogger.properties is found
echo "Running Groovy test script..."
cd "$TEMP_DIR"
CLASSPATH="$CLASSPATH" groovy \
    -Dfile.encoding=UTF-8 \
    -DfileOpsType="$FILE_OPS_TYPE" \
    -DuxBasedir="$TEMP_DIR" \
    -DtempDir="$TEMP_DIR" \
    -DfatSourceFile="$FAT_SOURCE" \
    -cp "$CLASSPATH" \
    "$TEST_SCRIPT"

RESULT=$?

if [ $RESULT -eq 0 ]; then
    echo ""
    echo "Test completed successfully."
else
    echo ""
    echo "Test failed with exit code $RESULT"
    exit 1
fi
