// Mainframe-only. Must be compiled and run with groovyz on z/OS USS.
// After upload to USS: chtag -tc IBM-1047 ZosFileOpsUSS.groovy
// ZFile javadoc https://www.ibm.com/docs/en/sdk-java-technology/8?topic=jzos-zfile
import com.ibm.jzos.ZFile
import com.ibm.jzos.ZFileException
import groovy.util.logging.Slf4j

/**
 * z/OS USS implementation of the {@link ZosFileOps} trait.
 *
 * Provides file-system operations (exists, delete, copy, list) that transparently
 * target either MVS datasets or USS HFS/zFS paths depending on the path prefix:
 *
 *   //DATASET.NAME          — sequential dataset or PDS without member
 *   //DATASET.NAME(MEMBER)  — PDS/PDSE member
 *   /u/path/to/file         — USS HFS/zFS file or directory (any path not starting with //)
 *
 * The // prefix convention mirrors the JCL DSN= notation used by IBM tools.
 * This class delegates MVS operations to the IBM JZOS {@link ZFile} API and
 * USS operations to standard {@link java.io.File}.
 *
 * Packaged separately into pulizia-cassaforte-zos.jar (requires IBM jars in libs/).
 * Never loaded by a local JVM — instantiated only inside the groovyz USS entry point.
 */
@Slf4j
class ZosFileOpsUSS implements ZosFileOps {

    /**
     * Checks whether a dataset or USS path exists.
     *
     * For MVS paths: queries the system catalog via {@link ZFile#dsExists(String)}.
     * For USS paths: delegates to {@link java.io.File#exists()}.
     *
     * @param path  MVS dataset reference (// prefix) or USS absolute path.
     * @return true if the dataset or file is found.
     */
    boolean exists(String path) {
        if (path.startsWith('//')) {
            // dsExists checks the catalog — does not require the dataset to be allocated
            def result = ZFile.dsExists(mvsName(path))
            log.debug("exists({}): {}", path, result)
            return result
        }
        def result = new File(path).exists()
        log.debug("exists({}): {}", path, result)
        result
    }

    /**
     * Deletes a dataset member, a sequential dataset, or a USS file.
     *
     * For MVS paths: builds the ZFile name spec (DSN or DSN(MEMBER)) and calls
     * {@link ZFile#remove(String)}, which maps to the z/OS remove() system call.
     * For USS paths: delegates to {@link java.io.File#delete()}.
     *
     * @param path  MVS dataset reference (// prefix) or USS absolute path.
     * @throws ZFileException if the MVS remove fails (e.g. dataset in use, not found).
     */
    void delete(String path) {
        log.debug("delete({})", path)
        if (path.startsWith('//')) {
            def (dsn, member) = parseDsn(path)
            // Build the ZFile-style spec: DSN(MEMBER) for PDS members, plain DSN otherwise
            def spec = member ? "${dsn}(${member})" : dsn
            ZFile.remove("//'${spec}'")
            return
        }
        new File(path).delete()
    }

    /**
     * Copies a dataset (or member) to another dataset (or member), or copies a USS file.
     *
     * MVS-to-MVS copy: opens source with {@code rb,type=record} and destination with
     * {@code wb,type=record} so that logical z/OS records are preserved exactly.
     * Reads and writes in chunks of 32760 bytes — the maximum LRECL for RECFM=VB records
     * on z/OS (0x7FF8). Both files are closed in finally blocks to avoid dataset locks
     * even if an exception occurs mid-transfer.
     *
     * USS copy: reads all bytes from src and writes them to dst atomically via Groovy's
     * {@link java.io.File#setBytes(byte[])} assignment — suitable for small USS files.
     *
     * Mixed USS→MVS or MVS→USS is not supported; both paths must be of the same type.
     *
     * @param src  Source: MVS dataset reference (// prefix) or USS path.
     * @param dst  Destination: MVS dataset reference (// prefix) or USS path.
     * @throws ZFileException if the MVS open, read, or write fails.
     */
    void copy(String src, String dst) {
        log.debug("copy({} -> {})", src, dst)
        if (src.startsWith('//') && dst.startsWith('//')) {
            def (srcDsn, srcMember) = parseDsn(src)
            def (dstDsn, dstMember) = parseDsn(dst)
            def srcSpec = srcMember ? "${srcDsn}(${srcMember})" : srcDsn
            def dstSpec = dstMember ? "${dstDsn}(${dstMember})" : dstDsn
            // rb/wb with type=record: ZFile reads/writes complete logical records,
            // preserving RECFM and blocking without EBCDIC↔ASCII translation
            def srcFile = new ZFile("//'${srcSpec}'", 'rb,type=record')
            try {
                def dstFile = new ZFile("//'${dstSpec}'", 'wb,type=record')
                try {
                    // 32760 = max VB block payload (0x7FF8); safe upper bound for any LRECL
                    def buf = new byte[32760]
                    int len
                    while ((len = srcFile.read(buf)) >= 0) {
                        dstFile.write(buf, 0, len)
                    }
                } finally {
                    dstFile.close()
                }
            } finally {
                srcFile.close()
            }
            return
        }
        // USS file copy
        new File(dst).bytes = new File(src).bytes
    }

    /**
     * Lists members of a PDS/PDSE or files in a USS directory.
     *
     * For MVS paths: calls {@link ZFile#listMembers(String)} which returns the directory
     * entries of the PDS/PDSE identified by the dataset name (member component ignored).
     * For USS paths: delegates to {@link java.io.File#list()} for directory entries.
     *
     * @param container  MVS PDS/PDSE reference (// prefix) or USS directory path.
     * @return list of member or file names; empty list if the container is empty or null.
     */
    List<String> list(String container) {
        if (container.startsWith('//')) {
            // listMembers returns null if the PDS has no members — coerce to empty list
            def result = ZFile.listMembers(mvsName(container))?.toList() ?: []
            log.debug("list({}): {} member(s)", container, result.size())
            return result
        }
        def result = new File(container).list()?.toList() ?: []
        log.debug("list({}): {} entry(s)", container, result.size())
        result
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    /**
     * Extracts the dataset name from an MVS path, discarding any member component.
     * Used for catalog-level operations (exists) where only the DSN is needed.
     *
     * Examples:
     *   //MY.DATASET          → MY.DATASET
     *   //MY.DATASET(MEMBER)  → MY.DATASET
     *
     * @param path  MVS path with // prefix.
     * @return dataset name without member.
     */
    private String mvsName(String path) {
        def inner = path.substring(2)
        def m = (inner =~ /^(.+?)\((.+?)\)$/)
        m.matches() ? m.group(1) : inner
    }

    /**
     * Parses an MVS path into a [dsn, member] pair.
     * Returns [dsn, null] when no member component is present.
     *
     * Examples:
     *   //MY.DATASET          → ['MY.DATASET', null]
     *   //MY.DATASET(MEMBER)  → ['MY.DATASET', 'MEMBER']
     *
     * @param path  MVS path with // prefix.
     * @return two-element list [datasetName, memberName]; memberName is null if absent.
     */
    private List<String> parseDsn(String path) {
        def inner = path.substring(2)
        def m = (inner =~ /^(.+?)\((.+?)\)$/)
        m.matches() ? [m.group(1), m.group(2)] : [inner, null]
    }
}
