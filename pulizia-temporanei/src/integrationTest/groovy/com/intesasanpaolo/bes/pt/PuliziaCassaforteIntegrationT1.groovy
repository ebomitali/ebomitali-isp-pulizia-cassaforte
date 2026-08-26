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
 * {@code dbbBuildDir/groovy/pulizia-temporanei/} the way they'd sit on a real {@code $DBB_BUILD} —
 * the subprocess is launched against that deployed copy, not the repo source file directly.
 *
 * <p>Test scenarios mirrored from {@code PuliziaTemporaneiImpl2Spec} to ensure consistency:
 * pattern matching (*, %), exact DSN, wildcard behavior.
 */
@Slf4j
class PuliziaCassaforteIntegrationT1 extends Specification {

    // Fresh per test: DBB_BUILD (jar + script deployment) and APP_DIR (script's own CWD).
    @TempDir Path dbbBuildDirPath
    @TempDir Path appDirPath
    @TempDir Path zosSimDirPath

    // Shared across all tests in this spec: DBB_HOME and original wrapper.script path
    @Shared Path dbbHomeDirPath
    @Shared String originalWrapperScript
    @Shared File jarFile

    PuliziaTemporaneiFixture fixture
    File deployedScript
    File deployedShellScript

    def setupSpec() {
        dbbHomeDirPath = Files.createTempDirectory('dbb-home')
        originalWrapperScript = System.getProperty('wrapper.script')

        // Find the built jar (created by 'dependsOn jar' in build.gradle)
        def jarPath = System.getProperty('puliziaCassaforteJar')
        if (!jarPath) {
            throw new IllegalStateException('System property puliziaCassaforteJar not set. ' +
                'Ensure integrationTest task sets this property pointing to build/libs/pulizia-temporanei.jar')
        }
        jarFile = new File(jarPath)
        log.info("Using jar: ${jarFile.absolutePath}")
    }

    def setup() {
        fixture = new PuliziaTemporaneiFixture(
            dbbBuildDirPath.toFile(),
            dbbHomeDirPath.toFile(),
            appDirPath.toFile(),
            zosSimDirPath.toFile()
        )
        fixture.writeConfig('macos')

        // Deploy shell script to dbbBuildDir (entry point for test execution)
        def shellScriptResource = getClass().getResource('/run-pulizia-temporanei.sh')
        if (!shellScriptResource) {
            throw new IllegalStateException('run-pulizia-temporanei.sh not found in integrationTest resources')
        }
        def shellScriptFile = new File(shellScriptResource.toURI())
        fixture.deployShellScriptToDbbBuild(shellScriptFile)
        deployedShellScript = new File(dbbBuildDirPath.toFile(), 'groovy/pulizia-temporanei/run-pulizia-temporanei.sh')

        // Deploy fat source and fe entry point to working directory
        def fatSourceFile = new File(System.getProperty('fatSourceFile'))
        if (!fatSourceFile.exists()) {
            throw new IllegalStateException("Fat source file not found: ${fatSourceFile.absolutePath}")
        }

        def feEntryPointResource = getClass().getResource('/RunPuliziaTemporanei.groovy')
        if (!feEntryPointResource) {
            throw new IllegalStateException('fe/RunPuliziaTemporanei.groovy not found in integrationTest resources')
        }
        def feEntryPointFile = new File(feEntryPointResource.toURI())

        // Copy fat source and entry point to app directory (working directory for shell script)
        def appDir = appDirPath.toFile()
        new File(appDir, 'FullPuliziaTemporanei.groovy').text = fatSourceFile.text
        new File(appDir, 'RunPuliziaTemporanei.groovy').text = feEntryPointFile.text

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
        def cfgFile = new File(appDirPath.toFile(), 'PuliziaTemporanei.properties').absolutePath
        CliRunner.runShellScript(
            appDirPath.toFile(),
            deployedShellScript,
            [:],
            dsnPatternArgs + ['-c', cfgFile]
        )
    }

    private CliResult runPuliziaTemporaneiWithArgs(List<String> args) {
        CliRunner.runShellScript(
            appDirPath.toFile(),
            deployedShellScript,
            [:],
            args
        )
    }
}
