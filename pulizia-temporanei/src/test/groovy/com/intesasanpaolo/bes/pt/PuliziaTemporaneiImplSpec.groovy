package com.intesasanpaolo.bes.pt
import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification

class PuliziaTemporaneiImplSpec extends Specification {
    @TempDir
    File baseDir

    @TempDir
    File configDir

    PuliziaTemporaneiImpl impl

    void setup() {
        impl = new PuliziaTemporaneiImpl()
    }

    void "doPuliziaTemporanei with macos config deletes matching datasets"() {
        given:
        def datasetDir1 = new File(baseDir, 'MY/TEMP/ABC')
        def datasetDir2 = new File(baseDir, 'MY/TEMP/XYZ')
        [datasetDir1, datasetDir2].each { it.mkdirs() }

        def props = new Properties()
        props.setProperty('fileOpsType', 'macos')
        props.setProperty('uxBasedir', baseDir.absolutePath)

        when:
        int count = impl.doPuliziaTemporanei('MY.TEMP.*', props)

        then:
        count == 2
        !datasetDir1.exists()
        !datasetDir2.exists()
    }

    void "doPuliziaTemporanei with properties map deletes matching datasets"() {
        given:
        def datasetDir = new File(baseDir, 'MY/TEMP/ABC')
        datasetDir.mkdirs()

        def props = new Properties()
        props.setProperty('fileOpsType', 'macos')
        props.setProperty('uxBasedir', baseDir.absolutePath)

        when:
        int count = impl.doPuliziaTemporanei('MY.TEMP.*', props)

        then:
        count == 1
        !datasetDir.exists()
    }

    void "doPuliziaTemporanei throws exception for null pattern"() {
        given:
        def props = new Properties()
        props.setProperty('fileOpsType', 'macos')
        props.setProperty('uxBasedir', baseDir.absolutePath)

        when:
        impl.doPuliziaTemporanei(null, props)

        then:
        thrown(IllegalArgumentException)
    }

    void "doPuliziaTemporanei throws exception for invalid fileOpsType"() {
        given:
        def props = new Properties()
        props.setProperty('fileOpsType', 'invalid')
        props.setProperty('uxBasedir', baseDir.absolutePath)

        when:
        impl.doPuliziaTemporanei('MY.TEMP.*', props)

        then:
        thrown(IllegalArgumentException)
    }

    void "doPuliziaTemporanei throws exception when uxBasedir missing for uss type"() {
        given:
        def props = new Properties()
        props.setProperty('fileOpsType', 'uss')
        // uxBasedir not set

        when:
        impl.doPuliziaTemporanei('MY.TEMP.*', props)

        then:
        thrown(IllegalArgumentException)
    }

    void "doPuliziaTemporanei defaults to zos fileOpsType"() {
        given:
        def props = new Properties()
        // fileOpsType not set, should default to 'zos'

        when:
        impl.doPuliziaTemporanei('MY.TEMP.*', props)

        then:
        thrown(UnsupportedOperationException)  // JzosDatasetService throws UnsupportedOperationException
    }

    void "doPuliziaTemporanei with uss config deletes matching datasets"() {
        given:
        def datasetDir = new File(baseDir, 'MY/TEMP/ABC')
        datasetDir.mkdirs()

        def props = new Properties()
        props.setProperty('fileOpsType', 'uss')
        props.setProperty('uxBasedir', baseDir.absolutePath)

        when:
        int count = impl.doPuliziaTemporanei('MY.TEMP.*', props)

        then:
        count == 1
        !datasetDir.exists()
    }

    void "doPuliziaTemporanei with cliHlq and cliUid constructs DSN pattern and deletes"() {
        given:
        def datasetDir1 = new File(baseDir, 'MYHLQ/TWX_0000/UID123/QUAL1')
        def datasetDir2 = new File(baseDir, 'MYHLQ/TWX_0000/UID123/QUAL2')
        def datasetDir3 = new File(baseDir, 'OTHERHLQ/TWX_0000/UID456/QUAL1')
        [datasetDir1, datasetDir2, datasetDir3].each { it.mkdirs() }

        def props = new Properties()
        props.setProperty('fileOpsType', 'macos')
        props.setProperty('uxBasedir', baseDir.absolutePath)

        when:
        int count = impl.doPuliziaTemporanei('MYHLQ', 'UID123', props)

        then:
        count == 2
        !datasetDir1.exists()
        !datasetDir2.exists()
        datasetDir3.exists()
    }

    void "doPuliziaTemporanei with cliHlq and cliUid trims whitespace"() {
        given:
        def datasetDir = new File(baseDir, 'MYHLQ/TWX_0000/UID123/QUAL1')
        datasetDir.mkdirs()

        def props = new Properties()
        props.setProperty('fileOpsType', 'macos')
        props.setProperty('uxBasedir', baseDir.absolutePath)

        when:
        int count = impl.doPuliziaTemporanei('  MYHLQ  ', '  UID123  ', props)

        then:
        count == 1
        !datasetDir.exists()
    }
}
