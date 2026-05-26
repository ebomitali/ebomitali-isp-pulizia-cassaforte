import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import java.nio.file.*

class DeleteCassaforteLogicSpec extends Specification {

    @TempDir
    Path tempDir

    LocalFileOps ops
    DeleteCassaforteLogic logic

    def setup() {
        def rulesFile = new File(getClass().getResource('/fixtures/rules.csv').toURI())
        def bmFile    = new File(getClass().getResource('/fixtures/buildmap.json').toURI()).canonicalPath
        ops   = new LocalFileOps(tempDir.toString())
        logic = new DeleteCassaforteLogic(
            ops:      ops,
            rules:    new DeletionRulesLoader().load(rulesFile),
            buildMap: new LocalBuildMapClient(bmFile)
        )
    }

    def "execute deletes member by source name (NO flag)"() {
        given:
        def lib    = 'LTM00.D9PX2A.PE000.LING.COB@@@@@.@@.COPY'
        def member = tempDir.resolve("${lib}/PGMCOBOL")
        Files.createDirectories(member.parent)
        Files.writeString(member, 'content')

        when:
        def count = logic.execute(
            '/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl',
            'ACPYCOB ',
            [C1STAGE: 'X2A', C1SYSTEM: 'r', HLQ: ''],
            'yn_r_01_ato_r1'
        )

        then:
        count == 1
        !ops.exists("//${lib}(PGMCOBOL)")
    }

    def "execute is idempotent when member is already absent"() {
        expect:
        logic.execute(
            '/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl',
            'ACPYCOB ',
            [C1STAGE: 'X2A', C1SYSTEM: 'r', HLQ: ''],
            'yn_r_01_ato_r1'
        ) == 0
    }

    def "execute resolves member name via BUILD MAP and deletes by generated object"() {
        given:
        def sourcePath = '/dbb/DEE/IBM/yn_r_01_ato_r1/src/mapasm/batch/mapobj.asm'
        def buildGroup = 'yn_r_01_ato_r1'
        def lib        = 'LTM00.D9PX2A.PE000.LING.MAP@@@@@.@@.COPY'
        def member     = tempDir.resolve("${lib}/MAPOBJ")
        Files.createDirectories(member.parent)
        Files.writeString(member, 'content')

        def buildMapMock = [getGeneratedObjects: { sp, bg ->
            sp == sourcePath && bg == buildGroup
                ? [[library: lib, member: 'MAPOBJ']]
                : []
        }] as BuildMapClient

        def localLogic = new DeleteCassaforteLogic(
            ops:      ops,
            rules:    logic.rules,
            buildMap: buildMapMock
        )

        when:
        def count = localLogic.execute(
            sourcePath, 'SZFSSWG ',
            [C1STAGE: 'X2A', C1SYSTEM: 'r', HLQ: ''],
            buildGroup
        )

        then:
        count == 1
        !ops.exists("//${lib}(MAPOBJ)")
    }

    def "memberName extracts uppercase stem without extension"() {
        expect:
        DeleteCassaforteLogic.memberName('/path/to/abcdef.cbl') == 'ABCDEF'
        DeleteCassaforteLogic.memberName('/path/to/NOEEXT')     == 'NOEEXT'
    }
}
