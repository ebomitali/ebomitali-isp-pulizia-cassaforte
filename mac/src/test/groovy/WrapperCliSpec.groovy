import spock.lang.Specification
import spock.lang.TempDir
import java.nio.file.Path

class WrapperCliSpec extends Specification {

    @TempDir Path workDir

    File config
    Map<String, String> env

    def setup() {
        // ---- set up the context ----
        config = new File(workDir.toFile(), 'app.conf')
        config.text = """
            input.dir = ${workDir}/in
            output.dir = ${workDir}/out
        """.stripIndent()
        new File(workDir.toFile(), 'in').mkdirs()
        new File(workDir.toFile(), 'out').mkdirs()
        new File(workDir.toFile(), 'in/sample.dat').text = 'payload'

        env = ['APP_HOME': workDir.toString()]
    }

    def "prints usage and exits 2 when no args are given"() {
        when:
        def r = CliRunner.run(workDir.toFile(), env, [])

        then:
        r.exitCode == 2
        r.stderr.contains('Usage:')
        r.stdout.isEmpty()
    }

    def "processes the input file and exits 0"() {
        when:
        def r = CliRunner.run(workDir.toFile(), env,
                ['--config', config.absolutePath, '--verbose'])

        then:
        r.exitCode == 0
        r.stdout.contains('Done')
        new File(workDir.toFile(), 'out/sample.out').exists()
    }

    def "reports a bad config on stderr and exits non-zero"() {
        when:
        def r = CliRunner.run(workDir.toFile(), env,
                ['--config', '/does/not/exist.conf'])

        then:
        r.exitCode != 0
        r.stderr.contains('config')
    }
}