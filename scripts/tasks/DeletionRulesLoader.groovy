// scripts/tasks/DeletionRulesLoader.groovy
class DeletionRulesLoader {
    List<DeletionRule> load(String filePath) {
        new File(filePath).readLines()
            .findAll { it.trim() && !it.startsWith('#') }
            .collect { line ->
                def parts = line.split(';', -1)
                if (parts.size() < 3)
                    throw new IllegalArgumentException("Invalid rule (need 3 semicolon-separated fields): '$line'")
                new DeletionRule(
                    typePattern:     parts[0],
                    libraryTemplate: parts[1].trim(),
                    useBuildMap:     parts[2].trim() == 'BUILD MAP'
                )
            }
    }
}
