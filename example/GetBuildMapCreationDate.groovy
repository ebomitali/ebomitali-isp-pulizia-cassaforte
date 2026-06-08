import com.ibm.dbb.metadata.*
import com.ibm.dbb.build.*
import java.text.SimpleDateFormat;

// connect to datastore
Properties properties = new Properties()
File configFile = new File("/prodotti/DEE/test/conf/db2Connection.conf")
configFile.withInputStream { stream ->
    properties.load(stream)
}
String id = "GADBB01"
File pwFile = new File("/prodotti/DEE/test/conf/DB01PSW.txt")
MetadataStore metadataStore = MetadataStoreFactory.createDb2MetadataStore(id, pwFile, properties)

// get build map for logical file as_a_01_ato_test/src/COBOL/BATCH/S2NN/AS14000.SCB2B on build group as_a_01_ato_test-master
BuildGroup buildGroup = metadataStore.getBuildGroup("as_a_01_ato_test-master");
BuildMap buildMap = buildGroup.getBuildMap("as_a_01_ato_test/src/COBOL/BATCH/S2NN/AS14000.SCB2B");

Date bmCreationDate = buildMap.getCreated();
String bmCreation = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss.SSSSS Z").format(bmCreationDate)
println "Build Map Creation: " + bmCreation