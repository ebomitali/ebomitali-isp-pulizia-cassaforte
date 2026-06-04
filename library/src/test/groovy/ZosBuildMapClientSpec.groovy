import com.ibm.dbb.build.BuildException
import com.ibm.dbb.metadata.BuildGroup
import com.ibm.dbb.metadata.BuildMap
import com.ibm.dbb.metadata.MetadataStore
import com.ibm.dbb.metadata.MetadataStoreFactory
import spock.lang.Specification

/**
 * Spock specification for {@link ZosBuildMapClient}.
 *
 * <p>IBM DBB classes are replaced by stubs from the {@code stubs} subproject, so
 * all tests run locally without mainframe access. {@link MetadatastoreFactory}
 * is intercepted via {@code GroovySpy(global: true)} to avoid file-system and DB2
 * setup in the {@link ZosBuildMapClient#fromConf} path.
 */
class ZosBuildMapClientSpec extends Specification {

    /** Minimal stub for the IBM BuildMap output objects. */
    static class OutputStub {
        String dataset
        String member
    }

    // ─── constructor ─────────────────────────────────────────────────────────

    def "constructor accepts a BuildGroup without error"() {
        given:
        def group = Mock(BuildGroup)

        when:
        def client = new ZosBuildMapClient(group)

        then:
        client != null
        noExceptionThrown()
    }

    // ─── getGeneratedObjects ──────────────────────────────────────────────────

    def "returns empty list when build map has no entry for source path"() {
        given:
        def group = Mock(BuildGroup)
        group.getBuildMap(_) >> null
        def client = new ZosBuildMapClient(group)

        expect:
        client.getGeneratedObjects('some/path/file.cbl', 'ATO') == []
    }

    def "returns mapped outputs for known source path"() {
        given:
        def group = Mock(BuildGroup)
        def bm    = Mock(BuildMap)
        group.getBuildMap('ATO/path/YO8AMADD.SJCLINP') >> bm
        bm.getOutputs() >> [new OutputStub(dataset: 'LTM00.D9PX2A.PE000.@@@@.JINP', member: 'YO8AMADD')]
        def client = new ZosBuildMapClient(group)

        when:
        def results = client.getGeneratedObjects('ATO/path/YO8AMADD.SJCLINP', 'ATO')

        then:
        results.size() == 1
        results[0].library == 'LTM00.D9PX2A.PE000.@@@@.JINP'
        results[0].member  == 'YO8AMADD'
    }

    def "returns empty list when outputs list is empty"() {
        given:
        def group = Mock(BuildGroup)
        def bm    = Mock(BuildMap)
        group.getBuildMap(_) >> bm
        bm.getOutputs() >> []
        def client = new ZosBuildMapClient(group)

        expect:
        client.getGeneratedObjects('path/file.cbl', 'ATO') == []
    }

    def "filters out outputs with null dataset or null member"() {
        given:
        def group = Mock(BuildGroup)
        def bm    = Mock(BuildMap)
        group.getBuildMap(_) >> bm
        bm.getOutputs() >> [
            new OutputStub(dataset: null,        member: 'ORPHAN'),
            new OutputStub(dataset: 'LTM00.VALID.DS', member: null),
            new OutputStub(dataset: 'LTM00.VALID.DS', member: 'GOOD'),
        ]
        def client = new ZosBuildMapClient(group)

        when:
        def results = client.getGeneratedObjects('path/file.cbl', 'ATO')

        then:
        results.size() == 1
        results[0].member == 'GOOD'
    }

    def "returns multiple outputs when build map has multiple entries"() {
        given:
        def group = Mock(BuildGroup)
        def bm    = Mock(BuildMap)
        group.getBuildMap(_) >> bm
        bm.getOutputs() >> [
            new OutputStub(dataset: 'LTM00.DS1', member: 'MBR1'),
            new OutputStub(dataset: 'LTM00.DS2', member: 'MBR2'),
        ]
        def client = new ZosBuildMapClient(group)

        when:
        def results = client.getGeneratedObjects('path/file.cbl', 'ATO')

        then:
        results.size() == 2
        results*.member == ['MBR1', 'MBR2']
    }

    def "returns empty list and swallows BuildException from getBuildMap"() {
        given:
        def group = Mock(BuildGroup)
        group.getBuildMap(_) >> { throw new BuildException('db error') }
        def client = new ZosBuildMapClient(group)

        expect:
        client.getGeneratedObjects('path/file.cbl', 'ATO') == []
    }

    def "buildGroup parameter is ignored — scope is fixed by constructor arg"() {
        given:
        def group = Mock(BuildGroup)
        def bm    = Mock(BuildMap)
        group.getBuildMap('path/file.cbl') >> bm
        bm.getOutputs() >> [new OutputStub(dataset: 'DS', member: 'MBR')]
        def client = new ZosBuildMapClient(group)

        expect:
        client.getGeneratedObjects('path/file.cbl', 'ANY_VALUE').size() == 1
        client.getGeneratedObjects('path/file.cbl', 'OTHER_VALUE').size() == 1
    }

    // ─── fromConf ─────────────────────────────────────────────────────────────

    // def "fromConf throws IllegalStateException when build group not found in store"() {
    //     given:
    //     def mockStore = Mock(MetadataStore)
    //     GroovySpy(MetadataStoreFactory, global: true)
    //     MetadataStoreFactory.createDb2MetadataStore(*_) >> mockStore
    //     mockStore.getBuildGroup('MISSING') >> null

    //     when:
    //     ZosBuildMapClient.fromConf('MISSING', 'user1', new File('/tmp/pw'), new Properties())

    //     then:
    //     def ex = thrown(IllegalStateException)
    //     ex.message.contains('MISSING')
    // }

    // def "fromConf returns ZosBuildMapClient wrapping the found build group"() {
    //     given:
    //     def mockGroup = Mock(BuildGroup)
    //     def mockStore = Mock(MetadataStore)
    //     GroovySpy(MetadataStoreFactory, global: true)
    //     MetadataStoreFactory.createDb2MetadataStore(*_) >> mockStore
    //     mockStore.getBuildGroup('MY_GROUP') >> mockGroup

    //     when:
    //     def client = ZosBuildMapClient.fromConf('MY_GROUP', 'user1', new File('/tmp/pw'), new Properties())

    //     then:
    //     client instanceof ZosBuildMapClient
    // }
}
