package com.intesasanpaolo.bes.pt
import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification

class DeleteTemporaneiLogicSpec extends Specification {
    @TempDir
    File baseDir

    DeleteTemporaneiLogic logic
    MacosDatasetService datasetOps

    void setup() {
        datasetOps = new MacosDatasetService(baseDir.absolutePath)
        logic = new DeleteTemporaneiLogic(ops: datasetOps)
    }

    void "execute deletes all matching datasets"() {
        given:
        def paths = [
            'MY/TEMP/ABC',
            'MY/TEMP/XYZ',
            'MY/PERM/DATA'
        ]
        paths.each { new File(baseDir, it).mkdirs() }

        when:
        int count = logic.execute('MY.TEMP.*')

        then:
        count == 2
        !new File(baseDir, 'MY/TEMP/ABC').exists()
        !new File(baseDir, 'MY/TEMP/XYZ').exists()
        new File(baseDir, 'MY/PERM/DATA').exists()
    }

    void "execute returns 0 when no datasets match"() {
        given:
        new File(baseDir, 'MY/TEMP/ABC').mkdirs()

        when:
        int count = logic.execute('MY.PERM.*')

        then:
        count == 0
        new File(baseDir, 'MY/TEMP/ABC').exists()
    }

    void "execute returns count of successfully deleted datasets"() {
        given:
        def paths = [
            'MY/TEMP/A',
            'MY/TEMP/B',
            'MY/TEMP/C'
        ]
        paths.each { new File(baseDir, it).mkdirs() }

        when:
        int count = logic.execute('MY.TEMP.*')

        then:
        count == 3
    }

    void "execute throws exception for null pattern"() {
        when:
        logic.execute(null)

        then:
        thrown(IllegalArgumentException)
    }

    void "execute throws exception for empty pattern"() {
        when:
        logic.execute('   ')

        then:
        thrown(IllegalArgumentException)
    }

    void "execute handles pattern with leading/trailing whitespace"() {
        given:
        new File(baseDir, 'MY/TEMP/ABC').mkdirs()

        when:
        int count = logic.execute('  MY.TEMP.*  ')

        then:
        count == 1
        !new File(baseDir, 'MY/TEMP/ABC').exists()
    }

    void "execute deletes exact dataset without wildcards"() {
        given:
        def paths = [
            'MY/TEMP/ABC',
            'MY/TEMP/XYZ'
        ]
        paths.each { new File(baseDir, it).mkdirs() }

        when:
        int count = logic.execute('MY.TEMP.ABC')

        then:
        count == 1
        !new File(baseDir, 'MY/TEMP/ABC').exists()
        new File(baseDir, 'MY/TEMP/XYZ').exists()
    }
}
