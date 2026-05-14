// scripts/tasks/LocalBuildMapClient.groovy
import groovy.json.JsonSlurper

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
