package com.intesasanpaolo.bes.pc
import com.ibm.dbb.build.BuildException
import com.ibm.dbb.metadata.BuildGroup
import com.ibm.dbb.metadata.BuildMap
import com.ibm.dbb.metadata.MetadataStore
import com.ibm.dbb.metadata.MetadataStoreFactory
import spock.lang.Specification

/**
 * Spock specification for {@link Db2BuildMapClient}.
 *
 * <p>IBM DBB classes are replaced by stubs from the {@code stubs} subproject, so
 * all tests run locally without mainframe access.
 */
class Db2BuildMapClientSpec extends Specification {

    /** Minimal stub for the IBM BuildMap output objects. */
    static class OutputStub {
        String dataset
        String member
    }

    // ─── constructor ─────────────────────────────────────────────────────────

    def "injection constructor accepts a BuildGroup without error"() {
        given:
        def group = Mock(BuildGroup)

        when:
        def client = new Db2BuildMapClient(group)

        then:
        client != null
        noExceptionThrown()
    }

    // ─── getGeneratedObjects ──────────────────────────────────────────────────

    def "returns empty list when build map has no entry for source path"() {
        given:
        def group = Mock(BuildGroup)
        group.getBuildMap(_) >> null
        def client = new Db2BuildMapClient(group)

        expect:
        client.getGeneratedObjects('some/path/file.cbl') == []
    }

    def "returns mapped outputs for known source path"() {
        given:
        def group = Mock(BuildGroup)
        def bm    = Mock(BuildMap)
        group.getBuildMap('ATO/path/YO8AMADD.SJCLINP') >> bm
        bm.getOutputs() >> [new OutputStub(dataset: 'LTM00.D9PX2A.PE000.@@@@.JINP', member: 'YO8AMADD')]
        def client = new Db2BuildMapClient(group)

        when:
        def results = client.getGeneratedObjects('ATO/path/YO8AMADD.SJCLINP')

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
        def client = new Db2BuildMapClient(group)

        expect:
        client.getGeneratedObjects('path/file.cbl') == []
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
        def client = new Db2BuildMapClient(group)

        when:
        def results = client.getGeneratedObjects('path/file.cbl')

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
        def client = new Db2BuildMapClient(group)

        when:
        def results = client.getGeneratedObjects('path/file.cbl')

        then:
        results.size() == 2
        results*.member == ['MBR1', 'MBR2']
    }

    def "returns empty list and swallows BuildException from getBuildMap"() {
        given:
        def group = Mock(BuildGroup)
        group.getBuildMap(_) >> { throw new BuildException('db error') }
        def client = new Db2BuildMapClient(group)

        expect:
        client.getGeneratedObjects('path/file.cbl') == []
    }

    def "scope is fixed by constructor arg — same results regardless of call context"() {
        given:
        def group = Mock(BuildGroup)
        def bm    = Mock(BuildMap)
        group.getBuildMap('path/file.cbl') >> bm
        bm.getOutputs() >> [new OutputStub(dataset: 'DS', member: 'MBR')]
        def client = new Db2BuildMapClient(group)

        expect:
        client.getGeneratedObjects('path/file.cbl').size() == 1
    }
}
