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
    String rulesPath    = new File('.', 'build-data/rules.csv').canonicalPath

    /** Path to the stage-map CSV. Override in tests or when running on USS. */
    String stageMapPath = new File('.', 'build-data/stage-map.csv').canonicalPath

    int run(String listFile, String environment, String buildGroup,
            String userId, String pwFilePath, File db2ConfigFile) {
        run(listFile, environment, buildGroup, userId, pwFilePath, db2ConfigFile, '')
    }

    int run(String listFile, String environment, String buildGroup,
            String userId, String pwFilePath, File db2ConfigFile, String hlq) {
        BuildMapClient buildMap
        try {
            buildMap = BuildMapClientFactory.fromConf(buildGroup, userId, pwFilePath, db2ConfigFile)
        } catch (IllegalStateException e) {
            System.err.println "WARN: ${e.message} — build map lookups will return empty"
            System.exit(1)
        }
        execute(listFile, environment, buildGroup, buildMap, ZosFileOpsFactory.createOnZos(), hlq)
    }

    int run(String listFile, String environment, String buildGroup, File bmFile) {
        run(listFile, environment, buildGroup, bmFile, ZosFileOpsFactory.mockZos(), '')
    }

    int run(String listFile, String environment, String buildGroup, File bmFile, String hlq) {
        run(listFile, environment, buildGroup, bmFile, ZosFileOpsFactory.mockZos(), hlq)
    }

    int run(String listFile, String environment, String buildGroup, File bmFile, ZosFileOps ops) {
        run(listFile, environment, buildGroup, bmFile, ops, '')
    }

    int run(String listFile, String environment, String buildGroup, File bmFile, ZosFileOps ops, String hlq) {
        execute(listFile, environment, buildGroup, BuildMapClientFactory.fromJson(bmFile), ops, hlq)
    }

    private int execute(String listFile, String environment, String buildGroup,
                        BuildMapClient buildMap, ZosFileOps ops, String hlq) {
        def rules      = new DeletionRulesLoader().load(rulesPath)
        def stageMap   = new StageMapLoader().load(stageMapPath)
        def extractor  = new PathVariableExtractor()
        def envChain   = new EnvironmentChain()
        def deleteLogic = new DeleteCassaforteLogic(ops: ops, rules: rules, buildMap: buildMap)
        def sfilamento  = new SfilamentoLogic(
            ops:         ops,
            deleteLogic: deleteLogic,
            rules:       rules,
            envChain:    envChain,
            extractor:   extractor,
            stageMap:    stageMap,
            hlq:         hlq
        )

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
                        def vars = extractor.extract(sourcePath, environment, stageMap, hlq)
                        deleteLogic.execute(sourcePath, fileType, vars, buildGroup)
                        processed++
                        break
                    case 'S':
                        sfilamento.execute(sourcePath, fileType, environment, buildGroup)
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

    private static String resolveFileType(String sourcePath) {
        def filename = sourcePath.tokenize('/').last()
        def ext = filename.contains('.') ? filename.substring(filename.lastIndexOf('.') + 1) : filename
        ext.toUpperCase().padRight(8).take(8)
    }
}
