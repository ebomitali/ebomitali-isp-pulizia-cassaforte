import groovy.util.logging.Slf4j
import java.nio.file.*

/**
 * USS Unix-filesystem adapter of the {@link FileService} trait.
 *
 * <p>Simulates a z/OS PDS environment by mapping z/OS-style dataset paths onto ordinary
 * directories under a configurable {@code baseDir} (default: {@code /tmp/zos-sim/}):
 * <pre>
 *   //DATASET.NAME(MEMBER)  →  &lt;baseDir&gt;/DATASET.NAME/MEMBER   (PDS member = file)
 *   //DATASET.NAME          →  &lt;baseDir&gt;/DATASET.NAME           (PDS = directory)
 * </pre>
 *
 * <p>Only z/OS syntax (//DSN(MEMBER)) is supported. Unix paths are rejected with
 * {@link IllegalArgumentException}. Uses {@link java.nio.file.Files} for all I/O — no IBM/DBB dependencies.
 * Suitable for running on USS HFS/zFS without the IBM JZOS jar.
 *
 * @see FileService
 * @see JzosFileService
 */
@Slf4j
class UssFileService implements FileService {

    final String baseDir

    UssFileService(String baseDir = '/tmp/zos-sim') {
        this.baseDir = baseDir
        log.debug("UssFileService initialized with baseDir: {}", baseDir)
    }

    /**
     * Parses a z/OS path into [dsn, member].
     * Member is null if path has no (MEMBER) component.
     * //DSN(MEMBER) → ['DSN', 'MEMBER']
     * //DSN → ['DSN', null]
     * Non-z/OS paths throw IllegalArgumentException.
     */
    private List<String> parseDsnMember(String zosPath) {
        if (!zosPath.startsWith('//')) {
            throw new IllegalArgumentException("Path must use z/OS syntax (//DSN or //DSN(MEMBER)), got: ${zosPath}")
        }
        def inner = zosPath.substring(2)
        def m = (inner =~ /^(.+?)\((.+?)\)$/) // matches DATASET(MEMBER) pattern
        m.matches() ? [m.group(1), m.group(2)] : [inner, null]
    }

    private Path resolve(String dsn, String member) {
        member ? Paths.get(baseDir, dsn, member) : Paths.get(baseDir, dsn)
    }

    boolean exists(String path) {
        def (dsn, member) = parseDsnMember(path)
        if (!member) {
            throw new IllegalArgumentException("exists() requires a member reference (//DSN(MEMBER)), got: ${path}")
        }
        def result = Files.exists(resolve(dsn, member))
        log.debug("exists({}): {}", path, result)
        result
    }

    void delete(String path) {
        log.debug("delete({})", path)
        def (dsn, member) = parseDsnMember(path)
        if (!member) {
            throw new IllegalArgumentException("delete() requires a member reference (//DSN(MEMBER)), got: ${path}")
        }
        Files.deleteIfExists(resolve(dsn, member))
    }

    void copy(String src, String dst) {
        log.debug("copy({} -> {})", src, dst)
        def (srcDsn, srcMember) = parseDsnMember(src)
        def (dstDsn, dstMember) = parseDsnMember(dst)

        if (!srcMember || !dstMember) {
            throw new IllegalArgumentException("copy() requires member references (//DSN(MEMBER)), got: src=${src} dst=${dst}")
        }

        if (srcMember != dstMember) {
            log.warn("copy({} -> {}): source member '{}' differs from destination member '{}'", src, dst, srcMember, dstMember)
        }

        def dstPath = resolve(dstDsn, dstMember)
        Files.createDirectories(dstPath.parent)
        def srcPath = resolve(srcDsn, srcMember)
        if (Files.exists(srcPath)) {
            Files.copy(srcPath, dstPath, StandardCopyOption.REPLACE_EXISTING)
        } else {
            Files.createFile(dstPath) // src absent → create empty dst, simulating a z/OS member with no content
        }
    }

    List<String> list(String container) {
        def (dsn, member) = parseDsnMember(container)
        // list() accepts member but ignores it, using only DSN
        def dir = resolve(dsn, null)
        if (!Files.isDirectory(dir)) {
            log.debug("list({}): not a directory", container)
            return []
        }
        def result = dir.toFile().list()?.toList() ?: []
        log.debug("list({}): {} member(s)", container, result.size())
        result
    }

}
