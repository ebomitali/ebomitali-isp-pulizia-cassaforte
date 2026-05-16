/**
 * Trait (interface) that abstracts z/OS file operations consumed by the cassaforte cleanup logic.
 *
 * <p>Two implementations are provided:
 * <ul>
 *   <li>{@link LocalFileOps}    — maps z/OS-style paths onto the local filesystem for unit testing.</li>
 *   <li>{@code ZosFileOpsUSS}   — delegates to IBM JZOS {@code ZFile} on z/OS USS (packaged separately).</li>
 * </ul>
 *
 * <p>Path convention used by all implementations:
 * <pre>
 *   //DATASET.NAME(MEMBER)  — PDS/PDSE member
 *   //DATASET.NAME          — sequential dataset or PDS root
 *   /path/to/file           — USS HFS/zFS path (passed through unchanged)
 * </pre>
 */
trait ZosFileOps {
    abstract boolean exists(String path)
    abstract void    delete(String path)
    abstract void    copy(String src, String dst)
    abstract List<String> list(String container)
}
