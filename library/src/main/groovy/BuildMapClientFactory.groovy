import com.ibm.dbb.metadata.BuildGroup
import com.ibm.dbb.metadata.MetadataStore
import com.ibm.dbb.metadata.MetadataStoreFactory
import groovy.util.logging.Slf4j

/**
 * Factory for creating {@link BuildMapClient} instances without IBM/DBB compile-time dependencies.
 *
 * <p>Two creation paths:
 * <ul>
 *   <li>{@link #fromJson} — local dev / fallback; wraps {@link LocalBuildMapClient} over a
 *       pre-captured JSON build map file.</li>
 *   <li>{@link #fromConf} — USS production; delegates to {@code ZosBuildMapClient.fromConf()}
 *       (in the USS-only jar) via reflection.</li>
 * </ul>
 *
 * @see BuildMapClient
 * @see LocalBuildMapClient
 */
@Slf4j
class BuildMapClientFactory {

    /**
     * Creates a {@link BuildMapClient} backed by a pre-captured JSON build map file.
     *
     * @param bmFile JSON file in the {@link LocalBuildMapClient} array format.
     */
    static BuildMapClient fromJson(File bmFile) {
        log.debug("Creating local BuildMapClient from JSON: {}", bmFile)
        return new LocalBuildMapClient(bmFile.canonicalPath)
    }

    /**
     * Creates a {@link BuildMapClient} connecting to a DB2 metadata store.
     *
     * <p>{@code ZosBuildMapClient} is resolved via reflection to avoid a compile-time
     * dependency on IBM jars — it must be on the classpath at runtime (USS only).
     *
     * @param buildGroupName DBB build group name.
     * @param userId         DB2 user ID.
     * @param pwFilePath     Path to the DB2 password file.
     * @param configFile     {@code db2Connection.conf} to use; defaults to
     *                       {@code ${DBB_CONF:-${DBB_HOME}/conf}/db2Connection.conf}.
     * @throws ClassNotFoundException  if {@code ZosBuildMapClient} is not on the classpath.
     * @throws IllegalStateException   if the build group is not found in the metadata store.
     */
    static BuildMapClient fromConf(String buildGroupName, String userId, String pwFilePath,
                                   File configFile = defaultConfigFile()) {
        log.info("Creating ZosBuildMapClient for group '{}' user '{}'", buildGroupName, userId)
        Properties db2ConnectionProps = new Properties()
        configFile.withInputStream { stream ->
            db2ConnectionProps.load(stream)
        }
        // Create file pwFile from pwFilePath and verify it exists
        File pwFile = new File(pwFilePath)
        if (!pwFile.exists()) {
            log.error("Password file '{}' not found", pwFilePath)
            throw new IllegalStateException("Password file '${pwFilePath}' not found")
        }
        MetadataStore store = MetadataStoreFactory.createDb2MetadataStore(userId, pwFile, db2ConnectionProps)
        BuildGroup group = store.getBuildGroup(buildGroupName)
        if (!group) {
            log.error("Build group '{}' not found in metadata store", buildGroupName)
            throw new IllegalStateException("Build group '${buildGroupName}' not found in metadata store")
        }
        return new ZosBuildMapClient(group)
    }

        /**
     * Creates a client by reading DB2 connection properties from {@code db2Connection.conf}
     * in {@code confDir} and connecting to the named build group.
     *
     * <p>Mirrors the connection pattern used by {@code GetBuildMapFields.groovy} and
     * {@code QueryBuildMap.groovy}: reads {@code url}, {@code user}, and {@code password}
     * keys from the conf file, then delegates to
     * {@link MetadataStoreFactory#createDb2MetadataStore(String, String, Properties)}.
     *
     * @param confDir        Directory containing {@code db2Connection.conf}
     *                       (typically {@code $DBB_CONF} or {@code $DBB_HOME/conf}).
     * @param buildGroupName Name of the DBB build group to look up.
     * @param userId         DB2 user ID.
     * @param pwFilePath     Path to the DB2 password file.
     * @throws IllegalStateException if the build group is not found in the metadata store.
     */
    static ZosBuildMapClient fromConf(String buildGroupName,
                                      String userId, File pwFile, Properties db2ConnectionProps) {
        log.info("Connecting to metadata store: group='{}' user='{}' url='{}'",
                 buildGroupName, userId, db2ConnectionProps?.getProperty('url'))
        MetadataStore store = MetadataStoreFactory.createDb2MetadataStore(userId, pwFile, db2ConnectionProps)
        BuildGroup group = store.getBuildGroup(buildGroupName)
        if (!group) {
            log.error("Build group '{}' not found in metadata store", buildGroupName)
            throw new IllegalStateException("Build group '${buildGroupName}' not found in metadata store")
        }
        return new ZosBuildMapClient(group)
    }

    private static File defaultConfigFile() {
        String confDir = System.getenv('DBB_CONF') ?: "${System.getenv('DBB_HOME')}/conf"
        return new File(confDir, 'db2Connection.conf')
    }

    /**
     * Creates a {@link BuildMapClient} reusing context BUILD_GROUP created by MetadataInit Task 
     *
     * <p>{@code ZosBuildMapClient} is resolved via reflection to avoid a compile-time
     * dependency on IBM jars — it must be on the classpath at runtime (USS only).
     *
     * @param buildGroupName DBB build group name.
     * @param userId         DB2 user ID.
     * @param pwFilePath     Path to the DB2 password file.
     * @param configFile     {@code db2Connection.conf} to use; defaults to
     *                       {@code ${DBB_CONF:-${DBB_HOME}/conf}/db2Connection.conf}.
     * @throws ClassNotFoundException  if {@code DbbCtxBuildMapClient} is not on the classpath.
     * @throws IllegalStateException   if the build group is not found in the metadata store.
     */
    // static BuildMapClient fromDbbCtx(String buildGroupName) {
    //     log.info("Creating DbbCtxBuildMapClient from DBB context")
    //     return Class.forName('DbbCtxBuildMapClient')
    //                 .fromDbbCtx(buildGroupName) as BuildMapClient
    // }

}
