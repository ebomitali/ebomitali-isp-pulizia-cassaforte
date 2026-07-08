import spock.lang.Specification
import spock.lang.Shared
import spock.lang.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PuliziaPostBuildSpec extends Specification {

    // Fresh per test: DBB_BUILD (fat-source deployment) and APP_DIR (script's own dir).
    @TempDir Path dbbBuildDirPath
    @TempDir Path appDirPath
    @TempDir Path zosSimDirPath

    // Shared across all tests in this spec: DBB_CONF and DBB_HOME.
    @Shared Path dbbConfDirPath
    @Shared Path dbbHomeDirPath

    static final String RULES  = 'SJCL*   ;LTM00.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.SJCL;NO'
    static final String SOURCE = 'ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLCA7/YO810BDD.SJCLCA7'
    static final String ATO_LIBRARY = 'LTM00.D9PX2A.PE000.@@@@.@@@@@@@@.@@.SJCL'

    PuliziaPostBuildFixture fixture
    File postBuildFile
    File sourceFile

    def setupSpec() {
        dbbConfDirPath = Files.createTempDirectory('dbb-conf')
        dbbHomeDirPath = Files.createTempDirectory('dbb-home')
    }

    def setup() {
        postBuildFile = new File(System.getProperty('frontEndPostBuildFile'))

        fixture = new PuliziaPostBuildFixture(
            dbbBuildDirPath.toFile(),
            dbbHomeDirPath.toFile(),
            appDirPath.toFile(),
            zosSimDirPath.toFile()
        )
        fixture.writeRules(RULES)
        fixture.writeStageMap(new File(System.getProperty('stageMapFixture')))
        fixture.writeConfig(new File(System.getProperty('buildMapFixture')).absolutePath)
        fixture.deployFatSource(new File(System.getProperty('fatSourceFile')))

        sourceFile = new File(appDirPath.toFile(), SOURCE)
        sourceFile.parentFile.mkdirs()
        sourceFile.text = ''
    }

    private FakeBuildContext buildContext(String env, String buildGroup) {
        new FakeBuildContext(vars: [
            BUILD_ENV : env,
            BUILD_GROUP: buildGroup,
            DBB_BUILD : dbbBuildDirPath.toFile().absolutePath,
            DBB_CONF  : dbbConfDirPath.toFile().absolutePath,
            DBB_HOME  : dbbHomeDirPath.toFile().absolutePath,
            APP_DIR   : appDirPath.toFile().absolutePath,
        ])
    }

    def "ST env deletes stale member from ATO predecessor cassaforte library"() {
        given:
        def atoLib = fixture.dataset(ATO_LIBRARY)
        fixture.member(atoLib, 'YO810BDD')

        def config  = new FakeTaskVariables(vars: [FILE_PATH: sourceFile.absolutePath])
        def context = buildContext('ST', 'ST')
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

        def config  = new FakeTaskVariables(vars: [FILE_PATH: sourceFile.absolutePath])
        def context = buildContext('ATO', 'ATO')
        def script  = fixture.loadPostBuild(postBuildFile, config, context)

        when:
        def result = script.run()

        then:
        result == 0
        new File(atoLib, 'YO810BDD').exists()
    }
}
