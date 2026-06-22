package com.intesasanpaolo.bes.pc
import spock.lang.Specification

class PathVariableExtractorSpec extends Specification {

    static final Map<String, String> STAGE_MAP = [
        '01|ATO': 'X2A', '01|ST': 'XAD', '01|PR': 'XPE',
        '03|ATO': 'Y2A', '03|ST': 'YAD',
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
}
