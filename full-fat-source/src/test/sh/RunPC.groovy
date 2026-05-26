@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript

String processFileList = 'lista.txt'
String environment = 'ATO'
String buildGroup = 'ATO'

File fileListFile = new File(processFileList)
if (!fileListFile.exists()) {
    println "List does not exist: ${processFileList}"
    System.exit(1)
}

// Read env var DBB_CONF
String dbbConf = System.getenv("DBB_CONF")
if (dbbConf == null) {
    println "Environment variable DBB_CONF is not set."
    System.exit(1)
}
// Overwritable variables with default values
String fileOpsType = 'local'
String buildMapClientType = 'json'
String rulesPath = 'resources/rules.csv'
String stageMapPath = 'resources/stagemap.csv'
// 
String uxBasedir = '/u/u0g9700/ux'
String hlq = null
String userId = null
String pwFilePath = null
String db2ConfigPath = null
String buildMapPath = 'resources/buildmap.json'

// write the valued variables as properties file in file PuliziaCassaforte.properties
Properties properties = new Properties()
properties.setProperty("fileOpsType", fileOpsType)
properties.setProperty("buildMapClientType", buildMapClientType)
properties.setProperty("rulesPath", rulesPath)
properties.setProperty("stageMapPath", stageMapPath)
properties.setProperty("uxBasedir", uxBasedir)
properties.setProperty("buildMapPath", buildMapPath)
properties.store(new FileOutputStream("RunPuliziaCassaforteJsonLocal.properties"), null)

def gcl = new GroovyClassLoader()
gcl.parseClass(new File("pulizia_cassaforte_full.groovy"))

def impl = gcl.loadClass('PuliziaCassaforteImpl').newInstance()
int errors = impl.run(processFileList, environment, buildGroup, "RunPuliziaCassaforteJsonLocal.properties")
println "Success: ${impl.class.name}"
