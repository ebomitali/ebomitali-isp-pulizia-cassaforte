import com.ibm.dbb.metadata.BuildGroup
import com.ibm.dbb.metadata.MetadataStore
import com.ibm.dbb.metadata.MetadataStoreFactory
import spock.lang.Specification

/**
 * Spock specification for {@link BuildMapClientFactory}.
 *
 * <p>{@link BuildMapClientFactory#fromJson} uses no IBM/DBB classes and tests normally.
 * {@link BuildMapClientFactory#fromConf(String, String, File, Properties)} is tested via
 * {@code GroovySpy(MetadataStoreFactory, global: true)} to intercept the static DB2 call
 * without a real DB2 connection. {@code fromDbbCtx} requires a live DBB task context —
 * not covered here.
 */
class BuildMapClientFactorySpec extends Specification {

    File bmFile

    def setup() {
        bmFile = new File(getClass().getResource('/fixtures/buildmap.json').toURI())
    }

    def "fromJson returns a BuildMapClient from a valid JSON file"() {
        when:
        def client = BuildMapClientFactory.fromJson(bmFile)

        then:
        client != null
        client instanceof BuildMapClient
    }

    def "fromJson delegates getGeneratedObjects for known source path"() {
        given:
        def client = BuildMapClientFactory.fromJson(bmFile)

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

    def "fromJson returns empty list for unknown source path"() {
        given:
        def client = BuildMapClientFactory.fromJson(bmFile)

        expect:
        client.getGeneratedObjects('no/such/file.cbl', 'ATO') == []
    }

    // ─── fromConf ─────────────────────────────────────────────────────────────

    def "fromConf returns ZosBuildMapClient wrapping the found build group"() {
        given:
        def mockStore = Mock(MetadataStore)
        def mockGroup = Mock(BuildGroup)
        GroovySpy(MetadataStoreFactory, global: true)
        MetadataStoreFactory.createDb2MetadataStore(*_) >> mockStore
        mockStore.getBuildGroup('MY_GROUP') >> mockGroup

        when:
        def client = BuildMapClientFactory.fromConf('MY_GROUP', 'user1', new File('/tmp/pw'), new Properties())

        then:
        client instanceof ZosBuildMapClient
    }

    def "fromConf throws IllegalStateException when build group not found in store"() {
        given:
        def mockStore = Mock(MetadataStore)
        GroovySpy(MetadataStoreFactory, global: true)
        MetadataStoreFactory.createDb2MetadataStore(*_) >> mockStore
        mockStore.getBuildGroup('MISSING') >> null

        when:
        BuildMapClientFactory.fromConf('MISSING', 'user1', new File('/tmp/pw'), new Properties())

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains('MISSING')
    }
}
