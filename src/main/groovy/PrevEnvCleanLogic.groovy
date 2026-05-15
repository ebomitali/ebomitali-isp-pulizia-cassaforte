class PrevEnvCleanLogic {
    DeleteCassaforteLogic deleteLogic
    EnvironmentChain      envChain = new EnvironmentChain()

    // Returns number of deletes performed (0 if current env has no predecessor).
    int execute(String sourcePath, String fileType, String environment, String system, String buildGroup) {
        if (!envChain.requiresPrevEnvClean(environment)) return 0
        def prevEnv   = envChain.getPredecessor(environment)
        def prevStage = envChain.getStage(prevEnv)
        deleteLogic.execute(sourcePath, fileType, prevStage, system, buildGroup)
    }
}
