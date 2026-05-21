/**
 * Query build map using jar
 */
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
            buildMap = BuildMapClientFactory.fromConf(buildGroup, userId, pwFilePath, db2ConfigFile)
        } catch (IllegalStateException e) {
            System.err.println "WARN: ${e.message} — build map lookups will return empty"
            System.exit(1)
        }
        return execute(listFile, buildGroup, buildMap)
    }

    /**
     * Queries the build map using a pre-captured JSON build map file (local / fallback).
     *
     * @param listFile   Path to the list file.
     * @param buildGroup DBB build group name.
     * @param bmFile     JSON build map file in {@link LocalBuildMapClient} format.
     * @return Number of errors encountered (0 = success).
     */
    int run(String listFile, String buildGroup, File bmFile) {
        return execute(listFile, buildGroup, BuildMapClientFactory.fromJson(bmFile))
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
                System.err.println "Skipping malformed line: '$line'"
                errors++
                return
            }

            def sourcePath = line.substring(comma + 1).trim()
            println "Querying build map on '${buildGroup}' '${sourcePath}'..."

            try {
                buildMap.getGeneratedObjects(sourcePath, buildGroup).each { genObj ->
                    println "${sourcePath} -> ${genObj}"
                }
                processed++
            } catch (Exception e) {
                System.err.println "ERROR processing '$sourcePath': ${e.message}"
                errors++
            }
        }

        println "QueryBuildMapOnZosImpl: processed=${processed} errors=${errors}"
        return errors
    }
}
