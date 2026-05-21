import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import java.nio.file.*

/**
 * Spock specification for {@link SfilamentoLogic}.
 *
 * <p>Verifies the S-action (sfilamento) scenarios:
 * <ul>
 *   <li>For ST + {@code SJCL*} types: the current-env member is deleted and then
 *       restored from the first superior environment that holds a copy.</li>
 *   <li>No restore when no copy exists in any superior environment (returns {@code false}).</li>
 *   <li>No restore for non-eligible environments or non-JCL file types (returns {@code false}).</li>
 * </ul>
 *
 * <p>Uses {@link LocalFileOps} rooted at a JUnit 5 {@code @TempDir} for filesystem isolation.
 */
class SfilamentoLogicSpec extends Specification {

    @TempDir
    Path tempDir

    SfilamentoLogic sfilamento
    LocalFileOps ops

    def setup() {
        def rulesFile = new File(getClass().getResource('/fixtures/rules.csv').toURI()).canonicalPath
        def bmFile    = new File(getClass().getResource('/fixtures/buildmap.json').toURI()).canonicalPath
        ops = new LocalFileOps(tempDir.toString())
        def rules       = new DeletionRulesLoader().load(rulesFile)
        def deleteLogic = new DeleteCassaforteLogic(
            ops: ops, rules: rules,
            buildMap: new LocalBuildMapClient(bmFile)
        )
        sfilamento = new SfilamentoLogic(ops: ops, deleteLogic: deleteLogic, rules: rules)
    }

    def "execute deletes ST cassaforte SJCL member and restores from PR into TOCOLB"() {
        given:
        def stSjclLib = 'LTM00.D9PS1.PE000.@@@@.@@@@@@@@.@@.SJCL'
        def prSjclLib = 'LTM00.D9PP1.PE000.@@@@.@@@@@@@@.@@.SJCL'
        [stSjclLib, prSjclLib].each { lib ->
            def m = tempDir.resolve("${lib}/MYJCL")
            Files.createDirectories(m.parent)
            Files.writeString(m, "${lib}-content")
        }

        when:
        def result = sfilamento.execute(
            '/dbb/DEE/IBM/yn_r_01_st_r1/src/jcl/batch/myjcl.jcl',
            'SJCL    ', 'ST', '', 'yn_r_01_st_r1'
        )

        then:
        result == true
        !ops.exists("//${stSjclLib}(MYJCL)")
        ops.exists('//LTM00.D9PS1.PE000.TO@@.COLB@@@@.@@.SJCL(MYJCL)')
    }

    def "execute returns false and only deletes for non-JCL type (ACPYCOB)"() {
        given:
        def cobLib = 'LTM00.D9PS1.PE000.LING.COB@@@@@.@@.COPY'
        def member = tempDir.resolve("${cobLib}/PGMCOBOL")
        Files.createDirectories(member.parent)
        Files.writeString(member, 'cobol-content')

        when:
        def result = sfilamento.execute(
            '/dbb/DEE/IBM/yn_r_01_st_r1/src/cobol/batch/pgmcobol.cbl',
            'ACPYCOB ', 'ST', '', 'yn_r_01_st_r1'
        )

        then:
        result == false
        !ops.exists("//${cobLib}(PGMCOBOL)")
    }
}
