package com.intesasanpaolo.bes.pc
// Mainframe-only. Must be compiled and run with groovyz in a DBB task or groovy step.
// After upload to USS: chtag -tc IBM-1047 DbbBuildMapClient.groovy
import com.ibm.dbb.build.BuildException
import com.ibm.dbb.task.BuildContext
import com.ibm.dbb.metadata.BuildGroup
import com.ibm.dbb.metadata.BuildMap
import groovy.util.logging.Slf4j

/**
 * DBB-task-context implementation of {@link BuildMapClient}.
 *
 * <p>Reuses the {@link BuildGroup} already resolved by the {@code MetadataInit} built-in task
 * and available in the running {@link BuildContext} as {@code BUILD_GROUP}.  No DB2 connection
 * is made by this client — the group is simply pulled from the context on the first call to
 * {@link #getGeneratedObjects}.
 *
 * <p>Usage from a DBB task script:
 * <pre>
 *   def client = new DbbBuildMapClient(buildGroupName, cfg)
 *   client.setContext(context)   // inject the running BuildContext
 * </pre>
 *
 * <p>Compiled against DBB metadata stubs (compileOnly) so it builds locally without IBM jars.
 * At runtime on z/OS USS the real DBB jars must be on the classpath.
 *
 * @see BuildMapClient
 * @see Db2BuildMapClient
 */
@Slf4j
class DbbBuildMapClient extends BuildMapClient {

    private BuildGroup  buildGroup
    private BuildContext context

    /**
     * Primary constructor: {@link BuildContext} must be injected via {@link #setContext} before
     * the first {@link #getGeneratedObjects} call.
     */
    DbbBuildMapClient(String buildGroupName, PuliziaCassaforteConfig cfg) {
        this.buildGroupName = buildGroupName
        this.buildGroup     = null
        this.context        = null
        log.debug("DbbBuildMapClient configured for group '{}' (context not yet injected)", buildGroupName)
    }

    /**
     * Injection constructor for task scripts that already hold the {@link BuildContext}.
     *
     * @param buildGroupName  DBB build group name (used for logging and validation).
     * @param context         Running DBB {@link BuildContext}; must contain {@code BUILD_GROUP}.
     */
    DbbBuildMapClient(String buildGroupName, BuildContext context) {
        this.buildGroupName = buildGroupName
        this.buildGroup     = null
        this.context        = context
        log.debug("DbbBuildMapClient configured for group '{}' with injected context", buildGroupName)
    }

    /** Allows late injection of the {@link BuildContext} after construction. */
    void setContext(BuildContext context) {
        this.context = context
    }

    private BuildGroup resolveBuildGroup() {
        if (buildGroup == null) {
            if (context == null)
                throw new IllegalStateException(
                    "DbbBuildMapClient: BuildContext not injected — call setContext(context) before use")
            buildGroup = context.get('BUILD_GROUP') as BuildGroup
            if (!buildGroup)
                throw new IllegalStateException(
                    "BUILD_GROUP not found in BuildContext for group '${buildGroupName}'")
            log.debug("DbbBuildMapClient: resolved BuildGroup '{}' from context", buildGroupName)
        }
        return buildGroup
    }

    /**
     * Returns the output PDS members produced from {@code sourcePath} in the build map
     * stored under that path within the {@link BuildGroup} from the running context.
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
            return result

        } catch (BuildException e) {
            log.warn("getGeneratedObjects: BuildException for '{}': {}", sourcePath, e.message)
            return []
        }
    }
}
