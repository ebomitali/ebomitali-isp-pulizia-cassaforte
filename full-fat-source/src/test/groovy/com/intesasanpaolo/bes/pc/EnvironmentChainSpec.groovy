package com.intesasanpaolo.bes.pc
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Spock specification for {@link EnvironmentChain}.
 *
 * <p>Exercises the full environment model: predecessor lookup, superior lists,
 * stage-code resolution, and capability flags ({@code requiresPrevEnvClean},
 * {@code supportsSfilamento}).  Uses {@code @Unroll} to generate one test per
 * environment entry, making failures easy to identify.
 */
class EnvironmentChainSpec extends Specification {

    def chain = new EnvironmentChain()

    @Unroll
    def "getPredecessor('#env') == #expected"() {
        expect:
        chain.getPredecessor(env) == expected

        where:
        env   | expected
        'ST'  | 'ATO'
        'PR'  | 'ST'
        'ATO' | null
    }

    @Unroll
    def "getSuperiors('#env') == #expected"() {
        expect:
        chain.getSuperiors(env) == expected

        where:
        env   | expected
        'ST'  | ['PR']
        'PR'  | []
    }

    @Unroll
    def "getStage('#env') == '#expected'"() {
        expect:
        chain.getStage(env) == expected

        where:
        env   | expected
        'ATI' | 'I1'
        'ATO' | 'O1'
        'ST'  | 'S1'
        'PR'  | 'P1'
    }

    def "getStage throws IllegalArgumentException on unknown environment"() {
        when:
        chain.getStage('UNKNOWN')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('UNKNOWN')
    }

    @Unroll
    def "requiresPrevEnvClean('#env') == #expected"() {
        expect:
        chain.requiresPrevEnvClean(env) == expected

        where:
        env   | expected
        'ST'  | true
        'PR'  | true
        'ATO' | false
    }

    @Unroll
    def "supportsSfilamento('#env') == #expected"() {
        expect:
        chain.supportsSfilamento(env) == expected

        where:
        env   | expected
        'ST'  | true
        'ATO' | false
        'PR'  | false
    }
}
