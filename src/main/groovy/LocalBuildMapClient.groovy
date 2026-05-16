import groovy.json.JsonSlurper

/**
 * Local-filesystem implementation of {@link BuildMapClient} used during unit testing.
 *
 * <p>Reads a pre-captured DBB build-map JSON file (see {@code src/test/resources/fixtures/buildmap.json})
 * and returns the list of generated output objects for a given source path and build group.
 *
 * <p>The JSON file is keyed by {@code "<buildGroup>:<sourcePath>"} strings.  Each value is a list of
 * objects with at least a {@code library} and a {@code member} field, mirroring the structure
 * returned by the real DBB build map API on USS.
 *
 * <p>This class has no IBM/DBB dependencies and can run in any standard JVM.
 *
 * @see BuildMapClient
 * @see LocalFileOps
 */
class LocalBuildMapClient implements BuildMapClient {
    private final Map data

    LocalBuildMapClient(String jsonFilePath) {
        data = new JsonSlurper().parse(new File(jsonFilePath)) as Map
    }

    List<Map<String, String>> getGeneratedObjects(String sourcePath, String buildGroup) {
        def key = "${buildGroup}:${sourcePath}"
        (data[key] ?: []) as List<Map<String, String>>
    }
}
