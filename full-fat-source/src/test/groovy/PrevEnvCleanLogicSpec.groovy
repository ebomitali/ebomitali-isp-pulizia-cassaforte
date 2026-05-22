import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import java.nio.file.*

class PrevEnvCleanLogicSpec extends Specification {

    static final Map<String, String> STAGE_MAP = [
        '01|ATO': 'X2A', '01|ST': 'XAD', '01|PR': 'XPE',
    ]

    @TempDir
    Path tempDir

    PrevEnvCleanLogic logic
    LocalFileOps ops

    def setup() {
        def rulesFile = new File(getClass().getResource('/fixtures/rules.csv').toURI()).canonicalPath
        def bmFile    = new File(getClass().getResource('/fixtures/buildmap.json').toURI()).canonicalPath
        ops = new LocalFileOps(tempDir.toString())
        def deleteLogic = new DeleteCassaforteLogic(
            ops:      ops,
            rules:    new DeletionRulesLoader().load(rulesFile),
            buildMap: new LocalBuildMapClient(bmFile)
        )
        logic = new PrevEnvCleanLogic(
            deleteLogic: deleteLogic,
            extractor:   new PathVariableExtractor(),
            stageMap:    STAGE_MAP,
            hlq:         ''
        )
    }

    def "execute deletes from predecessor env library when current env has a predecessor"() {
        given:
        // ST predecessor is ATO → stage X2A
        def lib    = 'LTM00.D9PX2A.PE000.LING.COB@@@@@.@@.COPY'
        def member = tempDir.resolve("${lib}/PGMCOBOL")
        Files.createDirectories(member.parent)
        Files.writeString(member, 'content')

        when:
        def count = logic.execute(
            '/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl',
            'ACPYCOB ', 'ST', 'yn_r_01_ato_r1'
        )

        then:
        count == 1
        !ops.exists("//${lib}(PGMCOBOL)")
    }

    def "execute returns 0 when current env has no predecessor (ATO)"() {
        expect:
        logic.execute(
            '/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl',
            'ACPYCOB ', 'ATO', 'yn_r_01_ato_r1'
        ) == 0
    }
}
