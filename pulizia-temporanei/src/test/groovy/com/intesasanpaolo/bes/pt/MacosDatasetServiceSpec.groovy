package com.intesasanpaolo.bes.pt
import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification

class MacosDatasetServiceSpec extends Specification {
    @TempDir
    File baseDir

    MacosDatasetService service

    void setup() {
        service = new MacosDatasetService(baseDir.absolutePath)
    }

    void "exists returns true for existing dataset directory"() {
        given:
        def datasetDir = new File(baseDir, 'MY/TEMP/ABC')
        datasetDir.mkdirs()

        expect:
        service.exists('MY.TEMP.ABC')
    }

    void "exists returns false for non-existing dataset"() {
        expect:
        !service.exists('MY.TEMP.NOTHERE')
    }

    void "listDatasets returns all matching datasets for wildcard pattern"() {
        given:
        def paths = [
            'MY/TEMP/ABC',
            'MY/TEMP/XYZ',
            'MY/TEMP/DEF',
            'MY/PERMANENT/ABC'
        ]
        paths.each { new File(baseDir, it).mkdirs() }

        when:
        def matching = service.listDatasets('MY.TEMP.*')

        then:
        matching.size() == 3
        matching.contains('MY.TEMP.ABC')
        matching.contains('MY.TEMP.XYZ')
        matching.contains('MY.TEMP.DEF')
        !matching.contains('MY.PERMANENT.ABC')
    }

    void "listDatasets handles single-char wildcard %"() {
        given:
        def paths = [
            'MY/TEMP/A',
            'MY/TEMP/B',
            'MY/TEMP/AB'
        ]
        paths.each { new File(baseDir, it).mkdirs() }

        when:
        def matching = service.listDatasets('MY.TEMP.%')

        then:
        matching.size() == 2
        matching.contains('MY.TEMP.A')
        matching.contains('MY.TEMP.B')
        !matching.contains('MY.TEMP.AB')
    }

    void "listDatasets returns exact dataset when no wildcard"() {
        given:
        new File(baseDir, 'MY/TEMP/ABC').mkdirs()

        when:
        def matching = service.listDatasets('MY.TEMP.ABC')

        then:
        matching.size() == 1
        matching[0] == 'MY.TEMP.ABC'
    }

    void "listDatasets returns empty when no matches"() {
        given:
        new File(baseDir, 'MY/TEMP/ABC').mkdirs()

        when:
        def matching = service.listDatasets('MY.PERM.*')

        then:
        matching.isEmpty()
    }

    void "deleteDataset removes dataset directory and contents"() {
        given:
        def datasetDir = new File(baseDir, 'MY/TEMP/ABC')
        datasetDir.mkdirs()
        new File(datasetDir, 'file1.txt').createNewFile()
        new File(datasetDir, 'file2.txt').createNewFile()

        expect:
        datasetDir.exists()

        when:
        service.deleteDataset('MY.TEMP.ABC')

        then:
        !datasetDir.exists()
    }

    void "deleteDataset handles non-existing dataset gracefully"() {
        when:
        service.deleteDataset('MY.TEMP.NOTHERE')

        then:
        noExceptionThrown()
    }

    void "delete matching datasets end-to-end test"() {
        given:
        def paths = [
            'MY/TEMP/ABC',
            'MY/TEMP/XYZ',
            'MY/PERM/DATA'
        ]
        paths.each { new File(baseDir, it).mkdirs() }

        when:
        def matching = service.listDatasets('MY.TEMP.*')
        matching.each { service.deleteDataset(it) }

        then:
        !new File(baseDir, 'MY/TEMP/ABC').exists()
        !new File(baseDir, 'MY/TEMP/XYZ').exists()
        new File(baseDir, 'MY/PERM/DATA').exists()
    }
}
