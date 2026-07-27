import spock.lang.Specification
import spock.lang.Shared
import spock.lang.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Drives {@code front-end/scripts/groovy/PuliziaCassaforte.groovy} as a real subprocess via
 * {@link CliRunner}, the way the CLI boundary is actually hit — as opposed to
 * {@code PuliziaCassaforteImplSpec} (library module), which calls
 * {@code PuliziaCassaforteImpl.doPuliziaCassaforte(...)} in-process.
 *
 * <p>Uses {@link PuliziaFixture} to deploy the fat source and the CLI script into
 * {@code dbbBuildDir/groovy/pulizia-cassaforte/} the way they'd sit on a real {@code $DBB_BUILD} —
 * the subprocess is launched against that deployed copy, not the repo source file directly.
 *
 * <p>Same source/rules/build-map fixtures as {@code PuliziaCassaforteImplSpec}, so outcomes line
 * up: {@code SOURCE_PATH}'s {@code .SZFSSWG} extension matches the {@code "SZFSSWG ;...;NO"} rule
 * in {@code fixtures/rules.csv}, resolving to {@code LIBRARY}/{@code MEMBER}.
 */
class PuliziaCassaforteSpec extends Specification {

    static final String ENV         = 'ATO'
    static final String BUILD_GROUP = 'ATO'
    static final String SOURCE_PATH = '/repo/cloned/ATO/yn_r_01_ato_r1/src/mapasm/batch/TESTMEM.SZFSSWG'

    // Fresh per test: DBB_BUILD (fat-source + script deployment) and APP_DIR (script's own CWD).
    @TempDir Path dbbBuildDirPath
    @TempDir Path appDirPath
    @TempDir Path zosSimDirPath

    // Shared across all tests in this spec: DBB_HOME (only needed to satisfy the fixture ctor)
    // and the original wrapper.script path — setup() overwrites that system property every test
    // to point CliRunner at the freshly-deployed copy, so the original source path must be
    // captured once, before the first overwrite, or later tests would "deploy" from the
    // previous test's already-deleted temp copy instead of the real repo source file.
    @Shared Path dbbHomeDirPath
    @Shared String originalWrapperScript

    PuliziaFixture fixture
    File buildMapFixture
    File deployedScript

    def setupSpec() {
        dbbHomeDirPath = Files.createTempDirectory('dbb-home')
        originalWrapperScript = System.getProperty('wrapper.script')
    }

    def setup() {
        buildMapFixture = new File(System.getProperty('buildMapFixture'))

        fixture = new PuliziaFixture(
            dbbBuildDirPath.toFile(),
            dbbHomeDirPath.toFile(),
            appDirPath.toFile(),
            zosSimDirPath.toFile()
        )
        fixture.writeRules(new File(getClass().getResource('/fixtures/rules.csv').toURI()).text)
        fixture.writeStageMap(new File(System.getProperty('stageMapFixture')))
        fixture.writeConfig(buildMapFixture.canonicalPath)
        fixture.deployFatSourceToDbbBuild(new File(System.getProperty('fatSourceFile')))
        fixture.deployRunPuliziaCassaforteToDbbBuild(new File(originalWrapperScript))

        // CliRunner reads wrapper.script fresh on every call — point it at the copy just
        // deployed under dbbBuildDir, not the repo source file, so the subprocess exercises
        // the script the way it actually sits on a real $DBB_BUILD.
        deployedScript = new File(dbbBuildDirPath.toFile(), 'groovy/pulizia-cassaforte/RunPuliziaCassaforte.groovy')
        System.setProperty('wrapper.script', deployedScript.absolutePath)
    }

    def "C action is processed without error"() {
        given:
        def lista = listFile("C,${SOURCE_PATH}")

        when:
        def result = run(lista, ['--bmf', buildMapFixture.canonicalPath])

        then:
        result.exitCode == 0
    }

    def "malformed line (no comma) makes the process exit non-zero"() {
        given:
        def lista = listFile('MALFORMED_LINE')

        when:
        def result = run(lista, ['--bmf', buildMapFixture.canonicalPath])

        then:
        result.exitCode != 0
    }

    def "unknown action makes the process exit non-zero"() {
        given:
        def lista = listFile("X,${SOURCE_PATH}")

        when:
        def result = run(lista, ['--bmf', buildMapFixture.canonicalPath])

        then:
        result.exitCode != 0
    }

    def "--bmf selects the build map file directly"() {
        given:
        def lista = listFile("C,${SOURCE_PATH}")

        when:
        def result = run(lista, ['--bmf', buildMapFixture.canonicalPath])

        then:
        result.exitCode == 0
    }

    def "missing build map file fails with a clear error"() {
        given:
        def lista = listFile("C,${SOURCE_PATH}")

        when:
        def result = run(lista)

        then:
        result.exitCode != 0
        result.stderr.contains('build map file not found')
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private File listFile(String content) {
        def f = new File(appDirPath.toFile(), 'lista.csv')
        f.text = content
        f
    }

    private CliResult run(File lista, List<String> extraArgs = []) {
        CliRunner.run(appDirPath.toFile(), [:], extraArgs + [lista.absolutePath, ENV, BUILD_GROUP])
    }
}
