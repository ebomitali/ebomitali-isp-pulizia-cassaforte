//This groovy script take three argumments from command line:
// 1. The path a file containing <action>;<file to process> lines
// 2. The environment (a string that represents the environment, e.g., "ATO")
// 3. The build group (a string that represents the build group, e.g., "ATO")
// This groovy script instantiate PuliziaCassaforte class and call the method run passing the three arguments.

if (args.length != 3) {
    println "Usage: groovy PuliziaCassaforte.groovy  <list path> <environment> <build group>"
    System.exit(1)
}

String fileListPath = args[0]
String environment = args[1]
String buildGroup = args[2]

File fileListFile = new File(fileListPath)
if (!fileListFile.exists()) {
    println "List does not exist: ${fileListPath}"
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
String rulesPath = '../resource/fixture/rules.csv'
String stageMapPath = '../resource/fixture/stage-map.csv'
// 
String uxBasedir = '/u/u0g9700/ux'
String hlq = null
String userId = null
String pwFilePath = null
String db2ConfigPath = null
String buildMapPath = '../resource/fixture/build-map.json'

// write the valued variables as properties file in file PuliziaCassaforte.properties
Properties properties = new Properties()
properties.setProperty("fileOpsType", fileOpsType)
properties.setProperty("buildMapClientType", buildMapClientType)
properties.setProperty("rulesPath", rulesPath)
properties.setProperty("stageMapPath", stageMapPath)
properties.setProperty("uxBasedir", uxBasedir)
properties.setProperty("buildMapPath", buildMapPath)
properties.store(new FileOutputStream("RunPuliziaCassaforteJsonLocal.properties"), null)

PuliziaCassaforteImpl impl   = new PuliziaCassaforteImpl()
int errors = impl.run(fileListFile, environment, buildGroup, "RunPuliziaCassaforteJsonLocal.properties")
println "PuliziaCassaforte completed with ${errors} errors."