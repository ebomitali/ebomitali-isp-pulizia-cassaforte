import com.ibm.dbb.metadata.*
import com.ibm.dbb.build.*

// connect to datastore
Properties properties = new Properties()
// use the DBB_CONF env var to get parent directory for db2Connection.conf; fail if not found
String confDir = System.getenv('DBB_CONF') ?: "${System.getenv('DBB_HOME')}/conf"
File configFile = new File(confDir, 'db2Connection.conf')
if (!configFile.exists()) {
    System.err.println "ERROR: DB2 conf not found: ${configFile.canonicalPath}"
    System.exit(1)
}
configFile.withInputStream { stream ->
    properties.load(stream)
}
String id = "GADBB01"
File pwFile = new File("${confDir}/DB01PSW.txt")
MetadataStore metadataStore = MetadataStoreFactory.createDb2MetadataStore(id, pwFile, properties)

// get build map for logical file as_a_01_ato_test/src/COBOL/BATCH/S2NN/AS14000.SCB2B on build group as_a_01_ato_test-master
BuildGroup buildGroup = metadataStore.getBuildGroup("ATO");
BuildMap buildMap = buildGroup.getBuildMap("ATO/yu_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YU7OMPAK.SJCLINP");
println "Build Map:\n" + buildMap.toString()
