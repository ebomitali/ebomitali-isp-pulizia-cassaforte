import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

/**
 * Local-filesystem implementation of {@link BuildMapClient} used during unit testing.
 *
 * <p>Reads a pre-captured DBB build-map JSON file (see {@code src/test/resources/fixtures/buildmap.json})
 * and returns the list of generated output objects for a given source path and build group.
 *
 * <p>The JSON file is an array of DBB build-map entries, each with {@code buildFile}, {@code group},
 * and {@code outputs} (list of {@code {member, dataset}} objects) — matching the structure of the
 * real DBB build map API response.  A single-object file is also accepted.
 *
 * <p>This class has no IBM/DBB dependencies and can run in any standard JVM.
 *
 * @see BuildMapClient
 * @see LocalFileOps
 */
@Slf4j
class LocalBuildMapClient implements BuildMapClient {
    private final List data

    LocalBuildMapClient(String jsonFilePath) {
        def parsed = new JsonSlurper().parse(new File(jsonFilePath))
        data = (parsed instanceof List) ? parsed : [parsed]
        log.debug("Loaded {} build map entries from: {}", data.size(), jsonFilePath)
    }

    List<Map<String, String>> getGeneratedObjects(String sourcePath, String buildGroup) {
        def entry = data.find { it.buildFile == sourcePath && it.group == buildGroup }
        if (!entry) {
            log.debug("getGeneratedObjects: no entry for '{}' in group '{}'", sourcePath, buildGroup)
            return []
        }
        def result = ((entry.outputs ?: []) as List)
            .findAll { it.dataset && it.member }
            .collect { [library: it.dataset as String, member: it.member as String] }
        log.debug("getGeneratedObjects: '{}' group '{}' -> {} object(s)", sourcePath, buildGroup, result.size())
        result
    }
}
