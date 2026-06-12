@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
//This groovy script take three argumments from command line:
// 1. The path a file containing <action>;<file to process> lines
// 2. The environment (a string that represents the environment, e.g., "ATO")
// 3. The build group (a string that represents the build group, e.g., "ATO")
// This groovy script instantiate PuliziaCassaforte class and call the method run passing the three arguments.

if (args.length != 3) {
    println "Usage: groovyz RunPuliziaCassaforte.groovy  <list path> <environment> <build group>"
    System.exit(1)
}

String fileList = args[0]
String environment = args[1]
String buildGroup = args[2]

File fileListFile = new File(fileList)
if (!fileListFile.exists()) {
    println "List does not exist: ${fileList}"
    System.exit(1)
}

// Read env var DBB_CONF
String dbbConf = System.getenv("DBB_CONF")
if (dbbConf == null) {
    println "Environment variable DBB_CONF is not set."
    System.exit(1)
}
// Read PuliziaCassaforte property file from current directory
Properties cfgProps = new Properties()
try {
    cfgProps.load(new FileInputStream("PuliziaCassaforte.properties"))
} catch (IOException e) {
    println "Could not read PuliziaCassaforte.properties: ${e.message}"
    System.exit(1)
}

def pcloaded = loadScript(new File("FullPuliziaCassaforte.groovy"))
def puliziaCassaforte = pcloaded.createPuliziaCassaforteImpl()
int errors = puliziaCassaforte.run(fileList, environment, buildGroup, cfgProps)
println "PuliziaCassaforte completed with ${errors} errors."