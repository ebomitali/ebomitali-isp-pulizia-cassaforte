// Mainframe-only. Must be compiled and run with groovyz on z/OS USS.
// After upload to USS: chtag -tc IBM-1047 ZosBuildMapClient.groovy
import com.ibm.dbb.build.BuildException
import com.ibm.dbb.metadata.BuildGroup
import com.ibm.dbb.metadata.BuildMap

/**
 * USS implementation of {@link BuildMapClient} that queries the live DBB metadata store
 * to resolve generated output objects for a given source file.
 *
 * <p>The {@link BuildGroup} is injected at construction time.  There are two acquisition paths:
 * <ul>
 *   <li><b>DBB task context</b> ({@code PuliziaPostBuild.groovy}): the {@code MetadataInit}
 *       built-in task populates {@code BUILD_GROUP} in the {@link com.ibm.dbb.task.BuildContext};
 *       pass {@code context.get('BUILD_GROUP') as BuildGroup} directly.</li>
 *   <li><b>Standalone USS script</b> ({@code PuliziaCassaforte.groovy}): use
 *       {@link #fromConf(String, String, String)} which reads {@code db2Connection.conf}
 *       and constructs the client in one call.</li>
 * </ul>
 *
 * <p>If no build map has been recorded for the given source path within the group, an empty
 * list is returned rather than throwing.
 *
 * <p>Packaged separately into {@code pulizia-cassaforte-zos.jar} (requires IBM DBB jars in libs/).
 * Must not be loaded by a local JVM — instantiated only inside the groovyz USS entry point.
 *
 * @see BuildMapClient
 * @see LocalBuildMapClient
 * @see ZosFileOpsUSS
 */
class ZosBuildMapClient implements BuildMapClient {

    private final BuildGroup buildGroup

    /**
     * @param buildGroup  DBB {@link BuildGroup} for the current build group.
     *                    In a task context, obtain via {@code context.get('BUILD_GROUP') as BuildGroup}.
     *                    In a standalone script, obtain via
     *                    {@code MetadataStoreFactory.createDb2MetadataStore(...).getBuildGroup(name)}.
     */
    ZosBuildMapClient(BuildGroup buildGroup) {
        this.buildGroup = buildGroup
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
    static ZosBuildMapClient fromConf(String confDir, String buildGroupName,
                                      String userId, String pwFilePath) {
        def store = MetadatastoreFactory.connect(userId, pwFilePath,
                                                 new File(confDir, 'db2Connection.conf'))
        BuildGroup group = store.getBuildGroup(buildGroupName)
        if (!group) {
            throw new IllegalStateException("Build group '${buildGroupName}' not found in metadata store")
        }
        return new ZosBuildMapClient(group)
    }

    /**
     * Returns the output PDS members produced from {@code sourcePath} in the build map
     * stored under that path within the injected {@link BuildGroup}.
     *
     * <p>Uses {@link BuildGroup#getBuildMap(String)} keyed by {@code sourcePath} — the same
     * label convention used by DBB when recording build outputs.  The {@code buildGroup}
     * parameter is ignored (already baked into the injected {@link BuildGroup}).
     *
     * @param sourcePath  Repository-relative source file path (the build map label).
     * @param buildGroup  Ignored; scope is fixed by the injected {@link BuildGroup}.
     * @return            List of {@code {library, member}} maps; empty if no record is found.
     */
    List<Map<String, String>> getGeneratedObjects(String sourcePath, String buildGroup) {
        try {
            BuildMap bm = this.buildGroup.getBuildMap(sourcePath)
            if (!bm) return []

            return bm.getOutputs()
                .findAll { it.getDataset() && it.getMember() }
                .collect { [library: it.getDataset(), member: it.getMember()] }

        } catch (BuildException e) {
            return []
        }
    }
}
