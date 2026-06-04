import spock.lang.Specification

/**
 * Spock specification for {@link LocalBuildMapClient}.
 *
 * <p>Verifies that the JSON fixture ({@code src/test/resources/fixtures/buildmap.json})
 * is parsed correctly and that {@code getGeneratedObjects()} returns the right list
 * of output objects for known source paths, and an empty list for unknown ones.
 */
class LocalBuildMapClientSpec extends Specification {

    LocalBuildMapClient client

    def setup() {
        def jsonFile = new File(getClass().getResource('/fixtures/buildmap.json').toURI()).canonicalPath
        client = new LocalBuildMapClient(jsonFile)
    }

    def "getGeneratedObjects returns mapped objects for known JCL source"() {
        when:
        def results = client.getGeneratedObjects(
            'ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YO8AMADD.SJCLINP',
            'ATO'
        )

        then:
        results.size() == 1
        results[0].library == 'LTM00.D9PX2A.PE000.@@@@.@@@@@@@@.@@.JINP'
        results[0].member  == 'YO8AMADD'
    }

    def "getGeneratedObjects returns empty list for unknown source or build group"() {
        expect:
        client.getGeneratedObjects('ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YO8AMADD.SJCLINP', 'UNKNOWN') == []
        client.getGeneratedObjects('ATO/no/such/file.cbl', 'ATO') == []
    }
}
