package com.intesasanpaolo.bes.pc
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

    // Used by UssFileService/MacosFileService when fileOpsType is 'uss' or 'macos'
    String uxBasedir          = null

    // Used by JzosFileService if fileOpsType is set to 'zos'
    String hlq                = null

    Set<String> jobzExtensions = [] as Set

    // Default paths to rules and stage map CSVs. Override in tests or when running on USS.
    String rulesPath          = null
    File   rulesFile          = null
    String stagemapPath       = null
    File   stageMapFile       = null

    BuildMapClient buildMapClient = null
    FileService fileOps = null

    List rules                      = null
    Map stageMap                    = null
    PathVariableExtractor extractor = null
    EnvironmentChain envChain       = null
    DeleteCassaforteLogic deleteLogic = null
    SfilamentoLogic sfilamento    = null

    int doPuliziaCassaforte(File listToProcess, String environment, String buildGroup, String configFile = 'PuliziaCassaforte.properties') {
        Properties props = new Properties()
        new File(configFile).withInputStream { props.load(it) }
        return doPuliziaCassaforte(listToProcess, environment, buildGroup, props)
    }

    int doPuliziaCassaforte(File listToProcess, String environment, String buildGroup, Properties props) {
        log.info("Starting PuliziaCassaforte")

        init(props, buildGroup)

        if (!listToProcess)
            throw new IllegalArgumentException('listToProcess argument is required')
        if (!listToProcess.exists())
            throw new IllegalArgumentException("listToProcess file not found: '$listToProcess'")

        log.info("Processing list='{}' env='{}' buildGroup='{}'",
                 listToProcess, environment, buildGroup)
        int processed = 0, errors = 0
        listToProcess.eachLine { raw ->
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

            if (processSource(action, sourcePath, environment, buildGroup)) {
                processed++
            } else {
                errors++
            }
        }

        log.info("PuliziaCassaforte: processed={} errors={}", processed, errors)
        return errors
    }

    private void init(Properties props, String buildGroup, Object resolvedBuildGroup = null) {
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

        def effectiveCfg = new PuliziaCassaforteConfig(
            buildMapClientType: this.buildMapClientType,
            rulesPath:          this.rulesPath,
            stageMapPath:       this.stagemapPath,
            buildMapPath:       this.buildMapPath,
            userId:             this.userId,
            pwFilePath:         this.pwFilePath,
            db2ConfigPath:      this.db2ConfigPath
        )
        effectiveCfg.validate(resolvedBuildGroup != null && this.buildMapClientType == 'db2')

        rulesFile    = new File(rulesPath)
        stageMapFile = new File(stagemapPath)

        switch (this.buildMapClientType) {
            case 'json':
                this.buildMapClient = new JsonBuildMapClient(buildGroup, effectiveCfg)
                break
            case 'db2':
                // When a BuildGroup was already resolved (e.g. from a running task's
                // BuildContext), inject it directly — no deferred DB2 connection needed.
                this.buildMapClient = resolvedBuildGroup != null
                    ? new Db2BuildMapClient(resolvedBuildGroup)
                    : new Db2BuildMapClient(buildGroup, effectiveCfg)
                break
            case 'dbb':
                this.buildMapClient = new DbbBuildMapClient(buildGroup, effectiveCfg)
                break
            default: throw new IllegalArgumentException("Unknown buildMapClientType: '${this.buildMapClientType}'")
        }

        if (fileOpsType == 'zos') {
            this.fileOps = new JzosFileService()
        } else if (fileOpsType == 'uss') {
            if (uxBasedir == null)
                throw new IllegalArgumentException('uxBasedir must be defined when fileOpsType is set to uss')
            this.fileOps = new UssFileService(uxBasedir)
        } else if (fileOpsType == 'macos') {
            if (uxBasedir == null)
                throw new IllegalArgumentException('uxBasedir must be defined when fileOpsType is set to macos')
            this.fileOps = new MacosFileService(uxBasedir)
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
    }

    private boolean processSource(String action, String sourcePath, String environment, String buildGroup) {
        def fileType = resolveFileType(sourcePath)

        try {
            switch (action) {
                case 'C':
                    def vars = isJobzType(fileType)
                        ? extractor.extractJobz(environment, stageMap, hlq)
                        : extractor.extract(sourcePath, environment, stageMap, hlq)
                    deleteLogic.execute(sourcePath, fileType, vars, buildGroup)
                    return true
                case 'S':
                    sfilamento.execute(sourcePath, fileType, environment, buildGroup)
                    return true
                default:
                    log.warn("Unknown action '{}' for sourcePath '{}'", action, sourcePath)
                    return false
            }
        } catch (Exception e) {
            log.error("ERROR processing '{}': {}", sourcePath, e.message, e)
            return false
        }
    }

    int doPuliziaPostBuild(String sourceToProcess, String environment, String buildGroup, Properties props,
                           Object resolvedBuildGroup = null) {
        log.info("Starting PuliziaPostBuild for source='{}' env='{}'", sourceToProcess, environment)

        init(props, buildGroup, resolvedBuildGroup)

        if (!envChain.requiresPrevEnvClean(environment)) {
            log.info("Environment '{}' does not require previous environment cleanup", environment)
            return 0
        }

        if (!sourceToProcess)
            throw new IllegalArgumentException('sourceToProcess argument is required')

        def prevEnv = envChain.getPredecessor(environment)
        if (!prevEnv) {
            log.warn("No predecessor environment found for '{}'", environment)
            return 0
        }

        def fileType = resolveFileType(sourceToProcess)

        try {
            def vars = isJobzType(fileType)
                ? extractor.extractJobz(prevEnv, stageMap, hlq)
                : extractor.extract(sourceToProcess, prevEnv, stageMap, hlq)
            deleteLogic.execute(sourceToProcess, fileType, vars, buildGroup)
            log.info("PuliziaPostBuild: successfully cleaned '{}' from predecessor env '{}'", sourceToProcess, prevEnv)
            return 0
        } catch (Exception e) {
            log.error("ERROR in PuliziaPostBuild for '{}': {}", sourceToProcess, e.message, e)
            return 1
        }
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
