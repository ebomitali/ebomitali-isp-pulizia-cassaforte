import groovy.util.logging.Slf4j

/**
 * Factory for creating {@link ZosFileOps} instances without IBM/DBB compile-time dependencies.
 *
 * <p>On USS (with {@code ZosFileOpsUSS} on the classpath) returns a live USS implementation;
 * falls back to {@link LocalFileOps} for local dev.
 *
 * @see ZosFileOps
 * @see LocalFileOps
 */
@Slf4j
class ZosFileOpsFactory {

    /** Returns {@code ZosFileOpsUSS} on USS, {@link LocalFileOps} otherwise. */
    static ZosFileOps create() {
        try {
            def impl = createOnZos()
            log.info("Using ZosFileOpsUSS")
            return impl
        } catch (ClassNotFoundException ignored) {
            log.info("ZosFileOpsUSS not found on classpath — using LocalFileOps")
            return mockZos()
        }
    }

    /**
     * Loads and returns a {@code ZosFileOpsUSS} instance via reflection.
     *
     * @throws ClassNotFoundException if {@code ZosFileOpsUSS} is not on the classpath.
     */
    static ZosFileOps createOnZos() {
        log.debug("Loading ZosFileOpsUSS via reflection")
        return Class.forName('ZosFileOpsUSS').newInstance() as ZosFileOps
    }

    /** Returns a {@link LocalFileOps} instance for local testing without z/OS dependencies. */
    static ZosFileOps mockZos() {
        log.debug("Creating LocalFileOps for local testing")
        return new LocalFileOps()
    }
}
