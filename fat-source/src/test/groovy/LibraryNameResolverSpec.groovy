import spock.lang.Specification

/**
 * Spock specification for {@link LibraryNameResolver}.
 *
 * <p>Covers:
 * <ul>
 *   <li>{@code resolve()} — substitution of {@code ${C1STAGE}} and {@code ${C1SYSTEM}}
 *       placeholders in library templates.</li>
 *   <li>{@code toTocolbLibrary()} — derivation of the TOCOLB restore target from a
 *       resolved cassaforte library name (qualifier 4/5 transformation).</li>
 * </ul>
 */
class LibraryNameResolverSpec extends Specification {

    def resolver = new LibraryNameResolver()

    def "resolve substitutes C1STAGE placeholder"() {
        expect:
        resolver.resolve(
            'LTM00.D9P${C1STAGE}.PE000.LING.COB@@@@@.@@.COPY',
            [C1STAGE: 'O1', C1SYSTEM: '', HLQ: '']
        ) == 'LTM00.D9PO1.PE000.LING.COB@@@@@.@@.COPY'
    }

    def "resolve substitutes both C1STAGE and C1SYSTEM"() {
        expect:
        resolver.resolve(
            'LTM00.D9P${C1STAGE}.PE000.SYST.${C1SYSTEM}@@@@@@@.BT.LOAD',
            [C1STAGE: 'S1', C1SYSTEM: 'MYSYS', HLQ: '']
        ) == 'LTM00.D9PS1.PE000.SYST.MYSYS@@@@@@@.BT.LOAD'
    }

    def "toTocolbLibrary derives TOCOLB library from cassaforte library"() {
        expect:
        resolver.toTocolbLibrary('LTM00.D9PS1.PE000.@@@@.@@@@@@@@.@@.SJCL') ==
            'LTM00.D9PS1.PE000.TO@@.COLB@@@@.@@.SJCL'
    }

    def "toTocolbLibrary passes through library without @@@@ qualifiers unchanged"() {
        expect:
        resolver.toTocolbLibrary('LTM00.D9PS1.PE000.LING.COB@@@@@.@@.COPY') ==
            'LTM00.D9PS1.PE000.LING.COB@@@@@.@@.COPY'
    }

    def "resolve(Map) substitutes C1STAGE and C1SYSTEM"() {
        expect:
        resolver.resolve(
            'LTM00.D9P${C1STAGE}.PE000.SYST.${C1SYSTEM}@@@@@@@.BT.LOAD',
            [C1STAGE: 'X2A', C1SYSTEM: 'y', HLQ: '']
        ) == 'LTM00.D9PX2A.PE000.SYST.y@@@@@@@.BT.LOAD'
    }

    def "resolve(Map) substitutes HLQ"() {
        expect:
        resolver.resolve(
            '${HLQ}.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.ZARA',
            [C1STAGE: 'X2A', C1SYSTEM: 'y', HLQ: 'U0G9700']
        ) == 'U0G9700.D9PX2A.PE000.@@@@.@@@@@@@@.@@.ZARA'
    }

    def "resolve(Map) HLQ empty string substitutes to empty string without error"() {
        expect:
        resolver.resolve(
            '${HLQ}.D9P${C1STAGE}',
            [C1STAGE: 'X2A', C1SYSTEM: '', HLQ: '']
        ) == '.D9PX2A'
    }

    def "resolve(Map) throws IllegalStateException on unresolved macro"() {
        when:
        resolver.resolve('LTM00.D9P${UNKNOWN}.PE000', [C1STAGE: 'X2A'])

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('${UNKNOWN}')
    }
}
