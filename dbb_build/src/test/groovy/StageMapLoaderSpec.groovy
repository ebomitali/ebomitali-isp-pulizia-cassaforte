import spock.lang.Specification

class StageMapLoaderSpec extends Specification {

    def loader = new StageMapLoader()

    def "load returns map from valid stagemap.csv"() {
        given:
        def path = new File(getClass().getResource('/fixtures/stagemap.csv').toURI())

        when:
        def map = loader.load(path)

        then:
        map['01|ATO'] == 'X2A'
        map['01|ST']  == 'XAD'
        map['03|ST']  == 'YAD'
        map['01|PR']  == 'XAE'
    }

    def "load strips surrounding quotes and whitespace from keys and values"() {
        given:
        def path = new File(getClass().getResource('/fixtures/stagemap.csv').toURI())

        when:
        def map = loader.load(path)

        then:
        map.keySet().every { !it.contains('"') && !it.startsWith(' ') }
        map.values().every { !it.contains('"') && !it.startsWith(' ') }
    }

    def "load skips blank lines"() {
        when:
        def tmp = File.createTempFile('stagemap', '.csv')
        tmp.text = '\n\n"01|ATO";"X2A"\n\n"02|ST";"YAD"\n\n'

        then:
        loader.load(tmp).size() == 2

        cleanup:
        tmp.delete()
    }

    def "load throws on malformed row missing semicolon"() {
        given:
        def tmp = File.createTempFile('stagemap', '.csv')
        tmp.text = '"01|ATO" "X2A"\n'

        when:
        loader.load(tmp)

        then:
        thrown(IllegalArgumentException)

        cleanup:
        tmp.delete()
    }
}
