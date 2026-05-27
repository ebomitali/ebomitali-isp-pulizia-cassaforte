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

    static final String HLQ_SOURCE_PATH =
        '/repo/cloned/ATO/yo_y_01_ato_r1/src/mapasm/batch/TESTMEM.SZFSSWG'
    static final String HLQ_LIBRARY =
        'U0G9700.D9PX2A.PE000.@@@@.@@@@@@@@.@@.ZARA'
    static final String HLQ_MEMBER = 'TESTMEM'

    @TempDir
    Path tempDir

    PuliziaCassaforteImpl impl
    LocalFileOps ops
    File bmFile

    def setup() {
        ops    = new LocalFileOps(tempDir.toString())
        impl   = new PuliziaCassaforteImpl()
        impl.rulesPath    = new File(getClass().getResource('/fixtures/rules.csv').toURI()).canonicalPath
        impl.stageMapPath = new File(getClass().getResource('/fixtures/stagemap.csv').toURI()).canonicalPath
        bmFile = new File(getClass().getResource('/fixtures/buildmap.json').toURI())
    }

    def "C action is processed without error"() {
        given:
        def lista = listFile("C,${SOURCE_PATH}")

        expect:
        runImpl(lista) == 0
    }

    def "C action is idempotent when member already absent"() {
        given:
        def lista = listFile("C,${SOURCE_PATH}")

        expect:
        runImpl(lista) == 0
    }

    def "blank lines and comments are skipped"() {
        given:
        def lista = listFile("# comment\n\nC,${SOURCE_PATH}\n")

        expect:
        runImpl(lista) == 0
    }

    def "malformed line (no comma) increments error count"() {
        given:
        def lista = listFile("MALFORMED_LINE")

        expect:
        runImpl(lista) == 1
    }

    def "unknown action increments error count"() {
        given:
        def lista = listFile("X,${SOURCE_PATH}")

        expect:
        runImpl(lista) == 1
    }

    def "multiple lines: good and bad counted separately"() {
        given:
        def lista = listFile("C,${SOURCE_PATH}\nMALFORMED\nX,${SOURCE_PATH}")

        expect:
        runImpl(lista) == 2
    }

    def "C action with HLQ resolves template and deletes correct member"() {
        given:
        def hlqImpl = new PuliziaCassaforteImpl()
        hlqImpl.rulesPath    = new File(getClass().getResource('/fixtures/rules-hlq.csv').toURI()).canonicalPath
        hlqImpl.stageMapPath = new File(getClass().getResource('/fixtures/stagemap.csv').toURI()).canonicalPath

        def member = tempDir.resolve("${HLQ_LIBRARY}/${HLQ_MEMBER}")
        Files.createDirectories(member.parent)
        Files.writeString(member, 'content')

        def lista = listFile("C,${HLQ_SOURCE_PATH}")

        when:
        def props = new Properties()
        props.setProperty('buildMapClientType', 'json')
        props.setProperty('buildMapPath', bmFile.canonicalPath)
        props.setProperty('fileOpsType', 'local')
        props.setProperty('uxBasedir', tempDir.toString())
        props.setProperty('hlq', 'U0G9700')
        def errors = hlqImpl.run(lista, 'ATO', 'yo_y_01_ato_r1', props)

        then:
        errors == 0
        !ops.exists("//${HLQ_LIBRARY}(${HLQ_MEMBER})")
    }

    // ─── config-file run ──────────────────────────────────────────────────────

    def "config-file: C action processed without error (buildMapPath + uxBasedir)"() {
        given:
        def configFile = writeConfig([
            buildMapClientType: 'json',
            buildMapPath: bmFile.canonicalPath,
            fileOpsType : 'local',
            uxBasedir   : tempDir.toString()
        ])
        def lista = listFile("C,${SOURCE_PATH}")

        expect:
        impl.run(lista, ENV, BUILD_GROUP, configFile) == 0
    }

    def "config-file: rulesPath and stageMapPath overrides are honoured"() {
        given:
        def configFile = writeConfig([
            buildMapClientType: 'json',
            buildMapPath: bmFile.canonicalPath,
            fileOpsType : 'local',
            uxBasedir   : tempDir.toString(),
            rulesPath   : new File(getClass().getResource('/fixtures/rules.csv').toURI()).canonicalPath,
            stageMapPath: new File(getClass().getResource('/fixtures/stagemap.csv').toURI()).canonicalPath
        ])
        def lista = listFile("C,${SOURCE_PATH}")

        expect:
        impl.run(lista, ENV, BUILD_GROUP, configFile) == 0
    }

    def "config-file: hlq is passed through"() {
        given:
        def hlqImpl = new PuliziaCassaforteImpl()
        hlqImpl.rulesPath    = new File(getClass().getResource('/fixtures/rules-hlq.csv').toURI()).canonicalPath
        hlqImpl.stageMapPath = new File(getClass().getResource('/fixtures/stagemap.csv').toURI()).canonicalPath

        def member = tempDir.resolve("${HLQ_LIBRARY}/${HLQ_MEMBER}")
        Files.createDirectories(member.parent)
        Files.writeString(member, 'content')

        def configFile = writeConfig([
            buildMapClientType: 'json',
            buildMapPath: bmFile.canonicalPath,
            fileOpsType : 'local',
            uxBasedir   : tempDir.toString(),
            hlq         : 'U0G9700'
        ])
        def lista = listFile("C,${HLQ_SOURCE_PATH}")

        when:
        def errors = hlqImpl.run(lista, 'ATO', 'yo_y_01_ato_r1', configFile)

        then:
        errors == 0
        !ops.exists("//${HLQ_LIBRARY}(${HLQ_MEMBER})")
    }

    def "config-file: missing both userId and buildMapPath throws"() {
        given:
        def configFile = writeConfig([uxBasedir: tempDir.toString()])
        def lista = listFile("C,${SOURCE_PATH}")

        when:
        impl.run(lista, ENV, BUILD_GROUP, configFile)

        then:
        thrown(IllegalArgumentException)
    }

    def "config-file: partial credentials (userId without pwFilePath) throws"() {
        given:
        def configFile = writeConfig([
            buildMapClientType: 'db2',
            userId: 'bob',
            uxBasedir: tempDir.toString()
        ])
        def lista = listFile("C,${SOURCE_PATH}")

        when:
        impl.run(lista, ENV, BUILD_GROUP, configFile)

        then:
        thrown(IllegalArgumentException)
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private int runImpl(String lista) {
        def props = new Properties()
        props.setProperty('buildMapClientType', 'json')
        props.setProperty('buildMapPath', bmFile.canonicalPath)
        props.setProperty('fileOpsType', 'local')
        props.setProperty('uxBasedir', tempDir.toString())
        impl.run(lista, ENV, BUILD_GROUP, props)
    }

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

    private String writeConfig(Map<String, String> entries) {
        def f = tempDir.resolve('config.properties').toFile()
        def props = new Properties()
        entries.each { k, v -> if (v != null) props.setProperty(k, v) }
        f.withOutputStream { props.store(it, null) }
        return f.canonicalPath
    }
}
