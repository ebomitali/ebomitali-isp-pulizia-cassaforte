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

    String fileOpsType        = null
    String buildMapClientType = null

    // Used by db2 build map client
    String userId             = null
    String pwFilePath         = null
    String db2ConfigPath      = null

    // Used by JSON build map client
    String buildMapPath       = null

    // Used by LocalFileOps if fileOpsType is set to 'local'
    String uxBasedir          = null

    // Used by ZosFileOps if fileOpsType is set to 'zos'
    String hlq                = null

    Set<String> jobzExtensions = [] as Set

    // Default paths to rules and stage map CSVs. Override in tests or when running on USS.
    String rulesPath          = null
    File   rulesFile          = null
    String stagemapPath       = null
    File   stageMapFile       = null

    BuildMapClient buildMapClient = null
    ZosFileOps fileOps = null

    List rules                      = null
    Map stageMap                    = null
    PathVariableExtractor extractor = null
    EnvironmentChain envChain       = null
    DeleteCassaforteLogic deleteLogic = null
    SfilamentoLogic sfilamento    = null

    int run(String listFileToProcess, String environment, String buildGroup, String configFile = 'PuliziaCassaforte.properties') {
        Properties props = new Properties()
        new File(configFile).withInputStream { props.load(it) }
        return run(listFileToProcess, environment, buildGroup, props)
    }

    int run(String listFileToProcess, String environment, String buildGroup, Properties props) {
        log.info("Starting PuliziaCassaforte")
        def cfg = PuliziaCassaforteConfig.from(props)
        if (cfg.fileOpsType)                   this.fileOpsType        = cfg.fileOpsType
        if (cfg.buildMapClientType)            this.buildMapClientType = cfg.buildMapClientType
        if (props.containsKey('rulesPath'))    this.rulesPath          = cfg.rulesPath
        if (props.containsKey('stageMapPath')) this.stagemapPath       = cfg.stageMapPath
        if (cfg.uxBasedir)          this.uxBasedir          = cfg.uxBasedir
        if (cfg.hlq)                this.hlq                = cfg.hlq
        if (cfg.userId)             this.userId             = cfg.userId
        if (cfg.pwFilePath)         this.pwFilePath         = cfg.pwFilePath
        if (cfg.db2ConfigPath)      this.db2ConfigPath      = cfg.db2ConfigPath
        if (cfg.buildMapPath)       this.buildMapPath       = cfg.buildMapPath
        if (cfg.jobzExtensions != null) this.jobzExtensions = cfg.jobzExtensions

        if (!listFileToProcess)
            throw new IllegalArgumentException('listFileToProcess argument is required')
        File lftp = new File(listFileToProcess)
        if (!lftp.exists())
            throw new IllegalArgumentException("listFileToProcess file not found: '$listFileToProcess'")

        new PuliziaCassaforteConfig(
            buildMapClientType: this.buildMapClientType,
            rulesPath:          this.rulesPath,
            stageMapPath:       this.stagemapPath,
            buildMapPath:       this.buildMapPath,
            userId:             this.userId,
            pwFilePath:         this.pwFilePath,
            db2ConfigPath:      this.db2ConfigPath
        ).validate()

        rulesFile    = new File(rulesPath)
        stageMapFile = new File(stagemapPath)

        if (buildMapClientType == 'db2') {
            this.buildMapClient = BuildMapClientFactory.fromConf(buildGroup, userId, pwFilePath, new File(db2ConfigPath))
        } else {
            this.buildMapClient = BuildMapClientFactory.fromJson(new File(buildMapPath))
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
