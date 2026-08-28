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
 * <p>Wires the {@code context.get('BUILD_GROUP')} branch of PuliziaPostBuild.groovy's build-map
 * resolution with a mocked {@link BuildGroup}/{@link BuildMap}, driving the real script end to
 * end (config/context -> DbbBuildMapClient -> DeleteCassaforteLogic).
 */
class PuliziaPostBuildWithBuildMapRule extends Specification {

    @TempDir Path dbbBuildDirPath
    @TempDir Path appDirPath
    @TempDir Path zosSimDirPath

    @Shared Path dbbHomeDirPath

    static final String RULES  = 'SZFSSWG ;LTM00.D9P${C1STAGE}.PE000.LING.MAP@@@@@.@@.ZORO;BUILD MAP'
    static final String SOURCE = 'ATO/yo_y_01_ato_r1/src/ZOS/BATCH/SZFSSWG/MYSOURCE.SZFSSWG'
    static final String ATO_LIBRARY = 'LTM00.D9PX2A.PE000.LING.MAP@@@@@.@@.ZORO'
    static final String MEMBER = 'MYSOURCE'
    static final String FILE_EXT = 'SZFSSWG'

    /** Minimal stand-in for the IBM BuildMap output objects — same shape Db2BuildMapClientSpec uses. */
    static class OutputStub {
        String dataset
        String member
    }

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

        def config  = new FakeTaskVariables(vars: [MEMBER: MEMBER, FILE_EXT: FILE_EXT, CLI_BUILDENV: 'ST', CLI_BUILDGROUP: 'ST'])
        def context = new FakeBuildContext(
            buildFile: sourceFile.absolutePath,
            workingDirectory: dbbBuildDirPath.toFile(),
            vars: [BUILD_GROUP: buildGroup]
        )
        def script  = fixture.loadPostBuild(postBuildFile, config, context)

        when:
        def result = script.run()

        then:
        result == 0
        !new File(atoLib, 'MYGEN1').exists()
        !new File(atoLib, 'MYGEN2').exists()
    }
}
