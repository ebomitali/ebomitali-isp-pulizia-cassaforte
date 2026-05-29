import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import java.nio.file.*

class SfilamentoLogicSpec extends Specification {

    static final Map<String, String> STAGE_MAP = [
        '01|ATO': 'X2A', '01|ST': 'XAD', '01|PR': 'XPE',
        '03|ATO': 'Y2A', '03|ST': 'YAD',
    ]

    @TempDir
    Path tempDir

    SfilamentoLogic sfilamento
    LocalFileOps ops

    def setup() {
        def rulesFile = new File(getClass().getResource('/fixtures/rules.csv').toURI())
        def bmFile    = new File(getClass().getResource('/fixtures/buildmap.json').toURI()).canonicalPath
        ops = new LocalFileOps(tempDir.toString())
        def rules       = new DeletionRulesLoader().load(rulesFile)
        def deleteLogic = new DeleteCassaforteLogic(
            ops: ops, rules: rules,
            buildMap: new LocalBuildMapClient(bmFile)
        )
        sfilamento = new SfilamentoLogic(
            ops:         ops,
            deleteLogic: deleteLogic,
            rules:       rules,
            extractor:   new PathVariableExtractor(),
            stageMap:    STAGE_MAP,
            hlq:         ''
        )
    }

    def "execute deletes ST cassaforte SJCL member and restores from PR into TOCOLB"() {
        given:
        def stSjclLib = 'LTM00.D9PXAD.PE000.@@@@.@@@@@@@@.@@.SJCL'
        def prSjclLib = 'LTM00.D9PXPE.PE000.@@@@.@@@@@@@@.@@.SJCL'
        [stSjclLib, prSjclLib].each { lib ->
            def m = tempDir.resolve("${lib}/MYJCL")
            Files.createDirectories(m.parent)
            Files.writeString(m, "${lib}-content")
        }

        when:
        def result = sfilamento.execute(
            'ST/yn_r_01_st_r1/src/jcl/batch/MYJCL.SJCLINP',
            'SJCLINP', 'ST', 'ST'
        )

        then:
        result == true
        !ops.exists("//${stSjclLib}(MYJCL)")
        ops.exists('//LTM00.D9PXAD.PE000.TO@@.COLB@@@@.@@.SJCL(MYJCL)')
    }

    def "execute in ATO deletes cassaforte member but does not restore into TOCOLB"() {
        given:
        def atoSjclLib = 'LTM00.D9PX2A.PE000.@@@@.@@@@@@@@.@@.SJCL'
        def m = tempDir.resolve("${atoSjclLib}/MYJCL")
        Files.createDirectories(m.parent)
        Files.writeString(m, "${atoSjclLib}-content")

        when:
        def result = sfilamento.execute(
            'ATO/yn_r_01_ato_r1/src/jcl/batch/MYJCL.SJCLINP',
            'SJCLINP', 'ATO', 'ATO'
        )

        then:
        result == false
        !ops.exists('//LTM00.D9PX2A.PE000.TO@@.COLB@@@@.@@.SJCL(MYJCL)')
    }

    def "execute returns false and only deletes for non-JCL type (ACPYCOB)"() {
        given:
        def cobLib = 'LTM00.D9PXAD.PE000.LING.COB@@@@@.@@.COPY' //XAD System Test
        def member = tempDir.resolve("${cobLib}/TESTCPY.ACPYCOB")
        Files.createDirectories(member.parent)
        Files.writeString(member, 'cobol-content')

        when:
        def result = sfilamento.execute(
            'ST/yn_r_01_st_r1/src/cobol/batch/TESTCPY.ACPYCOB',
            'ACPYCOB', 'ST', 'ST'
        )

        then:
        result == false
        !ops.exists("//${cobLib}(TESTCPY)")
    }
}
