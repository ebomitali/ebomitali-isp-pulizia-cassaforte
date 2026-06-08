import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import java.nio.file.*

/**
 * Spock specification for {@link MacosFileService}, the macOS/local-dev adapter
 * of the {@link FileService} trait used during unit testing.
 *
 * <p>MacosFileService simulates a z/OS PDS (Partitioned Data Set) environment by
 * mapping z/OS-style dataset paths onto ordinary directories:
 * <pre>
 *   //DATASET.NAME(MEMBER)  →  &lt;baseDir&gt;/DATASET.NAME/MEMBER   (PDS member)
 *   //DATASET.NAME          →  &lt;baseDir&gt;/DATASET.NAME           (PDS = directory)
 *   /path/to/file           →  /path/to/file                     (USS path – passed through as-is)
 * </pre>
 *
 * <p>Each test receives a fresh temporary directory so that
 * tests are fully isolated and leave no files on disk after completion.
 */
class MacosFileServiceSpec extends Specification {

    /**
     * JUnit 5 temporary directory injected before each feature method.
     * Passed to {@link MacosFileService} as the root that stands in for the
     * z/OS filesystem root (normally {@code /tmp/zos-sim/} on USS).
     */
    @TempDir
    Path tempDir

    /**
     * Verifies the full lifecycle of a PDS member:
     * 1. A member that has not been created yet must NOT exist.
     * 2. Copying a source member to a destination creates the destination member.
     * 3. Deleting the destination member removes it so it no longer exists.
     *
     * The z/OS path {@code //TEST.DS.SRC(MEMBSRC)} is resolved to
     * {@code <tempDir>/TEST.DS.SRC/MEMBSRC} on disk, and
     * {@code //TEST.DS(MEMBER1)} is resolved to {@code <tempDir>/TEST.DS/MEMBER1}.
     */
    def "copy creates PDS member; delete removes it"() {
        given: "a MacosFileService instance rooted at tempDir, and a source PDS member on disk"
        def ops = new MacosFileService(tempDir.toString())
        // Create the source file that copy() will read from.
        def src = tempDir.resolve('TEST.DS.SRC/MEMBSRC')
        Files.createDirectories(src.parent)   // ensure parent directory (= PDS) exists
        Files.writeString(src, 'content')

        expect: "the destination member does not exist before the copy"
        !ops.exists('//TEST.DS(MEMBER1)')

        when: "the source member is copied to the destination dataset"
        ops.copy('//TEST.DS.SRC(MEMBSRC)', '//TEST.DS(MEMBER1)')

        then: "the destination member now exists"
        ops.exists('//TEST.DS(MEMBER1)')

        when: "the destination member is deleted"
        ops.delete('//TEST.DS(MEMBER1)')

        then: "the destination member no longer exists"
        !ops.exists('//TEST.DS(MEMBER1)')
    }

    /**
     * Verifies that {@link MacosFileService#list} returns the names of all files
     * (= PDS members) inside a directory (= PDS).
     *
     * The z/OS path {@code //TEST.DS.SRC} is treated as a PDS and maps to the
     * directory {@code <tempDir>/TEST.DS.SRC}.  Each file in that directory
     * represents one PDS member, and its filename is the member name returned
     * by {@code list()}.
     */
    def "list returns PDS member names"() {
        given: "a MacosFileService instance and one member pre-populated inside the PDS"
        def ops = new MacosFileService(tempDir.toString())
        def member = tempDir.resolve('TEST.DS.SRC/MEMBSRC')
        Files.createDirectories(member.parent)
        Files.writeString(member, 'content')

        expect: "the member name appears in the list result"
        ops.list('//TEST.DS.SRC').contains('MEMBSRC')
    }

    /**
     * Verifies that {@link MacosFileService#exists} handles plain USS file paths
     * (i.e. paths that do NOT start with {@code //}) by delegating directly to
     * {@link java.nio.file.Files#exists} without any dataset-path translation.
     *
     * This is the passthrough branch used when the caller already holds a real
     * filesystem path rather than a z/OS-style {@code //DATASET(MEMBER)} path.
     */
    def "exists works for USS file path passthrough"() {
        given: "a MacosFileService instance and a real file on disk"
        def ops = new MacosFileService(tempDir.toString())
        def ussFile = tempDir.resolve('uss-test.txt')
        Files.writeString(ussFile, 'x')   // create the file so it really exists

        expect: "exists() returns true for the real file and false for a non-existent path"
        ops.exists(ussFile.toString())
        !ops.exists(tempDir.resolve('nonexistent.txt').toString())
    }
}
