import spock.lang.Specification
import spock.lang.Unroll

class PatternMatcherSpec extends Specification {

    def matcher = new PatternMatcher()

    @Unroll
    def "matches('#pattern', '#value') == #expected"() {
        expect:
        matcher.matches(pattern, value) == expected

        where:
        pattern    | value         | expected
        '%CPYCOB*' | 'ACPYCOB '   | true
        '%CPYCOB*' | 'XCPYCOBABC' | true
        'SZFSSWG ' | 'SZFSSWG '   | true
        'SZFSSWG ' | 'SZFSSWGX'   | false
        '%CB2%R  ' | 'ACB2XR  '   | true
        '%CB2%R  ' | 'ACB2XRY '   | false
        'SJCL*'    | 'SJCL    '   | true
        'SJCL*'    | 'SJCLPROC'   | true
        'SJCL*'    | 'XJCL    '   | false
        '*'        | 'ANYTHING'   | true
    }
}
