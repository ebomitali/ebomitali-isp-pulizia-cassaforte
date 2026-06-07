import groovy.util.logging.Slf4j

/**
 * Factory for creating {@link FileService} instances without IBM/DBB compile-time dependencies.
 *
 * <p>On USS (with {@code JzosFileService} on the classpath) returns a live IBM JZOS implementation;
 * falls back to {@link MacosFileService} for local dev.
 *
 * @see FileService
 * @see MacosFileService
 * @see UssFileService
 */
@Slf4j
class ZosFileOpsFactory {

    /** Returns {@code JzosFileService} on USS, {@link MacosFileService} otherwise. */
    static FileService create() {
        try {
            def impl = createOnZos()
            log.info("Using JzosFileService")
            return impl
        } catch (ClassNotFoundException ignored) {
            log.info("JzosFileService not found on classpath — using MacosFileService")
            return mockZos()
        }
    }

    /**
     * Loads and returns a {@code JzosFileService} instance via reflection.
     *
     * @throws ClassNotFoundException if {@code JzosFileService} is not on the classpath.
     */
    static FileService createOnZos() {
        log.debug("Loading JzosFileService via reflection")
        return Class.forName('JzosFileService').newInstance() as FileService
    }

    /** Returns a {@link MacosFileService} instance for local testing without z/OS dependencies. */
    static FileService mockZos() {
        log.debug("Creating MacosFileService for local testing")
        return new MacosFileService()
    }
}
