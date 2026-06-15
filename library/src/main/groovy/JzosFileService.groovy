// Mainframe-only. Must be compiled and run with groovyz on z/OS USS.
// After upload to USS: chtag -tc IBM-1047 JzosFileService.groovy
// ZFile javadoc https://www.ibm.com/docs/en/sdk-java-technology/8?topic=jzos-zfile
import com.ibm.jzos.PdsDirectory
import com.ibm.jzos.PdsDirectory.MemberInfo
import com.ibm.jzos.ZFile
import com.ibm.jzos.ZFileException
import groovy.util.logging.Slf4j

import java.util.concurrent.atomic.AtomicInteger

/**
 * z/OS PDS/PDSE member operations, implementing the member-only {@link FileService} trait.
 *
 * Every operation acts on a partitioned dataset member. References use the form:
 *
 *   //DSN(MEMBER)   or   DSN(MEMBER)     — a single member (exists / delete / copy)
 *   //DSN           or   DSN             — the library itself (list)
 *
 * A leading {@code //} is optional and stripped; the dataset name is always treated as
 * fully qualified (no userid prefix is added). exists / delete / copy reject a reference
 * without a member component, since this service does not perform dataset-level operations.
 *
 * Disposition: every path here is either a directory read or a member STOW, so DISP=SHR is
 * always the correct choice.
 *   - exists / list / copy-source  -> input open => SHR implicitly.
 *   - delete / copy-target         -> the library is pre-allocated SHR and the member is
 *                                     driven through a //DD: reference, so LE reuses the SHR
 *                                     allocation instead of taking its default exclusive
 *                                     (OLD) lock. STOW serializes the directory update.
 *
 * Compiled against JZOS stubs (compileOnly) so it builds locally without IBM jars.
 * At runtime on z/OS USS the real JZOS classes must be on the classpath.
 */
@Slf4j
class JzosFileService implements FileService {

    /** 32760 = max payload of a RECFM=VB block (0x7FF8); safe upper bound for any LRECL. */
    private static final int MAX_RECORD = 32760

    /** Per-JVM counter for unique DD names; avoids S99ERROR 0x410 on concurrent/retried ops. */
    private static final AtomicInteger DD_SEQ = new AtomicInteger()

    /**
     * True if {@code MEMBER} exists in the PDS named by {@code path} (//DSN(MEMBER)).
     *
     * Uses {@link ZFile#exists(String)}, which for a member reference reads the PDS
     * directory (input open => SHR). No exclusive allocation is taken.
     */
    boolean exists(String path) {
        def (dsn, member) = parse(path)
        requireMember(member, 'exists', path)
        boolean result
        try {
            result = ZFile.exists("//'${dsn}(${member})'")
        } catch (ZFileException ignored) {
            // Older JZOS levels could throw instead of returning false (APAR PM64118).
            result = false
        }
        log.debug('exists({}): {}', path, result)
        return result
    }

    /**
     * Deletes {@code MEMBER} from the PDS named by {@code path} (//DSN(MEMBER)).
     *
     * A member delete is a STOW DELETE on the directory, performed under DISP=SHR via a
     * //DD: reference so the whole library is not locked.
     */
    void delete(String path) {
        log.debug('delete({})', path)
        def (dsn, member) = parse(path)
        requireMember(member, 'delete', path)
        withShrDd(dsn) { String dd ->
            ZFile.remove("//DD:${dd}(${member})")
        }
    }

    /**
     * Copies a member from one PDS to another (or to a different member of the same PDS).
     * Both {@code src} and {@code dst} must be member references.
     *
     * Records are copied one logical record at a time with rb/wb + type=record, preserving
     * RECFM and record boundaries with no EBCDIC<->ASCII translation. The source opens for
     * input (SHR); the target member is written through a SHR-allocated //DD: reference, so
     * the target library is not exclusively locked. Both libraries must already exist and be
     * attribute-compatible.
     */
    void copy(String src, String dst) {
        log.debug('copy({} -> {})', src, dst)
        def (srcDsn, srcMember) = parse(src)
        def (dstDsn, dstMember) = parse(dst)
        requireMember(srcMember, 'copy(source)', src)
        requireMember(dstMember, 'copy(target)', dst)

        ZFile srcFile = new ZFile("//'${srcDsn}(${srcMember})'", 'rb,type=record')
        try {
            withShrDd(dstDsn) { String dd ->
                writeRecords(srcFile, "//DD:${dd}(${dstMember})")
            }
        } finally {
            srcFile.close()
        }
    }

    /**
     * Lists the members of the PDS/PDSE named by {@code dsn}, equivalent to
     * {@code tsocmd "LISTDS '<dsn>' MEMBERS"}. A leading {@code //} and any accidental
     * member component are tolerated and ignored. Members come back in directory order
     * (ascending by name), which is the order LISTDS reports; aliases are included.
     *
     * The directory is read with an input open (SHR). A sequential dataset, or a name that
     * cannot be opened as a directory, yields a clear IllegalArgumentException.
     */
    List<String> list(String dsn) {
        String lib = parse(dsn)[0]
        def members = []
        PdsDirectory dir
        try {
            dir = new PdsDirectory("//'${lib}'")   // input open => SHR
        } catch (ZFileException e) {
            throw new IllegalArgumentException(
                    "Cannot list '${lib}': not a partitioned dataset, or not found", e)
        }
        try {
            for (MemberInfo mi : dir) {
                members << mi.getName()             // mi.isAlias() available if filtering is wanted
            }
        } finally {
            dir.close()
        }
        log.debug('list({}): {} member(s)', dsn, members.size())
        return members
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    /**
     * Allocates {@code dsn} with DISP=SHR to a unique DD, runs {@code body} with that DD
     * name, and unconditionally frees the DD afterwards. The unique DD name prevents the
     * S99ERROR 0x410 ("ddname already allocated") that a hardcoded name would cause on
     * concurrent or retried operations. A failure to free is logged, never propagated, so
     * it cannot mask the real outcome of {@code body}.
     */
    private void withShrDd(String dsn, Closure body) {
        String dd = uniqueDd()
        ZFile.bpxwdyn("alloc fi(${dd}) da('${dsn}') shr msg(2)")
        try {
            body.call(dd)
        } finally {
            try {
                ZFile.bpxwdyn("free fi(${dd})")
            } catch (Exception e) {
                log.warn('Failed to free DD {}: {}', dd, e.message)
            }
        }
    }

    /** Copies all logical records from an already-open source into {@code dstSpec}. */
    private void writeRecords(ZFile srcFile, String dstSpec) {
        ZFile dstFile = new ZFile(dstSpec, 'wb,type=record')
        try {
            byte[] buf = new byte[MAX_RECORD]
            int len
            // read() returns one record's length, or -1 at EOF; 0 is a valid empty record.
            while ((len = srcFile.read(buf)) >= 0) {
                dstFile.write(buf, 0, len)
            }
        } finally {
            dstFile.close()
        }
    }

    /** DD name "DDnnnnnn" (8 chars, leading alpha, hex suffix — all valid DD characters). */
    private String uniqueDd() {
        String.format('DD%06X', DD_SEQ.incrementAndGet() & 0xFFFFFF)
    }

    /**
     * Parses a reference into [dsn, member]; member is null when absent. A leading {@code //}
     * is optional.
     *   //MY.PDS(MBR)  -> ['MY.PDS', 'MBR']
     *   MY.PDS(MBR)    -> ['MY.PDS', 'MBR']
     *   //MY.PDS       -> ['MY.PDS', null]
     */
    private List<String> parse(String ref) {
        String s = ref.startsWith('//') ? ref.substring(2) : ref
        def m = (s =~ /^(.+?)\((.+?)\)$/)
        m.matches() ? [m.group(1), m.group(2)] : [s, null]
    }

    /** Enforces the member-only contract for exists / delete / copy. */
    private void requireMember(String member, String op, String ref) {
        if (!member) {
            throw new IllegalArgumentException(
                    "${op} requires a PDS member reference like //DSN(MEMBER), got: ${ref}")
        }
    }
}
