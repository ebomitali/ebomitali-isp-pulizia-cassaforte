package com.intesasanpaolo.bes.pc
import groovy.util.logging.Slf4j

/**
 * Models the ISP build pipeline's ordered environment chain and the relationships
 * between environments used by the cassaforte cleanup scripts.
 *
 * <h3>Environment chain</h3>
 * <pre>
 *   ATI → ATO → ST → PR
 *   EM  (standalone, no predecessor)
 * </pre>
 *
 * <h3>Stage codes (C1STAGE)</h3>
 * <pre>
 *   ATI → I1 | ATO → O1 | ST → S1 | PR → P1 | EM → E1
 * </pre>
 *
 * <h3>Key relationships</h3>
 * <ul>
 *   <li><b>Predecessor</b> ({@link #getPredecessor}): the environment immediately before in
 *       the chain; used by {@link PrevEnvCleanLogic} to delete stale cassaforte members
 *       after a successful build.</li>
 *   <li><b>Superiors</b> ({@link #getSuperiors}): environments further ahead in the chain
 *       that may hold a stable copy to restore from; used by {@link SfilamentoLogic}.</li>
 * </ul>
 *
 * <h3>Capability flags</h3>
 * <ul>
 *   <li>{@link #requiresPrevEnvClean} — true for ST and PR (they have a predecessor).</li>
 *   <li>{@link #supportsSfilamento}   — true only for ST (the SAD restore step).</li>
 * </ul>
 */
@Slf4j
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
        if (!stage) {
            log.error("Unknown environment: '{}'", env)
            throw new IllegalArgumentException("Unknown environment: '${env ?: 'null'}'")
        }
        stage
    }

    boolean requiresPrevEnvClean(String env) {
        env?.toUpperCase() in PREDECESSORS.keySet()
    }

    boolean supportsSfilamento(String env) {
        env?.toUpperCase() == 'ST'
    }
}
