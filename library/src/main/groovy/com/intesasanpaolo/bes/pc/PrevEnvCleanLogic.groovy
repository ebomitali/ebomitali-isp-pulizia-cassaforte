package com.intesasanpaolo.bes.pc
import groovy.util.logging.Slf4j

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
@Slf4j
class PrevEnvCleanLogic {
    DeleteCassaforteLogic deleteLogic
    PathVariableExtractor extractor  = new PathVariableExtractor()
    Map<String, String>   stageMap   = [:]
    String                hlq        = ''
    EnvironmentChain      envChain   = new EnvironmentChain()

    int execute(String sourcePath, String fileType, String environment, String buildGroup) {
        if (!envChain.requiresPrevEnvClean(environment)) {
            log.debug("Skipping prevEnvClean for environment '{}' (no predecessor)", environment)
            return 0
        }
        def prevEnv  = envChain.getPredecessor(environment)
        log.info("Cleaning predecessor '{}' cassaforte for sourcePath='{}'", prevEnv, sourcePath)
        def prevVars = extractor.extract(sourcePath, prevEnv, stageMap, hlq)
        deleteLogic.execute(sourcePath, fileType, prevVars, buildGroup)
    }
}
