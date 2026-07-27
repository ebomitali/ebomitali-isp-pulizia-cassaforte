import spock.lang.Specification
import spock.lang.Shared
import spock.lang.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PuliziaPostBuildSpec extends Specification {

    // Fresh per test: DBB_BUILD (fat-source + script deployment) and APP_DIR (source file's dir).
    @TempDir Path dbbBuildDirPath
    @TempDir Path appDirPath
    @TempDir Path zosSimDirPath

    // Shared across all tests in this spec: DBB_HOME (only needed to satisfy the fixture ctor).
    @Shared Path dbbHomeDirPath

    static final String RULES  = 'SJCL*   ;LTM00.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.SJCL;NO'
    static final String SOURCE = 'ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLCA7/YO810BDD.SJCLCA7'
    static final String ATO_LIBRARY = 'LTM00.D9PX2A.PE000.@@@@.@@@@@@@@.@@.SJCL'
    static final String MEMBER = 'YO810BDD'
    static final String FILE_EXT = 'SJCLCA7'

    PuliziaFixture fixture
    File postBuildFile
    File sourceFile

    def setupSpec() {
        dbbHomeDirPath = Files.createTempDirectory('dbb-home')
    }

    def setup() {
        postBuildFile = new File(System.getProperty('frontEndPostBuildFile'))

        fixture = new PuliziaFixture(
            dbbBuildDirPath.toFile(),
            dbbHomeDirPath.toFile(),
            appDirPath.toFile(),
            zosSimDirPath.toFile()
        )
        fixture.writeRules(RULES)
        fixture.writeStageMap(new File(System.getProperty('stageMapFixture')))
        fixture.deployFatSourceToDbbBuild(new File(System.getProperty('fatSourceFile')))
        writeFileOpsConfig()

        sourceFile = new File(appDirPath.toFile(), SOURCE)
        sourceFile.parentFile.mkdirs()
        sourceFile.text = ''
    }

    // PuliziaPostBuild.groovy hardcodes JzosFileService (real z/OS) unless an optional
    // PuliziaCassaforte.properties at DBB_BUILD selects fileOpsType=macos for local runs.
    private void writeFileOpsConfig() {
        def props = new Properties()
        props.setProperty('fileOpsType', 'macos')
        props.setProperty('uxBasedir', zosSimDirPath.toFile().absolutePath)
        new File(dbbBuildDirPath.toFile(), 'PuliziaCassaforte.properties').withOutputStream { props.store(it, null) }
    }

    private FakeBuildContext buildContext() {
        new FakeBuildContext(
            buildFile: sourceFile.absolutePath,
            workingDirectory: dbbBuildDirPath.toFile()
        )
    }

    def "ST env deletes stale member from ATO predecessor cassaforte library"() {
        given:
        def atoLib = fixture.dataset(ATO_LIBRARY)
        fixture.member(atoLib, 'YO810BDD')

        def config  = new FakeTaskVariables(vars: [MEMBER: MEMBER, FILE_EXT: FILE_EXT, CLI_BUILDENV: 'ST', CLI_BUILDGROUP: 'ST'])
        def context = buildContext()
        def script  = fixture.loadPostBuild(postBuildFile, config, context)

        when:
        def result = script.run()

        then:
        result == 0
        !new File(atoLib, 'YO810BDD').exists()
    }

    def "ATO env has no predecessor to clean, member stays untouched"() {
        given:
        def atoLib = fixture.dataset(ATO_LIBRARY)
        fixture.member(atoLib, 'YO810BDD')

        def config  = new FakeTaskVariables(vars: [MEMBER: MEMBER, FILE_EXT: FILE_EXT, CLI_BUILDENV: 'ATO', CLI_BUILDGROUP: 'ATO'])
        def context = buildContext()
        def script  = fixture.loadPostBuild(postBuildFile, config, context)

        when:
        def result = script.run()

        then:
        result == 0
        new File(atoLib, 'YO810BDD').exists()
    }
}
