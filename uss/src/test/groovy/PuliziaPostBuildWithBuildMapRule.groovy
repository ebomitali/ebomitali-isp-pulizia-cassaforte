import com.ibm.dbb.metadata.BuildGroup
import com.ibm.dbb.metadata.BuildMap
import spock.lang.Specification
import spock.lang.Shared
import spock.lang.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Exercises PuliziaPostBuild.groovy against a {@code BUILD MAP} deletion rule (as opposed to
 * PuliziaPostBuildSpec's {@code NO} rules, which delete by source name directly). A BUILD MAP
 * rule resolves the *generated* member names for a source via the DBB build map instead of
 * deriving them from the source's own filename — a single source can fan out to multiple
 * generated members.
 *
 * <p>Wires buildMapClientType=db2 with a mocked {@link BuildGroup}/{@link BuildMap} — the same
 * injection pattern documented on Db2BuildMapClient's constructor and exercised in isolation by
 * Db2BuildMapClientSpec, but here driven through the real PuliziaPostBuild.groovy script end to
 * end (config/context -> PuliziaCassaforteImpl -> DeleteCassaforteLogic -> Db2BuildMapClient).
 */
class PuliziaPostBuildWithBuildMapRule extends Specification {

    @TempDir Path dbbBuildDirPath
    @TempDir Path appDirPath
    @TempDir Path zosSimDirPath

    @Shared Path dbbConfDirPath
    @Shared Path dbbHomeDirPath

    static final String RULES  = 'SZFSSWG ;LTM00.D9P${C1STAGE}.PE000.LING.MAP@@@@@.@@.ZORO;BUILD MAP'
    static final String SOURCE = 'ATO/yo_y_01_ato_r1/src/ZOS/BATCH/SZFSSWG/MYSOURCE.SZFSSWG'
    static final String ATO_LIBRARY = 'LTM00.D9PX2A.PE000.LING.MAP@@@@@.@@.ZORO'

    /** Minimal stand-in for the IBM BuildMap output objects — same shape Db2BuildMapClientSpec uses. */
    static class OutputStub {
        String dataset
        String member
    }

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
        fixture.writeConfig(new File(System.getProperty('buildMapFixture')).absolutePath, 'db2')
        fixture.deployFatSource(new File(System.getProperty('fatSourceFile')))

        sourceFile = new File(appDirPath.toFile(), SOURCE)
        sourceFile.parentFile.mkdirs()
        sourceFile.text = ''
    }

    def "ST env deletes both build-map generated members from ATO predecessor cassaforte library"() {
        given: 'MYSOURCE has two generated elements, MYGEN1 and MYGEN2, on the resolved library'
        def atoLib = fixture.dataset(ATO_LIBRARY)
        fixture.member(atoLib, 'MYGEN1')
        fixture.member(atoLib, 'MYGEN2')

        def buildMap = Mock(BuildMap)
        buildMap.getOutputs() >> [
            new OutputStub(dataset: atoLib.name, member: 'MYGEN1'),
            new OutputStub(dataset: atoLib.name, member: 'MYGEN2'),
        ]
        def buildGroup = Mock(BuildGroup)
        buildGroup.getName() >> 'ST'
        buildGroup.getBuildMap(_) >> buildMap

        def config  = new FakeTaskVariables(vars: [FILE_PATH: sourceFile.absolutePath])
        def context = new FakeBuildContext(vars: [
            BUILD_ENV : 'ST',
            BUILD_GROUP: buildGroup,
            DBB_BUILD : dbbBuildDirPath.toFile().absolutePath,
            DBB_CONF  : dbbConfDirPath.toFile().absolutePath,
            DBB_HOME  : dbbHomeDirPath.toFile().absolutePath,
            APP_DIR   : appDirPath.toFile().absolutePath,
        ])
        def script  = fixture.loadPostBuild(postBuildFile, config, context)

        when:
        def result = script.run()

        then:
        result == 0
        !new File(atoLib, 'MYGEN1').exists()
        !new File(atoLib, 'MYGEN2').exists()
    }
}
