// Mainframe-only. Must be compiled and run with groovyz on z/OS USS.
// After upload to USS: chtag -tc IBM-1047 Db2BuildMapClient.groovy
import com.ibm.dbb.build.BuildException
import com.ibm.dbb.metadata.BuildGroup
import com.ibm.dbb.metadata.BuildMap
import com.ibm.dbb.metadata.MetadataStore
import com.ibm.dbb.metadata.MetadataStoreFactory
import groovy.util.logging.Slf4j

/**
 * DB2-backed implementation of {@link BuildMapClient} that queries the live DBB metadata store
 * to resolve generated output objects for a given source file.
 *
 * <p>Two acquisition paths for the {@link BuildGroup}:
 * <ul>
 *   <li><b>Config-driven</b> ({@code PuliziaCassaforte.groovy} standalone): use the primary
 *       constructor with {@link PuliziaCassaforteConfig} — the DB2 connection is deferred until
 *       the first call to {@link #getGeneratedObjects}, so no connection is made when no rule
 *       uses {@code useBuildMap = true}.</li>
 *   <li><b>Direct injection</b> (tests): use {@code Db2BuildMapClient(BuildGroup)} to inject
 *       a mock or pre-fetched group without touching the DB2 stack.</li>
 * </ul>
 *
 * <p>Packaged separately into {@code pulizia-cassaforte-zos.jar} (requires IBM DBB jars in libs/).
 * Instantiated via reflection from {@link BuildMapClientFactory} — never imported directly.
 *
 * @see BuildMapClient
 * @see JsonBuildMapClient
 */
@Slf4j
class Db2BuildMapClient extends BuildMapClient {

    private BuildGroup buildGroup

    private final String userId
    private final String pwFilePath
    private final String db2ConfigPath

    /**
     * Primary constructor: deferred DB2 connection.
     * Stores credentials from {@code cfg}; no file reading or DB2 connection is made until
     * {@link #getGeneratedObjects} is first called.
     */
    Db2BuildMapClient(String buildGroupName, PuliziaCassaforteConfig cfg) {
        this.buildGroupName = buildGroupName
        this.buildGroup     = null
        this.userId         = cfg.userId
        this.pwFilePath     = cfg.pwFilePath
        this.db2ConfigPath  = cfg.db2ConfigPath
        log.debug("Db2BuildMapClient configured for group '{}' (deferred connection)", buildGroupName)
    }

    /**
     * Injection constructor for tests and task-context pass-through.
     * Accepts an already-fetched {@link BuildGroup} — no DB2 connection made.
     *
     * @param buildGroup  DBB {@link BuildGroup} for the current build; in a task context
     *                    obtain via {@code context.get('BUILD_GROUP') as BuildGroup}.
     */
    Db2BuildMapClient(BuildGroup buildGroup) {
        this.buildGroup     = buildGroup
        this.buildGroupName = buildGroup?.getName()
        this.userId         = null
        this.pwFilePath     = null
        this.db2ConfigPath  = null
        log.debug("Db2BuildMapClient initialized for group '{}'", buildGroupName)
    }

    /** Static factory invoked via reflection from {@link BuildMapClientFactory}. */
    static BuildMapClient create(String buildGroupName, PuliziaCassaforteConfig cfg) {
        new Db2BuildMapClient(buildGroupName, cfg)
    }

    private BuildGroup resolveBuildGroup() {
        if (buildGroup == null) {
            def db2Props = loadDb2Props(db2ConfigPath)
            log.info("Connecting to metadata store: group='{}' user='{}' url='{}'",
                     buildGroupName, userId, db2Props?.getProperty('url'))
            MetadataStore store = MetadataStoreFactory.createDb2MetadataStore(
                userId, new File(pwFilePath), db2Props)
            buildGroup = store.getBuildGroup(buildGroupName)
            if (!buildGroup) {
                log.error("Build group '{}' not found in metadata store", buildGroupName)
                throw new IllegalStateException("Build group '${buildGroupName}' not found in metadata store")
            }
        }
        return buildGroup
    }

    /**
     * Returns the output PDS members produced from {@code sourcePath} in the build map
     * stored under that path within the resolved {@link BuildGroup}.
     *
     * @param sourcePath  Repository-relative source file path (the build map label).
     * @return            List of {@code {library, member}} maps; empty if no record is found.
     */
    List<Map<String, String>> getGeneratedObjects(String sourcePath) {
        try {
            BuildMap bm = resolveBuildGroup().getBuildMap(sourcePath)
            if (!bm) {
                log.debug("getGeneratedObjects: no build map entry for '{}'", sourcePath)
                return []
            }

            def result = bm.getOutputs()
                .findAll { it.getDataset() && it.getMember() }
                .collect { [library: it.getDataset(), member: it.getMember()] }
            log.debug("getGeneratedObjects: '{}' -> {} object(s)", sourcePath, result.size())
            log.debug("Dump build map\n{}", bm.toString())
            return result

        } catch (BuildException e) {
            log.warn("getGeneratedObjects: BuildException for '{}': {}", sourcePath, e.message)
            return []
        }
    }

    private static Properties loadDb2Props(String db2ConfigPath) {
        def props = new Properties()
        new File(db2ConfigPath).withInputStream { stream -> props.load(stream) }
        return props
    }
}
