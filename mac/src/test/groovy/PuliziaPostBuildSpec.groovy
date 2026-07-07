import spock.lang.Specification
import spock.lang.TempDir
import java.nio.file.Path

class PuliziaPostBuildSpec extends Specification {

    @TempDir Path zosSimDirPath

    static final String RULES  = 'SJCL*   ;LTM00.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.SJCL;NO'
    static final String SOURCE = 'ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLCA7/YO810BDD.SJCLCA7'
    static final String ATO_LIBRARY = 'LTM00.D9PX2A.PE000.@@@@.@@@@@@@@.@@.SJCL'

    PuliziaPostBuildFixture fixture
    File postBuildFile

    def setup() {
        def dbbBuildDir = new File(System.getProperty('dbbBuildSimDir'))
        def cwd = new File('.').canonicalFile
        postBuildFile = new File(System.getProperty('frontEndPostBuildFile'))

        fixture = new PuliziaPostBuildFixture(cwd, dbbBuildDir, zosSimDirPath.toFile())
        fixture.writeRules(RULES)
        fixture.writeStageMap(new File(System.getProperty('stageMapFixture')))
        fixture.writeConfig(new File(System.getProperty('buildMapFixture')).absolutePath)

        new File(cwd, SOURCE).with {
            parentFile.mkdirs()
            text = ''
        }
    }

    def "ST env deletes stale member from ATO predecessor cassaforte library"() {
        given:
        def atoLib = fixture.dataset(ATO_LIBRARY)
        fixture.member(atoLib, 'YO810BDD')

        def config  = new FakeTaskVariables(vars: [FILE_PATH: SOURCE])
        def context = new FakeBuildContext(vars: [BUILD_ENV: 'ST', BUILD_GROUP: 'ST'])
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

        def config  = new FakeTaskVariables(vars: [FILE_PATH: SOURCE])
        def context = new FakeBuildContext(vars: [BUILD_ENV: 'ATO', BUILD_GROUP: 'ATO'])
        def script  = fixture.loadPostBuild(postBuildFile, config, context)

        when:
        def result = script.run()

        then:
        result == 0
        new File(atoLib, 'YO810BDD').exists()
    }
}
