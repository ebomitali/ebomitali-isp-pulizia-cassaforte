import spock.lang.Specification

/**
 * Spock specification for {@link DeletionRulesLoader}.
 *
 * <p>Verifies that the CSV parser correctly converts each non-blank,
 * non-comment row into a {@link DeletionRule}, maps the three semicolon-separated
 * fields to the right properties, and raises on malformed input.
 *
 * <p>Uses {@code src/test/resources/fixtures/rules.csv} as the test fixture.
 */
class DeletionRulesLoaderSpec extends Specification {

    def "load parses all rules from fixture CSV skipping comment line"() {
        given:
        def rulesFile = new File(getClass().getResource('/fixtures/rules.csv').toURI())

        when:
        def rules = new DeletionRulesLoader().load(rulesFile)

        then:
        rules.size() == 8
        rules[0].typePattern     == '%CPYCOB*'
        rules[0].libraryTemplate == 'LTM00.D9P${C1STAGE}.PE000.LING.COB@@@@@.@@.COPY'
        rules[0].useBuildMap     == false
        rules[1].typePattern     == 'SZFSSWG'
        rules[1].useBuildMap     == true
        rules[2].typePattern     == 'SZFSSWG'
        rules[2].useBuildMap     == false
        rules[3].typePattern     == 'SJCL*'
        rules[4].typePattern     == '%JCLINP'
        rules[5].typePattern     == '%CB2%'
        rules[6].typePattern     == 'STWSNCS'
        rules[6].libraryTemplate == 'LTM00.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.JOBZ'
        rules[6].useBuildMap     == false
        rules[7].typePattern     == 'STWSNCS'
        rules[7].libraryTemplate == 'LTM00.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@JNCS'
        rules[7].useBuildMap     == false

    }

    def "load throws IllegalArgumentException on malformed line"() {
        given:
        def tmp = File.createTempFile('rules', '.csv')
        tmp.text = 'BADLINE\n'
        tmp.deleteOnExit()

        when:
        new DeletionRulesLoader().load(tmp)

        then:
        thrown(IllegalArgumentException)
    }
}
