import spock.lang.Specification
import spock.lang.Unroll

class PathVariableExtractorSpec extends Specification {

    static final Map<String, String> STAGE_MAP = [
        '01|ATO': 'X2A', '01|ST': 'XAD', '01|PR': 'XAE',
        '03|ATO': 'Y2A', '03|ST': 'YAD',
        'STWSNCS|PR': 'XPE', 'STWSJGO|PR': 'XPE'
    ]

    def extractor = new PathVariableExtractor()

    def "extracts C1SYSTEM and C1STAGE from standard path format"() {
        when:
        def vars = extractor.extract(
            'ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YO8AMADD.SJCLINP',
            'ATO', STAGE_MAP, null
        )

        then:
        vars['C1SYSTEM'] == 'y'
        vars['C1STAGE']  == 'X2A'
        vars['HLQ']      == ''
    }

    def "works with absolute path containing prefix before ENV segment"() {
        when:
        def vars = extractor.extract(
            '/repo/cloned/ATO/yo_y_01_ato_r1/src/COBOL/batch/pgm.cbl',
            'ATO', STAGE_MAP, null
        )

        then:
        vars['C1SYSTEM'] == 'y'
        vars['C1STAGE']  == 'X2A'
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
