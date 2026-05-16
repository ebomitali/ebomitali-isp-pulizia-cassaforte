/**
 * Implements the sfilamento (S-action) scenario: delete the current environment's cassaforte
 * member and, if applicable, restore it from the nearest superior environment.
 *
 * <h3>When does sfilamento apply?</h3>
 * <ul>
 *   <li>Only in environment {@code ST} ({@link EnvironmentChain#supportsSfilamento}).</li>
 *   <li>Only for file types matching {@code SJCL*} (JCL source members).</li>
 * </ul>
 *
 * <h3>Restore logic</h3>
 * <p>After deleting the ST cassaforte member, the logic walks {@link EnvironmentChain#getSuperiors}
 * in order (e.g. {@code PR}) and, for each rule matching the file type, looks for the member in
 * that environment's cassaforte library.  The first copy found is restored into the ST
 * TOCOLB library (derived via {@link LibraryNameResolver#toTocolbLibrary}) and the loop stops.
 *
 * <p>Returns {@code true} if a restore was performed, {@code false} if no copy was found
 * or the file type is not sfilamento-eligible.
 *
 * @see DeleteCassaforteLogic
 * @see EnvironmentChain
 * @see LibraryNameResolver#toTocolbLibrary
 */
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
