// scripts/tasks/EnvironmentChain.groovy
class EnvironmentChain {

    // Predecessor env for DELETE_PREV_ENV_AFTER_BUILD
    static final Map<String, String> PREDECESSORS = [
        ST: 'ATO', SAD: 'ATO',
        PR: 'ST',  PA:  'SAD',
    ]

    // Superior envs for SFILAMENTO (nearest first)
    static final Map<String, List<String>> SUPERIORS = [
        ST:  ['PR', 'PA'],
        SAD: ['PR', 'PA'],
        ATI: ['ATO'],
        ATO: ['ST', 'SAD'],
    ]

    // C1STAGE value by environment.
    // TODO: verify these values against actual ISP build configuration.
    // If stage also depends on layer (e.g. R1/R2), replace with Map<String,Map<String,String>>
    // and update getStage(env, layer) signature.
    static final Map<String, String> STAGE_BY_ENV = [
        ATI: 'I1',
        ATO: 'O1',
        ST:  'S1', SAD: 'S1',
        PR:  'P1', PA:  'P1',
        EM:  'E1',
    ]

    String getPredecessor(String env) {
        PREDECESSORS[env?.toUpperCase()]
    }

    List<String> getSuperiors(String env) {
        SUPERIORS[env?.toUpperCase()] ?: []
    }

    String getStage(String env) {
        def stage = STAGE_BY_ENV[env?.toUpperCase()]
        if (!stage) throw new IllegalArgumentException("Unknown environment: '${env ?: 'null'}'")
        stage
    }

    boolean requiresPrevEnvClean(String env) {
        env?.toUpperCase() in PREDECESSORS.keySet()
    }

    boolean supportsSfilamento(String env) {
        env?.toUpperCase() in ['ST', 'SAD']
    }
}
