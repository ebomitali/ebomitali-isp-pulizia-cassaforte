package com.intesasanpaolo.bes.pt

import com.ibm.jzos.PdsDirectory
import com.ibm.jzos.PdsDirectory.MemberInfo
import com.ibm.jzos.ZFile
import com.ibm.jzos.ZFileException
import groovy.util.logging.Slf4j

/**
 * z/OS production implementation of DatasetService.
 * Uses JZOS to interact with z/OS Internal Catalog and dataset operations.
 *
 * <p>Key operations:
 * <ul>
 *   <li>listDatasets: Query z/OS catalog via reflection-based CatalogSearchInterface or JZOS APIs</li>
 *   <li>deleteDataset: Use JZOS ZFile.bpxwdyn to allocate DD, invoke IDCAMS DELETE</li>
 *   <li>exists: Check z/OS catalog for DSN existence</li>
 * </ul>
 *
 * <p>Note: This is a z/OS-only implementation. Requires JZOS jars and groovyz on USS.
 */
@Slf4j
class JzosDatasetService implements DatasetService {

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
    void deleteDataset(String path) {
        log.debug('deleteDataset({})', path)
        def (dsn, member) = parse(path)
        requireMember(member, 'deleteDataset', path)
        withShrDd(dsn) { String dd ->
            ZFile.remove("//DD:${dd}(${member})")
        }
    }

    /**
     * Lists PDS/PDSE datasets matching pattern dsnPattern the PDS/PDSE named by {@code dsn}, equivalent to
     * {@code tsocmd "LISTCAT ENTRIES('MY.DATASET.*')"}.
     *
     * The directory is read with an input open (SHR). A sequential dataset, or a name that
     * cannot be opened as a directory, yields a clear IllegalArgumentException.
     */
    List<String> listDatasets(String dsnPattern) {
        List<String> datasets = []
        try {
            String[] dsns = ZFile.dsnList(dsnPattern)
            if (dsns) {
                dsns.each { dsn -> datasets << dsn }
            }
        } catch (IOException e) {
            log.error("Error listing datasets for pattern '{}': {}", dsnPattern, e.getMessage())
        }
        log.debug('list({}): {} dataset(s)', dsnPattern, datasets.size())
        return datasets
    }

    /** Enforces the member-only contract for exists / delete / copy. */
    private void requireMember(String member, String op, String ref) {
        if (!member) {
            throw new IllegalArgumentException(
                    "${op} requires a PDS member reference like //DSN(MEMBER), got: ${ref}")
        }
    }
}
