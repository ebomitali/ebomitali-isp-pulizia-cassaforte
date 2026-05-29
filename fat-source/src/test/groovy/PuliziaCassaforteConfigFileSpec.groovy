import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import java.nio.file.*

/**
 * Integration test for PuliziaCassaforteImpl.run(String, String, String, String).
 *
 * Exercises the config-file code path with buildMapPath + uxBasedir.
 * Runs against the fat-source compilation — no separate zos jar needed.
 */
class PuliziaCassaforteConfigFileSpec extends Specification {

    static final String ENV         = 'ATO'
    static final String BUILD_GROUP = 'ATO'
    static final String SOURCE_PATH = '/repo/cloned/ATO/yn_r_01_ato_r1/src/mapasm/batch/TESTMEM.SZFSSWG'

    @TempDir
    Path tempDir

    def "run with config file processes C action without error"() {
        given:
        def configFile = writeConfig([
            buildMapClientType: 'json',
            buildMapPath: fixtureFile('buildmap.json'),
            fileOpsType : 'local',
            uxBasedir   : tempDir.toString(),
            rulesPath   : fixtureFile('rules.csv'),
            stageMapPath: fixtureFile('stagemap.csv'),
        ])
        def lista = listFile("C,${SOURCE_PATH}")

        when:
        def impl   = new PuliziaCassaforteImpl()
        def errors = impl.run(lista, ENV, BUILD_GROUP, configFile)

        then:
        errors == 0
    }

    def "run with config file counts malformed line as error"() {
        given:
        def configFile = writeConfig([
            buildMapClientType: 'json',
            buildMapPath: fixtureFile('buildmap.json'),
            fileOpsType : 'local',
            uxBasedir   : tempDir.toString(),
            rulesPath   : fixtureFile('rules.csv'),
            stageMapPath: fixtureFile('stagemap.csv'),
        ])
        def lista = listFile("C,${SOURCE_PATH}\nBAD_LINE")

        when:
        def impl   = new PuliziaCassaforteImpl()
        def errors = impl.run(lista, ENV, BUILD_GROUP, configFile)

        then:
        errors == 1
    }

    def "run with config file missing both userId and buildMapPath throws"() {
        given:
        def configFile = writeConfig([uxBasedir: tempDir.toString()])
        def lista = listFile("C,${SOURCE_PATH}")

        when:
        new PuliziaCassaforteImpl().run(lista, ENV, BUILD_GROUP, configFile)

        then:
        thrown(IllegalArgumentException)
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private String fixtureFile(String name) {
        new File(getClass().getResource("/fixtures/${name}").toURI()).canonicalPath
    }

    private String writeConfig(Map<String, String> entries) {
        def f     = tempDir.resolve('config.properties').toFile()
        def props = new Properties()
        entries.each { k, v -> if (v != null) props.setProperty(k, v) }
        f.withOutputStream { props.store(it, null) }
        f.canonicalPath
    }

    private String listFile(String content) {
        def f = tempDir.resolve('lista.csv').toFile()
        f.text = content
        f.canonicalPath
    }
}
