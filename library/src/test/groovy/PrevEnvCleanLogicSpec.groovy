import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import java.nio.file.*

/**
 * Spock specification for {@link PrevEnvCleanLogic}.
 *
 * <p>Verifies that the post-build predecessor cleanup:
 * <ul>
 *   <li>Deletes the matching cassaforte member from the predecessor environment's PDS
 *       when running in ST or PR.</li>
 *   <li>Is a no-op (returns 0) for environments without a predecessor (ATI, ATO, EM).</li>
 * </ul>
 *
 * <p>Uses {@link LocalFileOps} rooted at a JUnit 5 {@code @TempDir} for filesystem isolation.
 */
class PrevEnvCleanLogicSpec extends Specification {

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
        logic = new PrevEnvCleanLogic(deleteLogic: deleteLogic)
    }

    def "execute deletes from predecessor env library when current env has a predecessor"() {
        given:
        // ST's predecessor is ATO (stage O1)
        def lib    = 'LTM00.D9PO1.PE000.LING.COB@@@@@.@@.COPY'
        def member = tempDir.resolve("${lib}/PGMCOBOL")
        Files.createDirectories(member.parent)
        Files.writeString(member, 'content')

        when:
        def count = logic.execute(
            '/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl',
            'ACPYCOB ', 'ST', '', 'yn_r_01_ato_r1'
        )

        then:
        count == 1
        !ops.exists("//${lib}(PGMCOBOL)")
    }

    def "execute returns 0 when current env has no predecessor (ATO)"() {
        expect:
        logic.execute(
            '/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl',
            'ACPYCOB ', 'ATO', '', 'yn_r_01_ato_r1'
        ) == 0
    }
}
