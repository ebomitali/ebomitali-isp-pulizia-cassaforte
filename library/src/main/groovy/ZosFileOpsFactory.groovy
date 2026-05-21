/**
 * Factory for creating {@link ZosFileOps} instances without IBM/DBB compile-time dependencies.
 *
 * <p>On USS (with {@code ZosFileOpsUSS} on the classpath) returns a live USS implementation;
 * falls back to {@link LocalFileOps} for local dev.
 *
 * @see ZosFileOps
 * @see LocalFileOps
 */
class ZosFileOpsFactory {

    /** Returns {@code ZosFileOpsUSS} on USS, {@link LocalFileOps} otherwise. */
    static ZosFileOps create() {
        try {
            return createOnZos()
        } catch (ClassNotFoundException ignored) {
            return mockZos()
        }
    }

    /**
     * Loads and returns a {@code ZosFileOpsUSS} instance via reflection.
     *
     * @throws ClassNotFoundException if {@code ZosFileOpsUSS} is not on the classpath.
     */
    static ZosFileOps createOnZos() {
        return Class.forName('ZosFileOpsUSS').newInstance() as ZosFileOps
    }

    /** Returns a {@link LocalFileOps} instance for local testing without z/OS dependencies. */
    static ZosFileOps mockZos() {
        return new LocalFileOps()
    }
}
