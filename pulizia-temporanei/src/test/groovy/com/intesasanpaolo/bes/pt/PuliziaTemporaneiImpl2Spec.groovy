package com.intesasanpaolo.bes.pt

import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification

/**
 * Integration test for pulizia-temporanei module.
 * Tests the full implementation using the compiled JAR classes.
 */
class PuliziaTemporaneiImpl2Spec extends Specification {

    void "jar contains all necessary classes"() {
        when:
        // Load classes from the JAR to verify they're present
        def datasetServiceClass = Class.forName('com.intesasanpaolo.bes.pt.DatasetService')
        def macosServiceClass = Class.forName('com.intesasanpaolo.bes.pt.MacosDatasetService')
        def deleteLogicClass = Class.forName('com.intesasanpaolo.bes.pt.DeleteTemporaneiLogic')
        def implClass = Class.forName('com.intesasanpaolo.bes.pt.PuliziaTemporaneiImpl')
        def configClass = Class.forName('com.intesasanpaolo.bes.pt.PuliziaTemporaneiConfig')
        def patternMatcherClass = Class.forName('com.intesasanpaolo.bes.pt.PatternMatcher')

        then:
        datasetServiceClass != null
        macosServiceClass != null
        deleteLogicClass != null
        implClass != null
        configClass != null
        patternMatcherClass != null
    }

    @TempDir
    File simDsnDir

    void "execute via impl to delete matching datasets"() {
        given:
        // Create simulated DSN directory structure
        def paths = [
            'MY/TEMP/ABC',
            'MY/TEMP/XYZ',
            'MY/PERM/DATA'
        ]
        paths.each { new File(simDsnDir, it).mkdirs() }

        def cfg = new Properties()
        cfg.setProperty('fileOpsType', 'macos')
        cfg.setProperty('uxBasedir', simDsnDir.absolutePath)

        // Verify setup
        assert new File(simDsnDir, 'MY/TEMP/ABC').exists()
        assert new File(simDsnDir, 'MY/TEMP/XYZ').exists()
        assert new File(simDsnDir, 'MY/PERM/DATA').exists()

        when:
        // Load and instantiate from JAR classes
        def implClass = Class.forName('com.intesasanpaolo.bes.pt.PuliziaTemporaneiImpl')
        def impl = implClass.getDeclaredConstructor().newInstance()
        int count = impl.doPuliziaTemporanei('MY.TEMP.*', cfg)

        then:
        count == 2

        // Verify datasets were deleted
        !new File(simDsnDir, 'MY/TEMP/ABC').exists()
        !new File(simDsnDir, 'MY/TEMP/XYZ').exists()
        new File(simDsnDir, 'MY/PERM/DATA').exists()
    }

    void "execute via impl with pattern matching no datasets"() {
        given:
        def paths = [
            'MY/PERM/DATA'
        ]
        paths.each { new File(simDsnDir, it).mkdirs() }

        def cfg = new Properties()
        cfg.setProperty('fileOpsType', 'macos')
        cfg.setProperty('uxBasedir', simDsnDir.absolutePath)

        when:
        def implClass = Class.forName('com.intesasanpaolo.bes.pt.PuliziaTemporaneiImpl')
        def impl = implClass.getDeclaredConstructor().newInstance()
        int count = impl.doPuliziaTemporanei('MY.TEMP.*', cfg)

        then:
        count == 0
        new File(simDsnDir, 'MY/PERM/DATA').exists()
    }

    void "execute via impl with exact dsn pattern"() {
        given:
        def paths = [
            'MY/TEMP/ABC',
            'MY/TEMP/XYZ'
        ]
        paths.each { new File(simDsnDir, it).mkdirs() }

        def cfg = new Properties()
        cfg.setProperty('fileOpsType', 'macos')
        cfg.setProperty('uxBasedir', simDsnDir.absolutePath)

        when:
        def implClass = Class.forName('com.intesasanpaolo.bes.pt.PuliziaTemporaneiImpl')
        def impl = implClass.getDeclaredConstructor().newInstance()
        int count = impl.doPuliziaTemporanei('MY.TEMP.ABC', cfg)

        then:
        count == 1
        !new File(simDsnDir, 'MY/TEMP/ABC').exists()
        new File(simDsnDir, 'MY/TEMP/XYZ').exists()
    }

    void "execute via impl with wildcard percent"() {
        given:
        def paths = [
            'MY/TEMP/A',
            'MY/TEMP/B',
            'MY/TEMP/AB',
            'NOT/MATCH/DSN'
        ]
        paths.each { new File(simDsnDir, it).mkdirs() }

        def cfg = new Properties()
        cfg.setProperty('fileOpsType', 'macos')
        cfg.setProperty('uxBasedir', simDsnDir.absolutePath)

        when:
        def implClass = Class.forName('com.intesasanpaolo.bes.pt.PuliziaTemporaneiImpl')
        def impl = implClass.getDeclaredConstructor().newInstance()
        int count = impl.doPuliziaTemporanei('MY.TEMP.%', cfg)

        then:
        count == 2
        !new File(simDsnDir, 'MY/TEMP/A').exists()
        !new File(simDsnDir, 'MY/TEMP/B').exists()
        new File(simDsnDir, 'MY/TEMP/AB').exists()
    }

    void "execute via impl with wildcard asterisk"() {
        given:
        def paths = [
            'MY/TEMP/A',
            'MY/TEMP/B',
            'MY/TEMP/AB',
            'NOT/MATCH/DSN'
        ]
        paths.each { new File(simDsnDir, it).mkdirs() }

        def cfg = new Properties()
        cfg.setProperty('fileOpsType', 'macos')
        cfg.setProperty('uxBasedir', simDsnDir.absolutePath)

        when:
        def implClass = Class.forName('com.intesasanpaolo.bes.pt.PuliziaTemporaneiImpl')
        def impl = implClass.getDeclaredConstructor().newInstance()
        int count = impl.doPuliziaTemporanei('MY.TEMP.*', cfg)

        then:
        count == 3
        !new File(simDsnDir, 'MY/TEMP/A').exists()
        !new File(simDsnDir, 'MY/TEMP/B').exists()
        !new File(simDsnDir, 'MY/TEMP/AB').exists()
    }

    void "execute via impl with cliHlq and cliUid builds DSN pattern"() {
        given:
        def paths = [
            'MYHLQ/TWX_0000/UID123/QUAL1',
            'MYHLQ/TWX_0000/UID123/QUAL2',
            'OTHERHLQ/TWX_0000/UID456/QUAL1'
        ]
        paths.each { new File(simDsnDir, it).mkdirs() }

        def cfg = new Properties()
        cfg.setProperty('fileOpsType', 'macos')
        cfg.setProperty('uxBasedir', simDsnDir.absolutePath)

        when:
        def implClass = Class.forName('com.intesasanpaolo.bes.pt.PuliziaTemporaneiImpl')
        def impl = implClass.getDeclaredConstructor().newInstance()
        int count = impl.doPuliziaTemporanei('MYHLQ', 'UID123', cfg)

        then:
        count == 2
        !new File(simDsnDir, 'MYHLQ/TWX_0000/UID123/QUAL1').exists()
        !new File(simDsnDir, 'MYHLQ/TWX_0000/UID123/QUAL2').exists()
        new File(simDsnDir, 'OTHERHLQ/TWX_0000/UID456/QUAL1').exists()
    }
}
