// Mainframe-only. Must be compiled and run with groovyz on z/OS USS.
// After upload to USS: chtag -tc IBM-1047 MetadatastoreFactory.groovy
import com.ibm.dbb.metadata.MetadataStore
import com.ibm.dbb.metadata.MetadataStoreFactory

/**
 * Utility for creating a DBB {@link MetadataStore} connection from a
 * {@code db2Connection.conf} file and a password file.
 *
 * <p>Centralises the connection pattern shared by {@code GetBuildMapFields.groovy},
 * {@code QueryBuildMap.groovy}, and {@link ZosBuildMapClient#fromConf}.
 *
 * @see ZosBuildMapClient#fromConf
 */
class MetadatastoreFactory {

    /**
     * Opens a {@link MetadataStore} authenticating with {@code userId} and {@code pwFile}.
     *
     * @param userId     DB2 user ID.
     * @param pwFile     File containing the DB2 password.
     * @param configFile {@code db2Connection.conf} to read; defaults to
     *                   {@code ${DBB_CONF:-${DBB_HOME}/conf}/db2Connection.conf}.
     */
    static MetadataStore connect(String userId, File pwFile,
                                 File configFile = defaultConfigFile()) {
        Properties properties = new Properties()
        configFile.withInputStream { stream -> properties.load(stream) }
        return MetadataStoreFactory.createDb2MetadataStore(userId, pwFile, properties)
    }

    /** Convenience overload accepting the password file as a path string. */
    static MetadataStore connect(String userId, String pwFilePath,
                                 File configFile = defaultConfigFile()) {
        // verify that pwFile exists before proceeding
        File pwFile = new File(pwFilePath)
        if (!pwFile.exists()) {
            throw new IllegalStateException("DB2 password file not found at ${pwFile.canonicalPath}")
        }
        return connect(userId, pwFile, configFile)
    }

    private static File defaultConfigFile() {
        String confDir = System.getenv('DBB_CONF') ?: "${System.getenv('DBB_HOME')}/conf"
        // check that the file exists before returning it, to avoid confusion with a missing env var or typo in DBB_HOME
        File confFile = new File(confDir, 'db2Connection.conf')
        if (!confFile.exists()) {
            throw new IllegalStateException("DB2 config file not found at ${confFile.canonicalPath} — check DBB_CONF or DBB_HOME environment variables")
        }
        return confFile
    }
}
