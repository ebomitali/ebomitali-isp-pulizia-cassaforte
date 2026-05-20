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
 *   <li><b>Standalone USS script</b> ({@code PuliziaCassaforte.groovy}): create the store from
 *       {@code $DBB_HOME/conf/db2Connection.conf}, then call
 *       {@code MetadataStoreFactory.createDb2MetadataStore(...).getBuildGroup(name)}.</li>
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
