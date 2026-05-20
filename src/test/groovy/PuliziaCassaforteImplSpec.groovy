import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import java.nio.file.*

/**
 * Spock specification for {@link PuliziaCassaforteImpl} — JSON build map path only.
 *
 * <p>Uses {@link LocalFileOps} (rooted at a JUnit 5 {@code @TempDir}) and the classpath
 * fixtures ({@code fixtures/rules.csv}, {@code fixtures/buildmap.json}).  No IBM/DBB deps.
 *
 * <p>Source path uses extension {@code .SZFSSWG} (7 chars → padded to {@code "SZFSSWG "})
 * which exactly matches the {@code SZFSSWG } rule with flag {@code NO} in {@code rules.csv}:
 * <pre>
 *   library = LTM00.D9PO1.PE000.@@@@.@@@@@@@@.@@.ZARA  (stage O1 for ATO)
 *   member  = TESTMEM  (stem of the source filename)
 * </pre>
 */
class PuliziaCassaforteImplSpec extends Specification {

    static final String ENV         = 'ATO'
    static final String BUILD_GROUP = 'ATO'
    static final String SOURCE_PATH = '/repo/cloned/ATO/yn_r_01_ato_r1/src/mapasm/batch/TESTMEM.SZFSSWG'
    static final String LIBRARY     = 'LTM00.D9PO1.PE000.@@@@.@@@@@@@@.@@.ZARA'
    static final String MEMBER      = 'TESTMEM'

    @TempDir
    Path tempDir

    PuliziaCassaforteImpl impl
    LocalFileOps ops
    File bmFile

    def setup() {
        ops    = new LocalFileOps(tempDir.toString())
        impl   = new PuliziaCassaforteImpl()
        impl.rulesPath = new File(getClass().getResource('/fixtures/rules.csv').toURI()).canonicalPath
        bmFile = new File(getClass().getResource('/fixtures/buildmap.json').toURI())
    }

    def "C action deletes existing member"() {
        given:
        createMember(LIBRARY, MEMBER)
        def lista = listFile("C,${SOURCE_PATH}")

        when:
        def errors = impl.run(lista, ENV, BUILD_GROUP, bmFile)

        then:
        errors == 0
        !ops.exists("//${LIBRARY}(${MEMBER})")
    }

    def "C action is idempotent when member already absent"() {
        given:
        def lista = listFile("C,${SOURCE_PATH}")

        expect:
        impl.run(lista, ENV, BUILD_GROUP, bmFile) == 0
    }

    def "blank lines and comments are skipped"() {
        given:
        def lista = listFile("# comment\n\nC,${SOURCE_PATH}\n")

        expect:
        impl.run(lista, ENV, BUILD_GROUP, bmFile) == 0
    }

    def "malformed line (no comma) increments error count"() {
        given:
        def lista = listFile("MALFORMED_LINE")

        expect:
        impl.run(lista, ENV, BUILD_GROUP, bmFile) == 1
    }

    def "unknown action increments error count"() {
        given:
        def lista = listFile("X,${SOURCE_PATH}")

        expect:
        impl.run(lista, ENV, BUILD_GROUP, bmFile) == 1
    }

    def "multiple lines: good and bad counted separately"() {
        given:
        def lista = listFile("C,${SOURCE_PATH}\nMALFORMED\nX,${SOURCE_PATH}")

        expect:
        impl.run(lista, ENV, BUILD_GROUP, bmFile) == 2
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void createMember(String library, String member) {
        def path = tempDir.resolve("${library}/${member}")
        Files.createDirectories(path.parent)
        Files.writeString(path, 'content')
    }

    private String listFile(String content) {
        def f = tempDir.resolve('lista.csv').toFile()
        f.text = content
        return f.canonicalPath
    }
}
