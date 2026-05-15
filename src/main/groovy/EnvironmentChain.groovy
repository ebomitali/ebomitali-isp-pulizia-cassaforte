class EnvironmentChain {

    static final Map<String, String> PREDECESSORS = [
        ST: 'ATO',
        PR: 'ST',
    ]

    static final Map<String, List<String>> SUPERIORS = [
        ST:  ['PR'],
        ATI: ['ATO'],
        ATO: ['ST'],
    ]

    // TODO: verify EM and SDD stage codes against actual ISP build configuration.
    static final Map<String, String> STAGE_BY_ENV = [
        ATI: 'I1',
        ATO: 'O1',
        ST:  'S1',
        PR:  'P1',
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
        env?.toUpperCase() == 'ST'
    }
}
