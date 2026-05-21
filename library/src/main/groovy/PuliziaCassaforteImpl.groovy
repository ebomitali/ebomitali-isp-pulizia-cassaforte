/**
 * Business logic for the {@code PuliziaCassaforte.groovy} entry point.
 *
 * <p>Compiled into {@code pulizia-cassaforte.jar} so it is available on both USS
 * (via {@code groovyz}) and local dev (via {@code groovy}).  Has no IBM/DBB compile-time
 * dependencies — IBM types are accessed via reflection where needed.
 *
 * @see DeleteCassaforteLogic
 * @see SfilamentoLogic
 */
class PuliziaCassaforteImpl {

    /** Path to the deletion rules CSV. Override in tests to point at the classpath fixture. */
    String rulesPath = new File('.', 'build-data/rules.csv').canonicalPath

    /**
     * Processes the action list file against the live DBB metadata store (USS).
     *
     * @param listFile      Path to the action list file.
     * @param environment   Environment code (e.g. {@code ATI}, {@code ATO}, {@code ST}, {@code PR}).
     * @param buildGroup    DBB build group name.
     * @param userId        DB2 user ID.
     * @param pwFilePath    Path to the DB2 password file.
     * @param db2ConfigFile {@code db2Connection.conf} to use for the metadata store connection.
     * @return Number of errors encountered (0 = success).
     */
    int run(String listFile, String environment, String buildGroup,
            String userId, String pwFilePath, File db2ConfigFile) {
        BuildMapClient buildMap
        try {
            buildMap = BuildMapClientFactory.fromConf(buildGroup, userId, pwFilePath, db2ConfigFile)
        } catch (IllegalStateException e) {
            System.err.println "WARN: ${e.message} — build map lookups will return empty"
            //return a stub that always answer with empty list: buildMap = [getGeneratedObjects: { sp, g -> [] }] as BuildMapClient
            System.exit(1)
        }
        return execute(listFile, environment, buildGroup, buildMap, ZosFileOpsFactory.createOnZos())
    }

    /**
     * Processes the action list file using a pre-captured JSON build map (local / fallback).
     *
     * @param listFile    Path to the action list file.
     * @param environment Environment code.
     * @param buildGroup  DBB build group name.
     * @param bmFile      JSON build map file in {@link LocalBuildMapClient} format.
     * @return Number of errors encountered (0 = success).
     */
    int run(String listFile, String environment, String buildGroup, File bmFile) {
        return run(listFile, environment, buildGroup, bmFile, ZosFileOpsFactory.mockZos())
    }

    /** Overload with explicit {@code ops} — used by tests for isolated {@link LocalFileOps}. */
    int run(String listFile, String environment, String buildGroup, File bmFile, ZosFileOps ops) {
        return execute(listFile, environment, buildGroup, BuildMapClientFactory.fromJson(bmFile), ops)
    }

    private int execute(String listFile, String environment, String buildGroup,
                        BuildMapClient buildMap, ZosFileOps ops) {
        def rules       = new DeletionRulesLoader().load(rulesPath)
        def envChain    = new EnvironmentChain()
        def deleteLogic = new DeleteCassaforteLogic(ops: ops, rules: rules, buildMap: buildMap)
        def sfilamento  = new SfilamentoLogic(
            ops: ops, deleteLogic: deleteLogic, rules: rules, envChain: envChain
        )

        def stage  = envChain.getStage(environment)
        def system = extractSystem(buildGroup)

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
            def action     = line.substring(0, comma).trim().toUpperCase()
            def sourcePath = line.substring(comma + 1).trim()
            def fileType   = resolveFileType(sourcePath)

            try {
                switch (action) {
                    case 'C':
                        deleteLogic.execute(sourcePath, fileType, [C1STAGE: stage, C1SYSTEM: system, HLQ: ''], buildGroup)
                        processed++
                        break
                    case 'S':
                        sfilamento.execute(sourcePath, fileType, environment, system, buildGroup)
                        processed++
                        break
                    default:
                        System.err.println "Unknown action '$action' in line: '$line'"
                        errors++
                }
            } catch (Exception e) {
                System.err.println "ERROR processing '$sourcePath': ${e.message}"
                errors++
            }
        }

        println "PuliziaCassaforte: processed=${processed} errors=${errors}"
        return errors
    }

    private static String extractSystem(String buildGroup) {
        // TODO: implement ISP-specific system code extraction from build group name.
        // Build group format example: yn_r_01_ato_r1 → first token as system prefix.
        buildGroup?.tokenize('_')?.first()?.toUpperCase() ?: ''
    }

    private static String resolveFileType(String sourcePath) {
        // TODO: implement ISP-specific mapping from file path/extension to 8-char type code.
        // Currently returns the file extension uppercased and padded to 8 chars.
        def filename = sourcePath.tokenize('/').last()
        def ext = filename.contains('.') ? filename.substring(filename.lastIndexOf('.') + 1) : filename
        ext.toUpperCase().padRight(8).take(8)
    }
}
