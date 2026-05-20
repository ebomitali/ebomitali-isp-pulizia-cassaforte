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
class BuildMapClientFactory {

    /**
     * Creates a {@link BuildMapClient} backed by a pre-captured JSON build map file.
     *
     * @param bmFile JSON file in the {@link LocalBuildMapClient} array format.
     */
    static BuildMapClient fromJson(File bmFile) {
        return new LocalBuildMapClient(bmFile.canonicalPath)
    }

    /**
     * Creates a {@link BuildMapClient} connected to the live DBB metadata store.
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
        return Class.forName('ZosBuildMapClient')
                    .fromConf(configFile.parent, buildGroupName, userId, pwFilePath) as BuildMapClient
    }

    private static File defaultConfigFile() {
        String confDir = System.getenv('DBB_CONF') ?: "${System.getenv('DBB_HOME')}/conf"
        return new File(confDir, 'db2Connection.conf')
    }
}
