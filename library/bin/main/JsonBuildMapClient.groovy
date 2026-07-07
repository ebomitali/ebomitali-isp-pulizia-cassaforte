import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

/**
 * JSON-file implementation of {@link BuildMapClient} used during unit testing and local dev.
 *
 * <p>Reads a pre-captured DBB build-map JSON file (see {@code src/test/resources/fixtures/buildmap.json})
 * and returns the list of generated output objects for a given source path and build group.
 *
 * <p>The JSON file is an array of DBB build-map entries, each with {@code buildFile}, {@code group},
 * and {@code outputs} (list of {@code {member, dataset}} objects) — matching the structure of the
 * real DBB build map API response.  A single-object file is also accepted.
 *
 * <p>This class has no IBM/DBB dependencies and can run in any standard JVM.
 * Build map path is read from {@link PuliziaCassaforteConfig#buildMapPath}.
 *
 * @see BuildMapClient
 * @see MacosFileService
 */
@Slf4j
class JsonBuildMapClient extends BuildMapClient {
    private final List data

    JsonBuildMapClient(String buildGroupName, PuliziaCassaforteConfig cfg) {
        this.buildGroupName = buildGroupName
        String canonicalPath = new File(cfg.buildMapPath).canonicalPath
        def parsed = new JsonSlurper().parse(new File(canonicalPath))
        data = (parsed instanceof List) ? parsed : [parsed]
        log.debug("Loaded {} build map entries from: {}", data.size(), canonicalPath)
    }

    List<Map<String, String>> getGeneratedObjects(String sourcePath) {
        def entry = data.find { it.buildFile == sourcePath && it.group == buildGroupName }
        if (!entry) {
            log.debug("getGeneratedObjects: no entry for '{}' in group '{}'", sourcePath, buildGroupName)
            return []
        }
        def result = ((entry.outputs ?: []) as List)
            .findAll { it.dataset && it.member }
            .collect { [library: it.dataset as String, member: it.member as String] }
        log.debug("getGeneratedObjects: '{}' group '{}' -> {} object(s)", sourcePath, buildGroupName, result.size())
        result
    }
}
