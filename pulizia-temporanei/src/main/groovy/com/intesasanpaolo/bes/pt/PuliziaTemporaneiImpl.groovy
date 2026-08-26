package com.intesasanpaolo.bes.pt
import groovy.util.logging.Slf4j

/**
 * Main orchestrator for PuliziaTemporanei dataset deletion.
 *
 * <p>Responsible for:
 * <ol>
 *   <li>Loading configuration from Properties file.</li>
 *   <li>Creating the appropriate DatasetService implementation.</li>
 *   <li>Creating the DeleteTemporaneiLogic instance.</li>
 *   <li>Delegating to core logic and returning result.</li>
 * </ol>
 *
 * <p>This class has zero IBM/DBB compile-time dependencies — IBM types are accessed
 * via reflection where needed (only in JzosDatasetService).
 *
 * @see DeleteTemporaneiLogic
 * @see DatasetService
 */
@Slf4j
class PuliziaTemporaneiImpl {

    String fileOpsType = null
    String uxBasedir = null

    DatasetService datasetOps = null
    DeleteTemporaneiLogic deleteLogic = null

    /**
     * Execute dataset deletion.
     * Accepts configuration Properties directly (optional).
     * If no cfgProps provided, defaults to empty Properties with fileOpsType='zos'.
     *
     * @param dsnPattern DSN pattern to delete (e.g., "MY.TEMP.*")
     * @param cfgProps Configuration Properties (optional; defaults to empty Properties)
     * @return count of deleted datasets
     */
    int doPuliziaTemporanei(String dsnPattern, Properties cfgProps = null) {
        Properties props = cfgProps ?: new Properties()
        log.info("Starting PuliziaTemporanei for DSN pattern: '{}'", dsnPattern)

        init(props)

        if (!dsnPattern?.trim()) {
            throw new IllegalArgumentException('dsnPattern argument is required')
        }

        return deleteLogic.execute(dsnPattern.trim())
    }

    /**
     * Execute dataset deletion, loading config from file.
     * Convenience method that reads properties from a file and delegates to main method.
     *
     * @param dsnPattern DSN pattern to delete
     * @param configFile Path to properties file (e.g., "PuliziaTemporanei.properties")
     * @return count of deleted datasets
     */
    int doPuliziaTemporaneiFromFile(String dsnPattern, String configFile) {
        Properties props = new Properties()
        new File(configFile).withInputStream { props.load(it) }
        return doPuliziaTemporanei(dsnPattern, props)
    }

    /**
     * Initialize components (called during execution).
     * Creates DatasetService and DeleteTemporaneiLogic instances based on config.
     *
     * @param props Configuration Properties
     * @throws IllegalArgumentException if configuration is invalid
     */
    private void init(Properties props) {
        def cfg = PuliziaTemporaneiConfig.from(props)
        this.fileOpsType = cfg.fileOpsType
        this.uxBasedir = cfg.uxBasedir
        cfg.validate()

        log.debug("Configuration: {}", cfg)

        // Create appropriate DatasetService implementation
        switch (fileOpsType) {
            case 'zos':
                log.debug("Using JzosDatasetService (z/OS production)")
                this.datasetOps = new JzosDatasetService()
                break
            case 'uss':
                log.debug("Using UssDatasetService with baseDir: {}", uxBasedir)
                this.datasetOps = new UssDatasetService(uxBasedir)
                break
            case 'macos':
                log.debug("Using MacosDatasetService with baseDir: {}", uxBasedir)
                this.datasetOps = new MacosDatasetService(uxBasedir)
                break
            default:
                throw new IllegalArgumentException("Unknown fileOpsType: '$fileOpsType'")
        }

        this.deleteLogic = new DeleteTemporaneiLogic(ops: datasetOps)
    }
}
