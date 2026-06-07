import spock.lang.Specification

/**
 * Spock specification for {@link BuildMapClientFactory}.
 *
 * <p>{@link BuildMapClientFactory#create} with type {@code 'json'} uses no IBM/DBB classes
 * and tests normally.  The {@code 'db2'} path is tested via
 * {@code GroovySpy(MetadataStoreFactory, global: true)} to intercept the static DB2 call
 * without a real DB2 connection.  The {@code 'dbb'} path requires a live DBB task context
 * and is not covered here.
 */
class BuildMapClientFactorySpec extends Specification {

    File bmFile
    PuliziaCassaforteConfig jsonCfg

    def setup() {
        bmFile  = new File(getClass().getResource('/fixtures/buildmap.json').toURI())
        jsonCfg = new PuliziaCassaforteConfig(buildMapPath: bmFile.canonicalPath)
    }

    // ─── json ─────────────────────────────────────────────────────────────────

    def "create('json') returns a JsonBuildMapClient"() {
        when:
        def client = BuildMapClientFactory.create('json', 'ATO', jsonCfg)

        then:
        client != null
        client instanceof BuildMapClient
        client instanceof JsonBuildMapClient
    }

    def "create('json') delegates getGeneratedObjects for known source path"() {
        given:
        def client = BuildMapClientFactory.create('json', 'ATO', jsonCfg)

        when:
        def results = client.getGeneratedObjects(
            'ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YO8AMADD.SJCLINP'
        )

        then:
        results.size() == 1
        results[0].library == 'LTM00.D9PX2A.PE000.@@@@.@@@@@@@@.@@.JINP'
        results[0].member  == 'YO8AMADD'
    }

    def "create('json') returns empty list for unknown source path"() {
        given:
        def client = BuildMapClientFactory.create('json', 'ATO', jsonCfg)

        expect:
        client.getGeneratedObjects('no/such/file.cbl') == []
    }

    // ─── db2 ─────────────────────────────────────────────────────────────────
    // Note: Db2BuildMapClient constructor is lazy (no DB2 connection until getGeneratedObjects).
    // Full behavior (build group resolution, lazy connect) is covered in Db2BuildMapClientSpec.

    def "create('db2') returns Db2BuildMapClient without connecting to DB2"() {
        given:
        // Constructor is fully lazy — no file reading at instantiation time
        def cfg = new PuliziaCassaforteConfig(
            buildMapClientType: 'db2',
            userId:        'user1',
            pwFilePath:    '/tmp/pw',
            db2ConfigPath: '/tmp/fake-db2.conf'
        )

        when:
        def client = BuildMapClientFactory.create('db2', 'MY_GROUP', cfg)

        then:
        client instanceof Db2BuildMapClient
        noExceptionThrown()
    }

    // ─── unknown type ─────────────────────────────────────────────────────────

    def "create with unknown type throws IllegalArgumentException"() {
        when:
        BuildMapClientFactory.create('bogus', 'GRP', jsonCfg)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('bogus')
    }
}
