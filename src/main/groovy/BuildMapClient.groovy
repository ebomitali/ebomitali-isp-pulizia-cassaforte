trait BuildMapClient {
    abstract List<Map<String, String>> getGeneratedObjects(String sourcePath, String buildGroup)
}
