class DeleteCassaforteLogic {
    ZosFileOps          ops
    List<DeletionRule>  rules
    BuildMapClient      buildMap
    PatternMatcher      matcher  = new PatternMatcher()
    LibraryNameResolver resolver = new LibraryNameResolver()

    // stage and system are pre-resolved by the caller (via EnvironmentChain + path parser).
    // Returns number of delete operations performed.
    int execute(String sourcePath, String fileType, String stage, String system, String buildGroup) {
        def member   = memberName(sourcePath)
        def matching = rules.findAll { matcher.matches(it.typePattern, fileType) }
        int count    = 0

        matching.each { rule ->
            def lib = resolver.resolve(rule.libraryTemplate, stage, system)
            if (rule.useBuildMap) {
                buildMap.getGeneratedObjects(sourcePath, buildGroup).each { obj ->
                    if (obj.library == lib) {
                        def zp = "//${lib}(${obj.member})"
                        if (ops.exists(zp)) { ops.delete(zp); count++ }
                    }
                }
            } else {
                def zp = "//${lib}(${member})"
                if (ops.exists(zp)) { ops.delete(zp); count++ }
            }
        }
        count
    }

    static String memberName(String sourcePath) {
        def filename = sourcePath.tokenize('/').last()
        def name = filename.contains('.') ? filename.take(filename.lastIndexOf('.')) : filename
        name.toUpperCase()
    }
}
