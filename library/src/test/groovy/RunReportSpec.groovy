import groovy.json.JsonSlurper
import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import java.nio.file.*
import java.time.Instant

class RunReportSpec extends Specification {

    static final String LIST_FILE   = '/tmp/lista.csv'
    static final String ENVIRONMENT = 'ATO'
    static final String BUILD_GROUP = 'yn_r_01_ato_r1'

    @TempDir
    Path tempDir

    RunReport report

    def setup() {
        report = new RunReport(LIST_FILE, ENVIRONMENT, BUILD_GROUP)
    }

    // ─── writeTo: top-level fields ────────────────────────────────────────────

    def "writeTo writes file at given path"() {
        given:
        def out = tempDir.resolve('report.json').toString()

        when:
        report.writeTo(out)

        then:
        new File(out).exists()
    }

    def "writeTo JSON contains correct top-level metadata"() {
        given:
        def out = tempDir.resolve('report.json').toString()

        when:
        report.writeTo(out)
        def json = new JsonSlurper().parseText(new File(out).text)

        then:
        json.listFile    == LIST_FILE
        json.environment == ENVIRONMENT
        json.buildGroup  == BUILD_GROUP
    }

    def "writeTo timestamp is valid ISO-8601 instant"() {
        given:
        def out = tempDir.resolve('report.json').toString()
        def before = Instant.now()

        when:
        report.writeTo(out)
        def after = Instant.now()
        def json  = new JsonSlurper().parseText(new File(out).text)
        def ts    = Instant.parse(json.timestamp as String)

        then:
        !ts.isBefore(before)
        !ts.isAfter(after)
    }

    def "writeTo with no entries produces empty files array"() {
        given:
        def out = tempDir.resolve('report.json').toString()

        when:
        report.writeTo(out)
        def json = new JsonSlurper().parseText(new File(out).text)

        then:
        json.files == []
    }

    // ─── addEntry + writeTo ───────────────────────────────────────────────────

    def "addEntry single match with deleted element appears in report"() {
        given:
        def rule    = new DeletionRule(typePattern: 'ACPYCOB*', libraryTemplate: 'LIB.${C1STAGE}', useBuildMap: false)
        def matches = [new MatchResult(rule: rule, library: 'LIB.X2A', deletedElement: '//LIB.X2A(PGMCOBOL)')]
        def out     = tempDir.resolve('report.json').toString()

        when:
        report.addEntry('/src/pgmcobol.cbl', 'ACPYCOB', matches)
        report.writeTo(out)
        def json  = new JsonSlurper().parseText(new File(out).text)
        def entry = json.files[0]

        then:
        json.files.size() == 1
        entry.sourcePath  == '/src/pgmcobol.cbl'
        entry.fileType    == 'ACPYCOB'
        entry.matches.size() == 1

        with(entry.matches[0]) {
            library        == 'LIB.X2A'
            deletedElement == '//LIB.X2A(PGMCOBOL)'
            rule.typePattern     == 'ACPYCOB*'
            rule.libraryTemplate == 'LIB.${C1STAGE}'
            rule.useBuildMap     == false
        }
    }

    def "addEntry match with null deletedElement is serialized as null"() {
        given:
        def rule    = new DeletionRule(typePattern: 'ACPYCOB*', libraryTemplate: 'LIB.${C1STAGE}', useBuildMap: false)
        def matches = [new MatchResult(rule: rule, library: 'LIB.X2A', deletedElement: null)]
        def out     = tempDir.resolve('report.json').toString()

        when:
        report.addEntry('/src/absent.cbl', 'ACPYCOB', matches)
        report.writeTo(out)
        def json = new JsonSlurper().parseText(new File(out).text)

        then:
        json.files[0].matches[0].deletedElement == null
    }

    def "addEntry match with useBuildMap true is reflected in rule"() {
        given:
        def rule    = new DeletionRule(typePattern: 'SZFSSWG', libraryTemplate: 'MAP.${C1STAGE}', useBuildMap: true)
        def matches = [new MatchResult(rule: rule, library: 'MAP.X2A', deletedElement: '//MAP.X2A(MAPOBJ)')]
        def out     = tempDir.resolve('report.json').toString()

        when:
        report.addEntry('/src/mapobj.asm', 'SZFSSWG', matches)
        report.writeTo(out)
        def json = new JsonSlurper().parseText(new File(out).text)

        then:
        json.files[0].matches[0].rule.useBuildMap == true
    }

    def "addEntry with empty matches list produces entry with empty matches array"() {
        given:
        def out = tempDir.resolve('report.json').toString()

        when:
        report.addEntry('/src/nomatch.cbl', 'UNKNOWN', [])
        report.writeTo(out)
        def json = new JsonSlurper().parseText(new File(out).text)

        then:
        json.files[0].matches == []
    }

    def "addEntry multiple entries preserves insertion order"() {
        given:
        def rule = new DeletionRule(typePattern: '*', libraryTemplate: 'LIB', useBuildMap: false)
        def mk   = { path -> new MatchResult(rule: rule, library: 'LIB', deletedElement: null) }
        def out  = tempDir.resolve('report.json').toString()

        when:
        report.addEntry('/src/first.cbl',  'TYPE1', [mk('/src/first.cbl')])
        report.addEntry('/src/second.cbl', 'TYPE2', [mk('/src/second.cbl')])
        report.addEntry('/src/third.cbl',  'TYPE3', [mk('/src/third.cbl')])
        report.writeTo(out)
        def json = new JsonSlurper().parseText(new File(out).text)

        then:
        json.files.size() == 3
        json.files[0].sourcePath == '/src/first.cbl'
        json.files[1].sourcePath == '/src/second.cbl'
        json.files[2].sourcePath == '/src/third.cbl'
    }

    def "writeTo produces valid pretty-printed JSON"() {
        given:
        def out = tempDir.resolve('report.json').toString()

        when:
        report.writeTo(out)
        def text = new File(out).text

        then:
        text.contains('\n')
        new JsonSlurper().parseText(text) != null
    }
}
