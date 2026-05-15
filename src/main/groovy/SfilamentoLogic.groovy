class SfilamentoLogic {
    ZosFileOps          ops
    DeleteCassaforteLogic deleteLogic
    List<DeletionRule>  rules
    PatternMatcher      matcher  = new PatternMatcher()
    LibraryNameResolver resolver = new LibraryNameResolver()
    EnvironmentChain    envChain = new EnvironmentChain()

    // Returns true if a JCL restore to TOCOLB was performed.
    boolean execute(String sourcePath, String fileType, String environment, String system, String buildGroup) {
        def stage = envChain.getStage(environment)
        deleteLogic.execute(sourcePath, fileType, stage, system, buildGroup)

        if (!matcher.matches('SJCL*', fileType)) return false
        if (!envChain.supportsSfilamento(environment)) return false

        def member   = DeleteCassaforteLogic.memberName(sourcePath)
        def matching = rules.findAll { matcher.matches(it.typePattern, fileType) }

        for (String superEnv : envChain.getSuperiors(environment)) {
            def superStage = envChain.getStage(superEnv)
            for (def rule : matching) {
                def srcLib = resolver.resolve(rule.libraryTemplate, superStage, system)
                def src    = "//${srcLib}(${member})"
                if (ops.exists(src)) {
                    def localLib = resolver.resolve(rule.libraryTemplate, stage, system)
                    def tocolb   = resolver.toTocolbLibrary(localLib)
                    ops.copy(src, "//${tocolb}(${member})")
                    return true
                }
            }
        }
        return false
    }
}
