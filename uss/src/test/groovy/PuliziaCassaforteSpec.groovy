import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import java.nio.file.Path

/**
 * Drives {@code front-end/scripts/groovy/PuliziaCassaforte.groovy} as a real subprocess via
 * {@link CliRunner}, the way the CLI boundary is actually hit — as opposed to
 * {@code PuliziaCassaforteImplSpec} (library module), which calls
 * {@code PuliziaCassaforteImpl.doPuliziaCassaforte(...)} in-process.
 *
 * <p>Same source/rules/build-map fixtures as {@code PuliziaCassaforteImplSpec}, so outcomes line
 * up: {@code SOURCE_PATH}'s {@code .SZFSSWG} extension matches the {@code "SZFSSWG ;...;NO"} rule
 * in {@code fixtures/rules.csv}, resolving to {@code LIBRARY}/{@code MEMBER}.
 */
class PuliziaCassaforteSpec extends Specification {

    static final String ENV         = 'ATO'
    static final String BUILD_GROUP = 'ATO'
    static final String SOURCE_PATH = '/repo/cloned/ATO/yn_r_01_ato_r1/src/mapasm/batch/TESTMEM.SZFSSWG'

    @TempDir
    Path workDir

    File rulesFixture
    File stageMapFixture
    File buildMapFixture

    def setup() {
        rulesFixture    = new File(getClass().getResource('/fixtures/rules.csv').toURI())
        stageMapFixture = new File(System.getProperty('stageMapFixture'))
        buildMapFixture = new File(System.getProperty('buildMapFixture'))
    }

    def "C action is processed without error"() {
        given:
        writeConfig([:])
        def lista = listFile("C,${SOURCE_PATH}")

        when:
        def result = run(lista, ['--bmf', buildMapFixture.canonicalPath])

        then:
        result.exitCode == 0
    }

    def "malformed line (no comma) makes the process exit non-zero"() {
        given:
        writeConfig([:])
        def lista = listFile('MALFORMED_LINE')

        when:
        def result = run(lista, ['--bmf', buildMapFixture.canonicalPath])

        then:
        result.exitCode != 0
    }

    def "unknown action makes the process exit non-zero"() {
        given:
        writeConfig([:])
        def lista = listFile("X,${SOURCE_PATH}")

        when:
        def result = run(lista, ['--bmf', buildMapFixture.canonicalPath])

        then:
        result.exitCode != 0
    }

    def "--bmf selects the build map file directly"() {
        given:
        writeConfig([:])
        def lista = listFile("C,${SOURCE_PATH}")

        when:
        def result = run(lista, ['--bmf', buildMapFixture.canonicalPath])

        then:
        result.exitCode == 0
    }

    def "missing build map file fails with a clear error"() {
        given:
        writeConfig([:])
        def lista = listFile("C,${SOURCE_PATH}")

        when:
        def result = run(lista)

        then:
        result.exitCode != 0
        result.stderr.contains('build map file not found')
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void writeConfig(Map<String, String> overrides) {
        def props = new Properties()
        props.setProperty('fileOpsType', 'macos')
        props.setProperty('uxBasedir', workDir.toString())
        props.setProperty('rulesPath', rulesFixture.canonicalPath)
        props.setProperty('stageMapPath', stageMapFixture.canonicalPath)
        overrides.each { k, v -> props.setProperty(k, v) }
        new File(workDir.toFile(), 'PuliziaCassaforte.properties').withOutputStream { props.store(it, null) }
    }

    private File listFile(String content) {
        def f = new File(workDir.toFile(), 'lista.csv')
        f.text = content
        f
    }

    private CliResult run(File lista, List<String> extraArgs = []) {
        CliRunner.run(workDir.toFile(), [:], extraArgs + [lista.absolutePath, ENV, BUILD_GROUP])
    }
}
