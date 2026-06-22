import com.ibm.jzos.ZFile
import com.ibm.jzos.ZFileException

String testPds = System.getProperty('testPds')
String testSeq = System.getProperty('testSeq')

println("Creating test members in $testPds...")

try {
    // Create MEMBER1 with sample data
    ZFile m1 = new ZFile("//'$testPds(MEMBER1)'", 'wb,type=record')
    m1.write("member 1 content\n".getBytes())
    m1.close()
    println("Created MEMBER1")
} catch (ZFileException e) {
    println("Failed to create MEMBER1: ${e.message}")
    System.exit(1)
}

try {
    // Create MEMBER2 with sample data
    ZFile m2 = new ZFile("//'$testPds(MEMBER2)'", 'wb,type=record')
    m2.write("member 2 content\n".getBytes())
    m2.close()
    println("Created MEMBER2")
} catch (ZFileException e) {
    println("Failed to create MEMBER2: ${e.message}")
    System.exit(1)
}

println("Setup complete - members created successfully")
