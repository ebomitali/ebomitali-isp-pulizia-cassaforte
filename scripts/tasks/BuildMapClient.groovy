// scripts/tasks/BuildMapClient.groovy
trait BuildMapClient {
    // Returns list of generated objects for the given source path + build group.
    // Each map contains at minimum: 'library' (fully qualified DSN) and 'member' (member name).
    abstract List<Map<String, String>> getGeneratedObjects(String sourcePath, String buildGroup)
}
