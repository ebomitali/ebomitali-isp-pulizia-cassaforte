import groovy.util.logging.Slf4j

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
@Slf4j
class SfilamentoLogic {
    ZosFileOps            ops
    DeleteCassaforteLogic deleteLogic
    List<DeletionRule>    rules
    PathVariableExtractor extractor      = new PathVariableExtractor()
    Map<String, String>   stageMap       = [:]
    String                hlq            = ''
    PatternMatcher        matcher        = new PatternMatcher()
    LibraryNameResolver   resolver       = new LibraryNameResolver()
    EnvironmentChain      envChain       = new EnvironmentChain()
    Set<String>           jobzExtensions = [] as Set

    boolean execute(String sourcePath, String fileType, String environment, String buildGroup) {
        boolean isJobz = fileType?.trim() in jobzExtensions
        def currentVars = isJobz
            ? extractor.extractJobz(environment, stageMap, hlq)
            : extractor.extract(sourcePath, environment, stageMap, hlq)
        deleteLogic.execute(sourcePath, fileType, currentVars, buildGroup)

        boolean eligibleForRestore = isJobz || matcher.matches('SJCL*', fileType)
        if (!eligibleForRestore) {
            log.debug("Sfilamento skipped: fileType '{}' does not match SJCL* and is not jobz", fileType)
            return false
        }
        if (!envChain.supportsSfilamento(environment)) {
            log.debug("Sfilamento skipped: environment '{}' does not support it", environment)
            return false
        }

        def member   = DeleteCassaforteLogic.memberName(sourcePath)
        def matching = rules.findAll { matcher.matches(it.typePattern, fileType) }
        log.info("Sfilamento: searching restore for member '{}' fileType '{}' in environment '{}'",
                 member, fileType, environment)

        for (String superEnv : envChain.getSuperiors(environment)) {
            def superVars = isJobz
                ? extractor.extractJobz(superEnv, stageMap, hlq)
                : extractor.extract(sourcePath, superEnv, stageMap, hlq)
            for (def rule : matching) {
                def srcLib = resolver.resolve(rule.libraryTemplate, superVars)
                def src    = "//${srcLib}(${member})"
                if (ops.exists(src)) {
                    def localLib = resolver.resolve(rule.libraryTemplate, currentVars)
                    def tocolb   = resolver.toTocolbLibrary(localLib)
                    def dst      = "//${tocolb}(${member})"
                    log.info("Sfilamento: restoring {} -> {}", src, dst)
                    ops.copy(src, dst)
                    return true
                }
            }
        }
        log.info("Sfilamento: no restore source found for member '{}' in superiors of '{}'",
                 member, environment)
        return false
    }
}
