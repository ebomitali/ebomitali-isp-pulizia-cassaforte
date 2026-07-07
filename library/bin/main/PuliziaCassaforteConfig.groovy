/**
 * Configuration parsed from a {@link Properties} object for {@link PuliziaCassaforteImpl}.
 *
 * <p>Separates the "read config" concern from execution logic.  All fields are
 * {@code null} when the corresponding property key is absent — callers distinguish
 * "not set" from any default value.</p>
 *
 * <p>Property values may contain {@code ${VAR}} or {@code $VAR} placeholders resolved
 * against process environment variables before assignment.</p>
 *
 * <p>Configuration supports both testing using a local filesystem (non z/OS) 
 * and using a build map provided as a JSON file, by setting the appropriate {@code fileOpsType} 
 * and {@code buildMapClientType} values.</p>
 */
class PuliziaCassaforteConfig {

    // Sensible defaults for ease of use on USS, where properties files are less convenient.  
    // These can be overridden by properties files or test code.
    String fileOpsType        = 'zos' // 'zos', 'uss', or 'macos'
    String buildMapClientType = 'db2' // 'db2', 'dbb', 'json'
    String userId             = null
    String pwFilePath         = null
    String db2ConfigPath      = null
    String buildMapPath       = null
    String uxBasedir          = null
    String hlq                = null
    /** {@code null} when the {@code jobzExtensions} key is absent from the properties. */
    Set<String> jobzExtensions = ['STWSNCS','STWSJGO','STWSJGM'] as Set
    String rulesPath          = 'build-data/rules.csv'
    String stageMapPath       = 'build-data/stagemap.csv'
    String buildGroupName     = null

    /**
     * Build a config by expanding env-var placeholders in {@code props} and mapping
     * the known keys.  Unknown keys are silently ignored.
     */
    static PuliziaCassaforteConfig from(Properties props) {
        Properties resolved = new Properties()
        props.each { k, v ->
            def expanded = expandEnvVars(v?.toString())
            if (expanded != null) resolved.setProperty(k.toString(), expanded)
        }

        def cfg = new PuliziaCassaforteConfig()
        cfg.fileOpsType        = resolved.getProperty('fileOpsType')        ?: cfg.fileOpsType
        cfg.buildMapClientType = resolved.getProperty('buildMapClientType') ?: cfg.buildMapClientType
        cfg.rulesPath          = resolved.getProperty('rulesPath')          ?: cfg.rulesPath
        cfg.stageMapPath       = resolved.getProperty('stageMapPath')       ?: cfg.stageMapPath
        cfg.uxBasedir          = resolved.getProperty('uxBasedir')          ?: cfg.uxBasedir
        cfg.hlq                = resolved.getProperty('hlq')                ?: cfg.hlq
        cfg.userId             = resolved.getProperty('userId')             ?: cfg.userId
        cfg.pwFilePath         = resolved.getProperty('pwFilePath')         ?: cfg.pwFilePath
        cfg.db2ConfigPath      = resolved.getProperty('db2ConfigPath')      ?: cfg.db2ConfigPath
        cfg.buildMapPath       = resolved.getProperty('buildMapPath')       ?: cfg.buildMapPath
        if (resolved.getProperty('jobzExtensions')) {
            cfg.jobzExtensions = resolved.getProperty('jobzExtensions')
                .split(',').collect { it.trim().toUpperCase() }.findAll { it }.toSet()
        }
        return cfg
    }

    /**
     * Validates the configuration according to the following rules:
     * <ul>
     *   <li>{@code rulesPath} and {@code stagemapPath} must be set and point to existing files.</li>
     *   <li>When {@code buildMapClientType} is {@code db2}: {@code userId}, {@code pwFilePath}
     *       and {@code db2ConfigPath} must all be non-null, and their paths must exist.</li>
     *   <li>When {@code buildMapClientType} is {@code json}: {@code buildMapPath} must be set
     *       and point to an existing file.</li>
     * </ul>
     * @throws IllegalArgumentException on the first rule violation found.
     */
    void validate() {
        if (!rulesPath)
            throw new IllegalArgumentException('rulesPath must be defined in config')
        if (!new File(rulesPath).exists())
            throw new IllegalArgumentException("rulesPath not found: '$rulesPath'")
        if (!stageMapPath)
            throw new IllegalArgumentException('stageMapPath must be defined in config')
        if (!new File(stageMapPath).exists())
            throw new IllegalArgumentException("stageMapPath not found: '$stageMapPath'")

        def bmType = buildMapClientType ?: 'db2'
        if (bmType == 'db2') {
            int credCount = [userId, pwFilePath, db2ConfigPath].count { it }
            if (credCount == 0)
                throw new IllegalArgumentException('db2 buildMapClientType requires userId, pwFilePath and db2ConfigPath')
            if (credCount < 3)
                throw new IllegalArgumentException('userId, pwFilePath and db2ConfigPath must all be defined or none')
            if (!new File(pwFilePath).exists())
                throw new IllegalArgumentException("pwFilePath not found: '$pwFilePath'")
            if (!new File(db2ConfigPath).exists())
                throw new IllegalArgumentException("db2ConfigPath not found: '$db2ConfigPath'")
        } else if (bmType == 'json') {
            if (!buildMapPath)
                throw new IllegalArgumentException('json buildMapClientType requires buildMapPath')
            if (!new File(buildMapPath).exists())
                throw new IllegalArgumentException("buildMapPath not found: '$buildMapPath'")
        } else {
            throw new IllegalArgumentException("Unknown buildMapClientType: '$bmType'")
        }
    }

    /**
     * Expands {@code ${VAR}} and {@code $VAR} references using process environment variables.
     * Unresolved references (env var not set) are kept verbatim.
     */
    static String expandEnvVars(String value) {
        if (!value) return value
        value.replaceAll(/\$\{([A-Za-z_][A-Za-z0-9_]*)\}|\$([A-Za-z_][A-Za-z0-9_]*)/) { List<String> m ->
            String varName = m[1] ?: m[2]
            System.getenv(varName) ?: m[0]
        }
    }
}
