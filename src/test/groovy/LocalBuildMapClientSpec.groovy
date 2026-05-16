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

    def "getGeneratedObjects returns mapped objects for known mapasm source"() {
        when:
        def results = client.getGeneratedObjects(
            '/dbb/DEE/IBM/yn_r_01_ato_r1/src/mapasm/batch/mapobj.asm',
            'yn_r_01_ato_r1'
        )

        then:
        results.size() == 1
        results[0].library == 'LTM00.D9PO1.PE000.LING.MAP@@@@@.@@.COPY'
        results[0].member  == 'MAPOBJ'
    }

    def "getGeneratedObjects returns empty list for unknown source path"() {
        expect:
        client.getGeneratedObjects('/dbb/DEE/IBM/yn_r_01_ato_r1/src/unknown/file.cbl', 'yn_r_01_ato_r1') == []
    }
}
