import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import java.nio.file.*

class LocalFileOpsSpec extends Specification {

    @TempDir
    Path tempDir

    def "copy creates PDS member; delete removes it"() {
        given:
        def ops = new LocalFileOps(tempDir.toString())
        def src = tempDir.resolve('TEST.DS.SRC/MEMBSRC')
        Files.createDirectories(src.parent)
        Files.writeString(src, 'content')

        expect:
        !ops.exists('//TEST.DS(MEMBER1)')

        when:
        ops.copy('//TEST.DS.SRC(MEMBSRC)', '//TEST.DS(MEMBER1)')

        then:
        ops.exists('//TEST.DS(MEMBER1)')

        when:
        ops.delete('//TEST.DS(MEMBER1)')

        then:
        !ops.exists('//TEST.DS(MEMBER1)')
    }

    def "list returns PDS member names"() {
        given:
        def ops = new LocalFileOps(tempDir.toString())
        def member = tempDir.resolve('TEST.DS.SRC/MEMBSRC')
        Files.createDirectories(member.parent)
        Files.writeString(member, 'content')

        expect:
        ops.list('//TEST.DS.SRC').contains('MEMBSRC')
    }

    def "exists works for USS file path passthrough"() {
        given:
        def ops = new LocalFileOps(tempDir.toString())
        def ussFile = tempDir.resolve('uss-test.txt')
        Files.writeString(ussFile, 'x')

        expect:
        ops.exists(ussFile.toString())
        !ops.exists(tempDir.resolve('nonexistent.txt').toString())
    }
}
