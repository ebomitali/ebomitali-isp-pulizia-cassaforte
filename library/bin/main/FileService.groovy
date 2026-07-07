import groovy.util.logging.Slf4j

/**
 * Trait (interface) that abstracts z/OS PDS member operations for cassaforte cleanup logic.
 *
 * <p>Operates only on PDS/PDSE members. Dataset-level operations (allocation, deletion of entire datasets)
 * are out of scope. Unix/USS filesystem paths are not supported; all paths must use z/OS syntax.
 *
 * <p>Path format (required):
 * <pre>
 *   //DATASET.NAME(MEMBER)  — PDS/PDSE member (required; no plain dataset or Unix path references)
 * </pre>
 *
 * <p>Three implementations provided:
 * <ul>
 *   <li>{@link MacosFileService}  — simulates z/OS PDS on local filesystem for unit testing (macOS/Linux).</li>
 *   <li>{@link UssFileService}    — simulates z/OS PDS on USS filesystem (z/OS).</li>
 *   <li>{@link JzosFileService}   — delegates to IBM JZOS {@code ZFile} and {@code PdsDirectory} on z/OS USS.</li>
 * </ul>
 *
 * All operations require a member component and will throw {@link IllegalArgumentException} on
 * dataset-only or non-z/OS path references.
 */
@Slf4j
trait FileService {

    // all member only
    abstract boolean exists(String path)
    abstract void    delete(String path)
    abstract void    copy(String src, String dst)
    abstract List<String> list(String dsn)
}
