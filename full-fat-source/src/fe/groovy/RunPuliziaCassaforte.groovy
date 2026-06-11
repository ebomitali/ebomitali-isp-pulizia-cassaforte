//This groovy script is intended to be called from Jenkins in a USS context,
// managing PuliziaCassaforte z/OS library and accessing Metadatastore
// if rules require it. It can be called from command line as well, but it is not intended 
// to be used that way and it will not work without a proper configuration and context 
// (e.g., DBB environment variables, access to Metadatastore if needed by rules, etc.). 
// It takes three argumments from command line:
// 1. The path a file containing <action>;<file to process> lines
// 2. The environment (a string that represents the environment, e.g., "ATO")
// 3. The build group (a string that represents the build group, e.g., "ATO")
// This groovy script instantiate PuliziaCassaforteImpl class and call the method doPuliziaCassaforte
// passing the three arguments.

if (args.length != 3) {
    println "Usage: groovyz RunPuliziaCassaforte.groovy  <sources list path> <environment> <build group>"
    System.exit(1)
}

String sources = args[0]
String environment = args[1]
String buildGroup  = args[2]

File sourcesListFile = new File(sources)
if (!sourcesListFile.exists()) {
    println "Sources list file does not exist: ${sources}"
    System.exit(1)
}

// // Read env var DBB_CONF
// String dbbConf = System.getenv("DBB_CONF")
// if (dbbConf == null) {
//     println "Environment variable DBB_CONF is not set."
//     System.exit(1)
// }
// String dbbBuild = System.getenv("DBB_BUILD")
// if (dbbBuild == null) {
//     println "Environment variable DBB_BUILD is not set."
//     System.exit(1)
// }
// String dbbHome = System.getenv("DBB_HOME")
// if (dbbHome == null) {
//     println "Environment variable DBB_HOME is not set."
//     System.exit(1)
// }

// Read PuliziaCassaforte property file from current directory
Properties cfgProps = new Properties()
try {
    cfgProps.load(new FileInputStream("PuliziaCassaforte.properties"))
} catch (IOException e) {
    println "Could not read PuliziaCassaforte.properties: ${e.message}"
    System.exit(1)
}

// if buildMapClientType is not set in properties, default to 'db2'
if (!cfgProps.containsKey('buildMapClientType')) {
    cfgProps.setProperty('buildMapClientType', 'db2')
}
// if fileOpsType is not set in properties, default to 'zos'
if (!cfgProps.containsKey('fileOpsType')) {
    cfgProps.setProperty('fileOpsType', 'zos')
}

// FullPuliziaCassaforte.groovy is copied alongside this script by mrunpct2.sh
def fatSourceFile = new File("FullPuliziaCassaforte.groovy")
if (!fatSourceFile.exists()) {
    println "FullPuliziaCassaforte.groovy not found at: ${fatSourceFile.canonicalPath}"
    System.exit(1)
}

println "Starting PuliziaCassaforte with sources list: ${sources}, environment: ${environment}, build group: ${buildGroup}"
def gcl = new GroovyClassLoader(this.class.classLoader)
Class scriptClass = gcl.parseClass(fatSourceFile)
def scriptInstance = scriptClass.getDeclaredConstructor().newInstance()
def puliziaCassaforte = scriptInstance.createPuliziaCassaforteImpl()

int errors = puliziaCassaforte.doPuliziaCassaforte(sourcesListFile, environment, buildGroup, cfgProps)
println "PuliziaCassaforte completed with ${errors} errors."
if (errors > 0) System.exit(1)
