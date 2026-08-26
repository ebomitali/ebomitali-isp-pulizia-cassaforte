package com.intesasanpaolo.bes.pt

import groovy.util.logging.Slf4j
import spock.lang.Specification
import spock.lang.Shared
import spock.lang.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Drives {@code pulizia-temporanei} fat entry point (PuliziaTemporanei.groovy) as a real subprocess
 * via {@link CliRunner}, the way the CLI boundary is actually hit — as opposed to
 * {@code PuliziaTemporaneiImplSpec/PuliziaTemporaneiImpl2Spec}, which call
 * {@code PuliziaTemporaneiImpl.doPuliziaTemporanei(...)} in-process.
 *
 * <p>Uses {@link PuliziaTemporaneiFixture} to deploy the fat source jar and CLI script into
 * {@code workDir/groovy/pulizia-temporanei/} the way they'd sit on a real {@code $DBB_BUILD} —
 * the subprocess is launched against that deployed copy, not the repo source file directly.
 *
 * <p>Test scenarios mirrored from {@code PuliziaTemporaneiImpl2Spec} to ensure consistency:
 * pattern matching (*, %), exact DSN, wildcard behavior.
 */
@Slf4j
class PuliziaCassaforteViaShellSpec extends Specification {

    // Fresh per test: DBB_BUILD (jar + script deployment) and APP_DIR (script's own CWD).
    @TempDir Path workDirPath
    @TempDir Path appDirPath
    @TempDir Path zosSimDirPath

    // Shared across all tests in this spec: DBB_HOME and original wrapper.script path
    @Shared String originalWrapperScript
    @Shared File jarFile

    PuliziaTemporaneiFixture fixture
    File deployedScript
    File deployedShellScript

    def setupSpec() {
        originalWrapperScript = System.getProperty('wrapper.script')
    }

    def setup() {
        fixture = new PuliziaTemporaneiFixture(
            workDirPath.toFile(),
            zosSimDirPath.toFile()
        )
        fixture.writeConfig('macos')

        // Deploy shell script to workDir (entry point for test execution)
        def shellScriptResource = getClass().getResource('/run-pulizia-temporanei.sh')
        if (!shellScriptResource) {
            throw new IllegalStateException('run-pulizia-temporanei.sh not found in integrationTest resources')
        }
        def shellScriptFile = new File(shellScriptResource.toURI())
        fixture.deployShellScriptToWork(shellScriptFile)
        deployedShellScript = new File(workDirPath.toFile(), 'run-pulizia-temporanei.sh')

        // Deploy fat source (build output) to workDir
        def fatSourceFile = new File(System.getProperty('fatSourceFile'))
        if (!fatSourceFile.exists()) {
            throw new IllegalStateException("Fat source file not found: ${fatSourceFile.absolutePath}")
        }
        fixture.deployFullPuliziaTemporaneiToWorkDir(fatSourceFile)

        // Deploy fe entry point (from src/main/groovy) to workDir
        def feEntryPointFile = new File(System.getProperty('feEntryPointFile'))
        if (!feEntryPointFile.exists()) {
            throw new IllegalStateException("FE entry point file not found: ${feEntryPointFile.absolutePath}")
        }
        fixture.deployRunPuliziaTemporaneiToWorkDir(feEntryPointFile)

        // Deploy logback config to workDir so subprocess can find it
        def logbackConfigResource = getClass().getResource('/logback.xml')
        if (logbackConfigResource) {
            def logbackConfigFile = new File(logbackConfigResource.toURI())
            fixture.deployLogbackConfigToWorkDir(logbackConfigFile)
        }

        // Deploy SLF4J jars to workDir/lib for subprocess use
        def slf4jApiJarPath = System.getProperty('slf4jApiJar')
        def slf4jSimpleJarPath = System.getProperty('slf4jSimpleJar')
        def jarFiles = []
        if (slf4jApiJarPath) jarFiles << new File(slf4jApiJarPath)
        if (slf4jSimpleJarPath) jarFiles << new File(slf4jSimpleJarPath)
        if (jarFiles) {
            fixture.deploySlf4jJar(jarFiles)
        }

        System.setProperty('wrapper.script', deployedShellScript.absolutePath)
    }

    def "delete matching datasets with wildcard asterisk"() {
        given:
        def simDsnDir = zosSimDirPath.toFile()
        fixture.dataset('MY/TEMP/ABC')
        fixture.dataset('MY/TEMP/XYZ')
        fixture.dataset('MY/PERM/DATA')

        when:
        def result = runPuliziaTemporanei(['MY.TEMP.*'])

        then:
        result.exitCode == 0
        result.stdout.contains('Successfully deleted 2')
        !new File(simDsnDir, 'MY/TEMP/ABC').exists()
        !new File(simDsnDir, 'MY/TEMP/XYZ').exists()
        new File(simDsnDir, 'MY/PERM/DATA').exists()
    }

    def "no datasets match pattern returns zero"() {
        given:
        def simDsnDir = zosSimDirPath.toFile()
        fixture.dataset('MY/PERM/DATA')

        when:
        def result = runPuliziaTemporanei(['MY.TEMP.*'])

        then:
        result.exitCode == 0
        result.stdout.contains('Successfully deleted 0')
        new File(simDsnDir, 'MY/PERM/DATA').exists()
    }

    def "exact DSN pattern deletes single dataset"() {
        given:
        def simDsnDir = zosSimDirPath.toFile()
        fixture.dataset('MY/TEMP/ABC')
        fixture.dataset('MY/TEMP/XYZ')

        when:
        def result = runPuliziaTemporanei(['MY.TEMP.ABC'])

        then:
        result.exitCode == 0
        result.stdout.contains('Successfully deleted 1')
        !new File(simDsnDir, 'MY/TEMP/ABC').exists()
        new File(simDsnDir, 'MY/TEMP/XYZ').exists()
    }

    def "wildcard percent matches single character only"() {
        given:
        def simDsnDir = zosSimDirPath.toFile()
        fixture.dataset('MY/TEMP/A')
        fixture.dataset('MY/TEMP/B')
        fixture.dataset('MY/TEMP/AB')
        fixture.dataset('NOT/MATCH/DSN')

        when:
        def result = runPuliziaTemporanei(['MY.TEMP.%'])

        then:
        result.exitCode == 0
        result.stdout.contains('Successfully deleted 2')
        !new File(simDsnDir, 'MY/TEMP/A').exists()
        !new File(simDsnDir, 'MY/TEMP/B').exists()
        new File(simDsnDir, 'MY/TEMP/AB').exists()
        new File(simDsnDir, 'NOT/MATCH/DSN').exists()
    }

    def "missing config file fails with error"() {
        given:
        fixture.dataset('MY/TEMP/ABC')

        when:
        def result = runPuliziaTemporaneiWithArgs(['MY.TEMP.*', '-c', '/nonexistent/config.properties'])

        then:
        result.exitCode != 0
    }

    def "missing DSN pattern fails with error"() {
        given:
        when:
        def result = runPuliziaTemporaneiWithArgs([])

        then:
        result.exitCode != 0
        result.stderr.contains('DSN pattern argument required')
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private CliResult runPuliziaTemporanei(List<String> dsnPatternArgs) {
        def cfgFile = new File(workDirPath.toFile(), 'PuliziaTemporanei.properties').absolutePath
        def groovyClasspath = System.getProperty('groovyClasspath')
        log.info("GROOVY_CLASSPATH: ${groovyClasspath}")
        def env = ['GROOVY_CLASSPATH': groovyClasspath]
        def result = CliRunner.runShellScript(
            workDirPath.toFile(),
            deployedShellScript,
            env,
            dsnPatternArgs + ['-c', cfgFile]
        )
        if (result.stdout) {
            println(">>> Script stdout:\n${result.stdout}")
        }
        if (result.stderr) {
            println(">>> Script stderr:\n${result.stderr}")
        }
        result
    }

    private CliResult runPuliziaTemporaneiWithArgs(List<String> args) {
        def groovyClasspath = System.getProperty('groovyClasspath')
        def env = ['GROOVY_CLASSPATH': groovyClasspath]
        def result = CliRunner.runShellScript(
            workDirPath.toFile(),
            deployedShellScript,
            env,
            args
        )
        if (result.stdout) {
            println(">>> Script stdout:\n${result.stdout}")
        }
        if (result.stderr) {
            println(">>> Script stderr:\n${result.stderr}")
        }
        result
    }
}
