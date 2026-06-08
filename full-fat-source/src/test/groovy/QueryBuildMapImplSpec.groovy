import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import java.nio.file.*

/**
 * Spock specification for {@link QueryBuildMapImpl}.
 *
 * <p>Tests use two paths:
 * <ul>
 *   <li>JSON fixture ({@code fixtures/buildmap.json}) via
 *       {@link QueryBuildMapOnZosImpl#run(String, String, File)} — no IBM deps.</li>
 *   <li>Inline {@link BuildMapClient} mock via
 *       {@link QueryBuildMapOnZosImpl#run(String, String, BuildMapClient)} — full isolation.</li>
 * </ul>
 *
 * <p>List file format: one {@code <ignored>,<sourcePath>} per line.
 */
class QueryBuildMapImplSpec extends Specification {

    static final String BUILD_GROUP = 'ATO'
    static final String SOURCE_PATH = 'ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YO8AMADD.SJCLINP'

    @TempDir
    Path tempDir

    QueryBuildMapImpl impl = new QueryBuildMapImpl()
    File bmFile

    def setup() {
        bmFile = new File(getClass().getResource('/fixtures/buildmap.json').toURI())
    }

    // ─── JSON fixture path ────────────────────────────────────────────────────

    def "run with JSON file returns 0 errors for known source path"() {
        given:
        def lista = listFile("Q,${SOURCE_PATH}")

        expect:
        impl.run(lista, BUILD_GROUP, bmFile) == 0
    }

    def "run with JSON file returns 0 errors for unknown source path (empty result, no exception)"() {
        given:
        def lista = listFile("Q,ATO/no/such/file.cbl")

        expect:
        impl.run(lista, BUILD_GROUP, bmFile) == 0
    }

    // ─── Inline BuildMapClient mock ───────────────────────────────────────────

    def "processes each source path and returns 0 errors"() {
        given:
        def results = [[library: 'LTM00.D9PO1.PE000.@@@@.@@@@@@@@.@@.SJCL', member: 'YO8AMADD']]
        def mock    = [getGeneratedObjects: { sp -> results }] as BuildMapClient
        def lista   = listFile("Q,${SOURCE_PATH}")

        expect:
        impl.run(lista, BUILD_GROUP, mock) == 0
    }

    def "blank lines and comments are skipped"() {
        given:
        def mock  = [getGeneratedObjects: { sp -> [] }] as BuildMapClient
        def lista = listFile("# comment\n\nQ,${SOURCE_PATH}\n")

        expect:
        impl.run(lista, BUILD_GROUP, mock) == 0
    }

    def "malformed line (no comma) increments error count"() {
        given:
        def mock  = [getGeneratedObjects: { sp -> [] }] as BuildMapClient
        def lista = listFile("MALFORMED_LINE")

        expect:
        impl.run(lista, BUILD_GROUP, mock) == 1
    }

    def "exception during getGeneratedObjects increments error count"() {
        given:
        def mock  = [getGeneratedObjects: { sp -> throw new RuntimeException("db error") }] as BuildMapClient
        def lista = listFile("Q,${SOURCE_PATH}")

        expect:
        impl.run(lista, BUILD_GROUP, mock) == 1
    }

    def "multiple lines: good and bad counted separately"() {
        given:
        def mock  = [getGeneratedObjects: { sp -> [] }] as BuildMapClient
        def lista = listFile("Q,${SOURCE_PATH}\nMALFORMED\nQ,${SOURCE_PATH}")

        expect:
        impl.run(lista, BUILD_GROUP, mock) == 1
    }

    def "processed count covers all successful queries regardless of result size"() {
        given:
        int callCount = 0
        def mock  = [getGeneratedObjects: { sp -> callCount++; [] }] as BuildMapClient
        def lista = listFile("Q,path/one.cbl\nQ,path/two.cbl\nQ,path/three.cbl")

        when:
        def errors = impl.run(lista, BUILD_GROUP, mock)

        then:
        errors == 0
        callCount == 3
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private String listFile(String content) {
        def f = tempDir.resolve('lista.csv').toFile()
        f.text = content
        return f.canonicalPath
    }
}
