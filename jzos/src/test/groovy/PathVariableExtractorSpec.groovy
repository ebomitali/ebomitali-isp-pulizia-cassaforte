import spock.lang.Specification
import spock.lang.Unroll

// Specs from meeting 13/06/26, repo name is always the second segment of the path, e.g. 'ATO' in 'ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YO8AMADD.SJCLINP'.

class PathVariableExtractorSpec extends Specification {

    static final Map<String, String> STAGE_MAP = [
        '01|ATO': 'X2A', '01|ST': 'XAD', '01|PR': 'XAE',
        '03|ATO': 'Y2A', '03|ST': 'YAD',
        'STWSNCS|PR': 'XPE', 'STWSJGO|PR': 'XPE'
    ]

    def extractor = new PathVariableExtractor()

    @Unroll("extract env=#buildEnv sourcePath=#sourcePath expects C1SYSTEM=#expectedC1SYSTEM C1STAGE=#expectedC1STAGE")
    def "extracts C1SYSTEM and C1STAGE in multiple scenarios"() {
        when:
        def vars = extractor.extract(
            sourcePath, buildEnv, STAGE_MAP, null
        )

        then:
        vars['C1SYSTEM'] == expectedC1SYSTEM
        vars['C1STAGE']  == expectedC1STAGE
        vars['HLQ']      == ''

        where:
        buildEnv | sourcePath | expectedC1SYSTEM | expectedC1STAGE
        'ATO'    | 'ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YO8AMADD.SJCLINP' | 'y' | 'X2A'
        'ST'     | 'ST/yo_y_01_st_r1/src/JCL/BATCH/SJCLINP/YO8AMADD.SJCLINP' | 'y' | 'XAD'
        'PR'     | 'PR/yo_y_01_pr_r1/src/JCL/BATCH/SJCLINP/YO8AMADD.SJCLINP' | 'y' | 'XAE'
        'ATO'    | 'ATO/yo_x_01_ato_r1/src/JCL/COPYBOOKS/ASMCPY.SCPYASM' | 'x' | 'X2A'
    }

    def "sets HLQ from parameter"() {
        when:
        def vars = extractor.extract(
            'ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YO8AMADD.SJCLINP',
            'ATO', STAGE_MAP, 'U0G9700'
        )

        then:
        vars['HLQ'] == 'U0G9700'
    }

    def "HLQ is empty string when null parameter passed"() {
        when:
        def vars = extractor.extract(
            'ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YO8AMADD.SJCLINP',
            'ATO', STAGE_MAP, null
        )

        then:
        vars['HLQ'] == ''
    }

    def "extracts from different PATH_LO yielding different C1STAGE"() {
        when:
        def vars = extractor.extract(
            'ATO/xo_n_03_ato_r1/src/COBOL/batch/pgm.cbl',
            'ATO', STAGE_MAP, null
        )

        then:
        vars['C1SYSTEM'] == 'n'
        vars['C1STAGE']  == 'Y2A'
    }

    def "throws IllegalArgumentException when PATH_LO|BUILD_ENV key not in stage map"() {
        when:
        extractor.extract(
            'ATO/yo_y_99_ato_r1/src/JCL/f.jcl',
            'ATO', STAGE_MAP, null
        )

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('99|ATO')
    }

    def "throws IllegalArgumentException when no application segment found in path"() {
        when:
        extractor.extract('/just/a/flat/path/file.ext', 'ATO', STAGE_MAP, null)

        then:
        thrown(IllegalArgumentException)
    }

    @Unroll("extractJobz env=#buildEnv fileType=#fileType expects C1STAGEP=#expectedC1STAGEP C1STAGE=#expectedC1STAGE")
    def "extractJobz with multiple scenarios"() {
        // Jobz files (fileType 'STWSNCS','STWSJGO','STWSJGM') returns C1STAGEP same as C1STAGE
        when:
        def vars = extractor.extractJobz(buildEnv, STAGE_MAP, null, fileType)

        then:
        vars['C1STAGEP'] == expectedC1STAGEP
        vars['C1STAGE']  == expectedC1STAGE
        vars['C1SYSTEM'] == ''
        vars['HLQ']      == ''

        where:
        buildEnv | fileType    | expectedC1STAGEP | expectedC1STAGE
        'PR'     | 'STWSNCS'   | 'XPE'            | 'XPE'
        'ATO'    | 'STWSNCS'   | 'X2A'            | 'X2A'
        'PR'     | 'STWSJGO'   | 'XPE'            | 'XPE'
        'ATO'    | 'STWSJGO'   | 'X2A'            | 'X2A'
        'PR'     | 'STWSJGM'   | 'XAE'            | 'XAE'
        'ST'     | 'STWSJGM'   | 'XAD'            | 'XAD'
    }
}
