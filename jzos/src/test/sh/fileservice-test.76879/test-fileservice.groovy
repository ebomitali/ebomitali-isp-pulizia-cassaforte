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
