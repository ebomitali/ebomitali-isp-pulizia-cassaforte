import groovy.util.logging.Slf4j

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
@Slf4j
class PuliziaCassaforteImpl {

    String fileOpsType   = 'zos' // set value to 'local' to use LocalFileOps for file operations, which is useful for testing
    String buildMapClientType = 'db2' // set value to 'json' to read build map from JSON file instead of DB2, which is useful for testing

    // Used by db2 build map client
    String userId        = null
    String pwFilePath    = null
    String db2ConfigPath = null

    // Used by JSON build map client
    String buildMapPath  = null

    // Used by LocalFileOps if fileOpsType is set to 'local'
    String uxBasedir     = null

    // Used by ZosFileOps if fileOpsType is set to 'zos'
    String hlq           = null

    Set<String> jobzExtensions = [] as Set

    // Default paths to rules and stage map CSVs. Override in tests or when running on USS.
    String rulesPath    = 'build-data/rules.csv'
    File   rulesFile    = null
    String stageMapPath = 'build-data/stage-map.csv'
    File   stageMapFile = null

    BuildMapClient buildMapClient = null
    ZosFileOps fileOps = null

    List rules                    = null
    Map stageMap                  = null
    PathVariableExtractor extractor = null
    EnvironmentChain envChain     = null
    DeleteCassaforteLogic deleteLogic = null
    SfilamentoLogic sfilamento    = null

    int run(String listFileToProcess, String environment, String buildGroup, String configFile = 'PuliziaCassaforte.properties') {
        Properties props = new Properties()
        new File(configFile).withInputStream { props.load(it) }
        return run(listFileToProcess, environment, buildGroup, props)
    }

    int run(String listFileToProcess, String environment, String buildGroup, Properties props) {
        log.info("Starting PuliziaCassaforte")
        if (props.getProperty('fileOpsType'))        this.fileOpsType        = props.getProperty('fileOpsType')
        if (props.getProperty('buildMapClientType')) this.buildMapClientType = props.getProperty('buildMapClientType')
        if (props.getProperty('rulesPath'))     this.rulesPath     = props.getProperty('rulesPath')
        if (props.getProperty('stageMapPath'))  this.stageMapPath  = props.getProperty('stageMapPath')
        if (props.getProperty('uxBasedir'))     this.uxBasedir     = props.getProperty('uxBasedir')
        if (props.getProperty('hlq'))           this.hlq           = props.getProperty('hlq')
        if (props.getProperty('userId'))        this.userId        = props.getProperty('userId')
        if (props.getProperty('pwFilePath'))    this.pwFilePath    = props.getProperty('pwFilePath')
        if (props.getProperty('db2ConfigPath')) this.db2ConfigPath = props.getProperty('db2ConfigPath')
        if (props.getProperty('buildMapPath'))  this.buildMapPath  = props.getProperty('buildMapPath')
        if (props.getProperty('jobzExtensions')) {
            this.jobzExtensions = props.getProperty('jobzExtensions')
                .split(',').collect { it.trim().toUpperCase() }.findAll { it }.toSet()
        }

        if (!listFileToProcess)
            throw new IllegalArgumentException('listFileToProcess argument is required')
        File lftp = new File(listFileToProcess)
        if (!lftp.exists()) {
            throw new IllegalArgumentException("listFileToProcess file not found: '$listFileToProcess'")
        }
        if (!rulesPath || !stageMapPath)
            throw new IllegalArgumentException('rulesPath and stageMapPath must be defined in config')
        rulesFile    = new File(rulesPath)
        stageMapFile = new File(stageMapPath)
        if (!rulesFile.exists())
            throw new IllegalArgumentException("rulesPath file not found: '$rulesPath'")
        if (!stageMapFile.exists())
            throw new IllegalArgumentException("stageMapPath file not found: '$stageMapPath'")

        if (buildMapClientType == 'db2') {
            int credCount = [userId, pwFilePath, db2ConfigPath].count { it != null }
            if (credCount == 0)
                throw new IllegalArgumentException('config must define userId (db2) or buildMapPath (json)')
            if (credCount < 3)
                throw new IllegalArgumentException('userId, pwFilePath and db2ConfigPath must all be defined or none')
            this.buildMapClient = BuildMapClientFactory.fromConf(buildGroup, userId, pwFilePath, new File(db2ConfigPath))
        } else if (buildMapClientType == 'json') {
            this.buildMapClient = BuildMapClientFactory.fromJson(new File(buildMapPath))
        } else {
            throw new IllegalArgumentException('config must define userId or buildMapPath')
        }

        if (fileOpsType == 'zos') {
            this.fileOps = ZosFileOpsFactory.createOnZos()
        } else if (fileOpsType == 'local') {
            if (uxBasedir == null)
                throw new IllegalArgumentException('uxBasedir must be defined when fileOpsType is set to local')
            this.fileOps = new LocalFileOps(uxBasedir)
        } else {
            throw new IllegalArgumentException("Unknown fileOpsType '$fileOpsType'")
        }

        rules       = new DeletionRulesLoader().load(rulesFile)
        stageMap    = new StageMapLoader().load(stageMapFile)
        extractor   = new PathVariableExtractor()
        envChain    = new EnvironmentChain()
        deleteLogic = new DeleteCassaforteLogic(ops: fileOps, rules: rules, buildMap: buildMapClient)
        sfilamento  = new SfilamentoLogic(
            ops:            fileOps,
            deleteLogic:    deleteLogic,
            rules:          rules,
            envChain:       envChain,
            extractor:      extractor,
            stageMap:       stageMap,
            hlq:            hlq,
            jobzExtensions: jobzExtensions
        )

        log.info("Processing list='{}' env='{}' buildGroup='{}'",
                 listFileToProcess, environment, buildGroup)
        int processed = 0, errors = 0
        lftp.eachLine { raw ->
            def line = raw.trim()
            if (!line || line.startsWith('#')) return
            def comma = line.indexOf(',')
            if (comma < 0) {
                log.warn("Skipping malformed line: '{}'", line)
                errors++
                return
            }
            def action     = line.substring(0, comma).trim().toUpperCase()
            def sourcePath = line.substring(comma + 1).trim()
            def fileType   = resolveFileType(sourcePath)

            try {
                switch (action) {
                    case 'C':
                        def vars = isJobzType(fileType)
                            ? extractor.extractJobz(environment, stageMap, hlq)
                            : extractor.extract(sourcePath, environment, stageMap, hlq)
                        deleteLogic.execute(sourcePath, fileType, vars, buildGroup)
                        processed++
                        break
                    case 'S':
                        sfilamento.execute(sourcePath, fileType, environment, buildGroup)
                        processed++
                        break
                    default:
                        log.warn("Unknown action '{}' in line: '{}'", action, line)
                        errors++
                }
            } catch (Exception e) {
                log.error("ERROR processing '{}': {}", sourcePath, e.message, e)
                errors++
            }
        }

        log.info("PuliziaCassaforte: processed={} errors={}", processed, errors)
        return errors
    }

    private boolean isJobzType(String fileType) {
        fileType?.trim() in jobzExtensions
    }

    private static String resolveFileType(String sourcePath) {
        def filename = sourcePath.tokenize('/').last()
        def ext = filename.contains('.') ? filename.substring(filename.lastIndexOf('.') + 1) : filename
        ext.toUpperCase().trim()
    }
}
