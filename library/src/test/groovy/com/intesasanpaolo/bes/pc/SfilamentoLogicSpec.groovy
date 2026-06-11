package com.intesasanpaolo.bes.pc
import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import java.nio.file.*

class SfilamentoLogicSpec extends Specification {

    static final Map<String, String> STAGE_MAP = [
        '01|ATO': 'X2A',
        '01|ST' : 'XAD',
        '01|PR' : 'XAE',
    ]

    static final LibraryNameResolver RESOLVER      = new LibraryNameResolver()
    static final String              JNCS_TEMPLATE = 'LTM00.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.JNCS'
    static final String              JJGO_TEMPLATE = 'LTM00.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.JJGO'
    static final String              SJCL_TEMPLATE = 'LTM00.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.SJCL'
    static final String              SJINP_TEMPLATE = 'LTM00.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.SJINP'

    @TempDir
    Path tempDir

    SfilamentoLogic sfilamento
    MacosFileService ops

    def setup() {
        ops = new MacosFileService(tempDir.toString())
        def rules = [
            new DeletionRule(typePattern: 'STWSNCS', libraryTemplate: JNCS_TEMPLATE, useBuildMap: false),
            new DeletionRule(typePattern: 'STWSJGO', libraryTemplate: JJGO_TEMPLATE, useBuildMap: false),
            new DeletionRule(typePattern: 'SJCL*',   libraryTemplate: SJCL_TEMPLATE,  useBuildMap: false),
            new DeletionRule(typePattern: '%JCLINP', libraryTemplate: SJINP_TEMPLATE, useBuildMap: false),
        ]
        def bmFile = new File(getClass().getResource('/fixtures/buildmap.json').toURI()).canonicalPath
        def deleteLogic = new DeleteCassaforteLogic(
            ops: ops, rules: rules,
            buildMap: new JsonBuildMapClient('', new PuliziaCassaforteConfig(buildMapPath: bmFile))
        )
        sfilamento = new SfilamentoLogic(
            ops:            ops,
            deleteLogic:    deleteLogic,
            rules:          rules,
            extractor:      new PathVariableExtractor(),
            stageMap:       STAGE_MAP,
            hlq:            '',
            jobzExtensions: ['STWSNCS','STWSJGO','STWSJGM'] as Set
        )
    }

    def "execute SJCLINP in ATO: deletes SJINP member, does not restore (eligible but env has superiors)"() {
        given:
        def atoSjinpLib = RESOLVER.resolve(SJINP_TEMPLATE, [C1STAGE: 'X2A'])
        def m = tempDir.resolve("${atoSjinpLib}/YO8AMBDD")
        Files.createDirectories(m.parent)
        Files.writeString(m, "${atoSjinpLib}-content")

        when:
        def result = sfilamento.execute(
            'ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YO8AMBDD.SJCLINP',
            'SJCLINP', 'ATO', 'ATO'
        )

        then:
        result == false
        !ops.exists("//${atoSjinpLib}(YO8AMBDD)")
    }

    def "execute STWSNCS in ATO: deletes JNCS member, does not restore"() {
        given:
        def atoJncsLib = RESOLVER.resolve(JNCS_TEMPLATE, [C1STAGE: 'X2A'])
        def m = tempDir.resolve("${atoJncsLib}/MYJCL")
        Files.createDirectories(m.parent)
        Files.writeString(m, "${atoJncsLib}-content")

        when:
        def result = sfilamento.execute('edux0-jobz/MYJCL.STWSNCS', 'STWSNCS', 'ATO', 'ATO')

        then:
        result == false
        !ops.exists("//${atoJncsLib}(MYJCL)")
    }

    def "execute STWSJGO in ST: deletes JJGO member without restore (not eligible)"() {
        given:
        def stJjgoLib = RESOLVER.resolve(JJGO_TEMPLATE, [C1STAGE: 'XAD'])
        def m = tempDir.resolve("${stJjgoLib}/MYJOB")
        Files.createDirectories(m.parent)
        Files.writeString(m, 'jjgo-content')

        when:
        def result = sfilamento.execute(
            'ST/yn_r_01_st_r1/src/jcl/batch/stwsjgo/MYJOB.STWSJGO',
            'STWSJGO', 'ST', 'ST'
        )

        then:
        result == false
        !ops.exists("//${stJjgoLib}(MYJOB)")
    }

    def "execute STWSNCS in ST when no PR copy exists: deletes, does not restore"() {
        given:
        def stJncsLib = RESOLVER.resolve(JNCS_TEMPLATE, [C1STAGE: 'XAD'])
        def m = tempDir.resolve("${stJncsLib}/MYMEM")
        Files.createDirectories(m.parent)
        Files.writeString(m, "${stJncsLib}-content")

        when:
        def result = sfilamento.execute('edux0-jobz/MYMEM.STWSNCS', 'STWSNCS', 'ST', 'ST')

        then:
        result == false
        !ops.exists("//${stJncsLib}(MYMEM)")
    }

    def "execute STWSNCS in ST: deletes ST JNCS member and restores from PR into TOCOLB"() {
        given:
        // ST -> XAD, PR -> XAE according to stage map
        def stJncsLib = RESOLVER.resolve(JNCS_TEMPLATE, [C1STAGE: 'XAD'])
        def prJncsLib = RESOLVER.resolve(JNCS_TEMPLATE, [C1STAGE: 'XAE'])
        [stJncsLib, prJncsLib].each { lib ->
            def m = tempDir.resolve("${lib}/\$HXQ001")
            Files.createDirectories(m.parent)
            Files.writeString(m, "${lib}-content")
        }

        when:
        def result = sfilamento.execute('edux0-jobz/$HXQ001.STWSNCS', 'STWSNCS', 'ST', 'ST')

        then:
        result == true
        !ops.exists("//${stJncsLib}(\$HXQ001)")
        ops.exists("//${RESOLVER.toTocolbLibrary(stJncsLib)}(\$HXQ001)")
    }

    def "execute STWSNCS in PR: deletes PR JNCS member only (degraded to C)"() {
        given:
        def stJncsLib = RESOLVER.resolve(JNCS_TEMPLATE, [C1STAGE: 'XAD'])
        def prJncsLib = RESOLVER.resolve(JNCS_TEMPLATE, [C1STAGE: 'XAE'])
        [stJncsLib, prJncsLib].each { lib ->
            def m = tempDir.resolve("${lib}/\$HXQ003")
            Files.createDirectories(m.parent)
            Files.writeString(m, "${lib}-content")
        }

        when:
        def result = sfilamento.execute('edux0-jobz/$HXQ003.STWSNCS', 'STWSNCS', 'PR', 'PR')

        then:
        result == true
        !ops.exists("//${prJncsLib}(\$HXQ003)")
        ops.exists("//${stJncsLib}(\$HXQ003)")
    }

}
