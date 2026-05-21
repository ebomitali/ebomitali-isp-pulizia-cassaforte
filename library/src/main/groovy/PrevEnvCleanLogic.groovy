/**
 * DBB post-build task logic: deletes the corresponding cassaforte member from the
 * <em>predecessor</em> environment after a successful compile step.
 *
 * <h3>Purpose</h3>
 * <p>When a source is promoted from environment N-1 to N, the N-1 cassaforte library
 * may still contain a stale copy of the old object.  If the N-1 library participates in
 * a concatenation with N, the stale entry shadows the new one.  This logic removes it
 * so that the concatenation always resolves to the most recently promoted version.
 *
 * <h3>Applicability</h3>
 * <p>Runs only in environments that have a predecessor
 * ({@link EnvironmentChain#requiresPrevEnvClean}): currently {@code ST} and {@code PR}.
 * Returns 0 immediately for all other environments (ATI, ATO, EM).
 *
 * @see DeleteCassaforteLogic
 * @see EnvironmentChain#requiresPrevEnvClean
 * @see EnvironmentChain#getPredecessor
 */
class PrevEnvCleanLogic {
    DeleteCassaforteLogic deleteLogic
    EnvironmentChain      envChain = new EnvironmentChain()

    // Returns number of deletes performed (0 if current env has no predecessor).
    int execute(String sourcePath, String fileType, String environment, String system, String buildGroup) {
        if (!envChain.requiresPrevEnvClean(environment)) return 0
        def prevEnv   = envChain.getPredecessor(environment)
        def prevStage = envChain.getStage(prevEnv)
        deleteLogic.execute(sourcePath, fileType, [C1STAGE: prevStage, C1SYSTEM: system, HLQ: ''], buildGroup)
    }
}
