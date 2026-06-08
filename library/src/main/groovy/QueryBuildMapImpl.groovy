import groovy.util.logging.Slf4j

/**
 * Query build map using jar
 */
@Slf4j
class QueryBuildMapImpl {

    /**
     * Queries the build map for each source path in the list file against the live DBB metadata store.
     *
     * @param listFile      Path to the list file (one {@code <any>,<sourcePath>} per line).
     * @param buildGroup    DBB build group name.
     * @param userId        DB2 user ID.
     * @param pwFilePath    Path to the DB2 password file.
     * @param db2ConfigFile {@code db2Connection.conf} to use for the metadata store connection.
     * @return Number of errors encountered (0 = success).
     */
    int run(String listFile, String buildGroup,
            String userId, String pwFilePath, File db2ConfigFile) {
        BuildMapClient buildMap
        try {
            def cfg = new PuliziaCassaforteConfig(
                userId:        userId,
                pwFilePath:    pwFilePath,
                db2ConfigPath: db2ConfigFile.canonicalPath
            )
            buildMap = new Db2BuildMapClient(buildGroup, cfg)
        } catch (IllegalStateException e) {
            log.warn("build map unavailable: {} — build map lookups will return empty", e.message)
            System.exit(1)
        }
        return execute(listFile, buildGroup, buildMap)
    }

    /**
     * Queries the build map using a pre-captured JSON build map file (local / fallback).
     *
     * @param listFile   Path to the list file.
     * @param buildGroup DBB build group name.
     * @param bmFile     JSON build map file in {@link JsonBuildMapClient} format.
     * @return Number of errors encountered (0 = success).
     */
    int run(String listFile, String buildGroup, File bmFile) {
        def cfg = new PuliziaCassaforteConfig(buildMapPath: bmFile.canonicalPath)
        return execute(listFile, buildGroup, new JsonBuildMapClient(buildGroup, cfg))
    }

    /** Overload with explicit {@link BuildMapClient} — used by tests for injection. */
    int run(String listFile, String buildGroup, BuildMapClient buildMap) {
        return execute(listFile, buildGroup, buildMap)
    }

    private int execute(String listFile, String buildGroup, BuildMapClient buildMap) {
        int processed = 0, errors = 0
        new File(listFile).eachLine { raw ->
            def line = raw.trim()
            if (!line || line.startsWith('#')) return
            def comma = line.indexOf(',')
            if (comma < 0) {
                log.warn("Skipping malformed line: '{}'", line)
                errors++
                return
            }

            def sourcePath = line.substring(comma + 1).trim()
            log.info("Querying build map on '{}' for '{}'", buildGroup, sourcePath)

            try {
                buildMap.getGeneratedObjects(sourcePath).each { genObj ->
                    log.info("{} -> {}", sourcePath, genObj)
                }
                processed++
            } catch (Exception e) {
                log.error("ERROR processing '{}': {}", sourcePath, e.message, e)
                errors++
            }
        }

        log.info("QueryBuildMapImpl: processed={} errors={}", processed, errors)
        return errors
    }
}
