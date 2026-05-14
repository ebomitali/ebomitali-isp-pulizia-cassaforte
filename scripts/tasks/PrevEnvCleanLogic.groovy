// scripts/tasks/PrevEnvCleanLogic.groovy
class PrevEnvCleanLogic {
    DeleteCassaforteLogic deleteLogic
    EnvironmentChain      envChain = new EnvironmentChain()

    // Returns number of deletes performed (0 if current env has no predecessor).
    // buildGroup should reference the predecessor environment's build group
    // so the build map lookup resolves the right generated objects.
    // TODO: if fileType matches SJCL*, also copy the current env's freshly-built
    // JCL to the predecessor env's TOCOLB (prevents look-through to stale JCL).
    // Requires clarification from ISP on the exact copy direction.
    int execute(String sourcePath, String fileType, String environment, String system, String buildGroup) {
        if (!envChain.requiresPrevEnvClean(environment)) return 0
        def prevEnv   = envChain.getPredecessor(environment)
        def prevStage = envChain.getStage(prevEnv)
        deleteLogic.execute(sourcePath, fileType, prevStage, system, buildGroup)
    }
}
