package com.intesasanpaolo.bes.pc
import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import java.nio.file.*

class PuliziaCassaforteConfigSpec extends Specification {

    @TempDir
    Path tempDir

    // ─── validate() ──────────────────────────────────────────────────────────

    def "validate: passes for complete json config"() {
        given:
        def rules    = tempDir.resolve('rules.csv').toFile()   ; rules.text    = ''
        def stageMap = tempDir.resolve('stagemap.csv').toFile(); stageMap.text = ''
        def bmap     = tempDir.resolve('buildmap.json').toFile(); bmap.text    = '{}'
        def cfg = new PuliziaCassaforteConfig(
            buildMapClientType: 'json',
            rulesPath:          rules.canonicalPath,
            stageMapPath:       stageMap.canonicalPath,
            buildMapPath:       bmap.canonicalPath
        )

        expect:
        cfg.validate() == null   // no exception
    }

    def "validate: passes for complete db2 config"() {
        given:
        def rules    = tempDir.resolve('rules.csv').toFile()    ; rules.text    = ''
        def stageMap = tempDir.resolve('stagemap.csv').toFile() ; stageMap.text = ''
        def pwFile   = tempDir.resolve('pw').toFile()           ; pwFile.text   = ''
        def db2Conf  = tempDir.resolve('db2.conf').toFile()     ; db2Conf.text  = ''
        def cfg = new PuliziaCassaforteConfig(
            buildMapClientType: 'db2',
            rulesPath:          rules.canonicalPath,
            stageMapPath:       stageMap.canonicalPath,
            userId:             'bob',
            pwFilePath:         pwFile.canonicalPath,
            db2ConfigPath:      db2Conf.canonicalPath
        )

        expect:
        cfg.validate() == null
    }

    def "validate: throws when rulesPath is null"() {
        given:
        def cfg = new PuliziaCassaforteConfig(buildMapClientType: 'json')

        when:
        cfg.validate()

        then:
        thrown(IllegalArgumentException)
    }

    def "validate: throws when rulesPath does not exist"() {
        given:
        def cfg = new PuliziaCassaforteConfig(
            buildMapClientType: 'json',
            rulesPath:          '/no/such/rules.csv'
        )

        when:
        cfg.validate()

        then:
        thrown(IllegalArgumentException)
    }

    def "validate: throws when stageMapPath does not exist"() {
        given:
        def rules = tempDir.resolve('rules.csv').toFile(); rules.text = ''
        def cfg   = new PuliziaCassaforteConfig(
            buildMapClientType: 'json',
            rulesPath:          rules.canonicalPath,
            stageMapPath:       '/no/such/stagemap.csv'
        )

        when:
        cfg.validate()

        then:
        thrown(IllegalArgumentException)
    }

    def "validate: throws for json when buildMapPath is null"() {
        given:
        def rules    = tempDir.resolve('rules.csv').toFile()   ; rules.text    = ''
        def stageMap = tempDir.resolve('stagemap.csv').toFile(); stageMap.text = ''
        def cfg = new PuliziaCassaforteConfig(
            buildMapClientType: 'json',
            rulesPath:          rules.canonicalPath,
            stageMapPath:       stageMap.canonicalPath
        )

        when:
        cfg.validate()

        then:
        thrown(IllegalArgumentException)
    }

    def "validate: throws for json when buildMapPath does not exist"() {
        given:
        def rules    = tempDir.resolve('rules.csv').toFile()   ; rules.text    = ''
        def stageMap = tempDir.resolve('stagemap.csv').toFile(); stageMap.text = ''
        def cfg = new PuliziaCassaforteConfig(
            buildMapClientType: 'json',
            rulesPath:          rules.canonicalPath,
            stageMapPath:       stageMap.canonicalPath,
            buildMapPath:       '/no/such/buildmap.json'
        )

        when:
        cfg.validate()

        then:
        thrown(IllegalArgumentException)
    }

    def "validate: throws for db2 when no credentials provided"() {
        given:
        def rules    = tempDir.resolve('rules.csv').toFile()   ; rules.text    = ''
        def stageMap = tempDir.resolve('stagemap.csv').toFile(); stageMap.text = ''
        def cfg = new PuliziaCassaforteConfig(
            buildMapClientType: 'db2',
            rulesPath:          rules.canonicalPath,
            stageMapPath:       stageMap.canonicalPath
        )

        when:
        cfg.validate()

        then:
        thrown(IllegalArgumentException)
    }

    def "validate: throws for db2 when credentials are partial"() {
        given:
        def rules    = tempDir.resolve('rules.csv').toFile()   ; rules.text    = ''
        def stageMap = tempDir.resolve('stagemap.csv').toFile(); stageMap.text = ''
        def cfg = new PuliziaCassaforteConfig(
            buildMapClientType: 'db2',
            rulesPath:          rules.canonicalPath,
            stageMapPath:       stageMap.canonicalPath,
            userId:             'bob'   // pwFilePath and db2ConfigPath absent
        )

        when:
        cfg.validate()

        then:
        thrown(IllegalArgumentException)
    }

    def "validate: throws for unknown buildMapClientType"() {
        given:
        def rules    = tempDir.resolve('rules.csv').toFile()   ; rules.text    = ''
        def stageMap = tempDir.resolve('stagemap.csv').toFile(); stageMap.text = ''
        def cfg = new PuliziaCassaforteConfig(
            buildMapClientType: 'unknown',
            rulesPath:          rules.canonicalPath,
            stageMapPath:       stageMap.canonicalPath
        )

        when:
        cfg.validate()

        then:
        thrown(IllegalArgumentException)
    }

    def "from: all known properties are mapped to config fields"() {
        given:
        def props = new Properties()
        props.setProperty('fileOpsType',        'local')
        props.setProperty('buildMapClientType', 'json')
        props.setProperty('rulesPath',          '/path/to/rules.csv')
        props.setProperty('stageMapPath',       '/path/to/stage-map.csv')
        props.setProperty('uxBasedir',          '/tmp/sim')
        props.setProperty('hlq',                'U0G9700')
        props.setProperty('buildMapPath',       '/path/to/buildmap.json')
        props.setProperty('userId',             'bob')
        props.setProperty('pwFilePath',         '/path/to/pw')
        props.setProperty('db2ConfigPath',      '/path/to/db2.conf')
        props.setProperty('jobzExtensions',     'jobn, jobz')

        when:
        def cfg = PuliziaCassaforteConfig.from(props)

        then:
        cfg.fileOpsType        == 'local'
        cfg.buildMapClientType == 'json'
        cfg.rulesPath          == '/path/to/rules.csv'
        cfg.stageMapPath       == '/path/to/stage-map.csv'
        cfg.uxBasedir          == '/tmp/sim'
        cfg.hlq                == 'U0G9700'
        cfg.buildMapPath       == '/path/to/buildmap.json'
        cfg.userId             == 'bob'
        cfg.pwFilePath         == '/path/to/pw'
        cfg.db2ConfigPath      == '/path/to/db2.conf'
        cfg.jobzExtensions     == ['JOBN', 'JOBZ'] as Set
    }

    def "from: absent properties keep field defaults"() {
        when:
        def cfg = PuliziaCassaforteConfig.from(new Properties())

        then:
        cfg.fileOpsType        == 'zos'
        cfg.buildMapClientType == 'db2'
        cfg.rulesPath          == 'build-data/rules.csv'
        cfg.stageMapPath       == 'build-data/stagemap.csv'
        cfg.uxBasedir          == null
        cfg.hlq                == null
        cfg.buildMapPath       == null
        cfg.userId             == null
        cfg.pwFilePath         == null
        cfg.db2ConfigPath      == null
        cfg.jobzExtensions     == ['STWSNCS', 'STWSJGO', 'STWSJGM'] as Set
    }

    def "from: property with empty value keeps field default"() {
        given:
        def props = new Properties()
        props.setProperty('fileOpsType',        '')
        props.setProperty('buildMapClientType', '')

        when:
        def cfg = PuliziaCassaforteConfig.from(props)

        then:
        cfg.fileOpsType        == 'zos'
        cfg.buildMapClientType == 'db2'
    }

    def "from: env var placeholder in property value is expanded"() {
        given:
        def home = System.getenv('HOME')
        def props = new Properties()
        props.setProperty('uxBasedir', '${HOME}/sim')

        when:
        def cfg = PuliziaCassaforteConfig.from(props)

        then:
        cfg.uxBasedir == "${home}/sim"
    }

    def "expandEnvVars: \${VAR} syntax is expanded"() {
        given:
        def home = System.getenv('HOME')

        expect:
        PuliziaCassaforteConfig.expandEnvVars('${HOME}/path') == "${home}/path"
    }

    def "expandEnvVars: \$VAR syntax is expanded"() {
        given:
        def home = System.getenv('HOME')

        expect:
        PuliziaCassaforteConfig.expandEnvVars('$HOME/path') == "${home}/path"
    }

    def "expandEnvVars: unset variable is kept verbatim"() {
        expect:
        PuliziaCassaforteConfig.expandEnvVars('${__NO_SUCH_VAR_XYZ__}/path') == '${__NO_SUCH_VAR_XYZ__}/path'
    }

    def "expandEnvVars: null and empty input are returned unchanged"() {
        expect:
        PuliziaCassaforteConfig.expandEnvVars(null) == null
        PuliziaCassaforteConfig.expandEnvVars('')   == ''
    }
}
