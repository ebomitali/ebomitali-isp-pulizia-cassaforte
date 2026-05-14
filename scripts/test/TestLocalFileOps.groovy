// scripts/test/TestLocalFileOps.groovy
import java.nio.file.*

def base = '/tmp/zos-sim-test-' + System.currentTimeMillis()
def ops  = new LocalFileOps(base)

// Seed source file
def src = Paths.get(base, 'TEST.DS.SRC', 'MEMBSRC')
Files.createDirectories(src.parent)
Files.writeString(src, 'content')

assert !ops.exists('//TEST.DS(MEMBER1)') : "should not exist before copy"

ops.copy('//TEST.DS.SRC(MEMBSRC)', '//TEST.DS(MEMBER1)')
assert ops.exists('//TEST.DS(MEMBER1)') : "should exist after copy"

ops.delete('//TEST.DS(MEMBER1)')
assert !ops.exists('//TEST.DS(MEMBER1)') : "should not exist after delete"

def members = ops.list('//TEST.DS.SRC')
assert 'MEMBSRC' in members : "list should contain MEMBSRC"

// USS path passthrough
def ussFile = Paths.get(base, 'uss-test.txt')
Files.writeString(ussFile, 'x')
assert ops.exists(ussFile.toString())

// Cleanup
new File(base).deleteDir()

println "TestLocalFileOps: PASS"
