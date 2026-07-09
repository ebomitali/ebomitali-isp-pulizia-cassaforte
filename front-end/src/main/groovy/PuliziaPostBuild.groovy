@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript
// This groovy script is intended to be called from DBB within a step of type task
// so it should inherit from TaskScript to access DBB interface
// Parameters are given as context/config variables
// source is single source file, config.get("FILE_PATH")
// environemnt is context.get("BUILD_ENV")
// build group is context.get("BUILD_GROUP")
// if no simulationEnv is set in config or it is empty, default PuliziaCassaforteConfig has fileOpsType to 'zos' and buildMapClientType to 'db2'
// simulationEnv is set to macos set fileOpsType to 'zos' and buildMapClientType to 'json'
// simulationEnv is set to ussux set fileOpsType to 'uss', buildMapClientType to 'db2'
// simulationEnv is set to usszos, set fileOpsType to 'zos', buildMapClientType to 'db2', hlq is set to user

// BaseScript transform with com.ibm.dbb.groovy.TaskScript allows this script to access 
// the DBB interface and use context/config,log variables.
// config → a com.ibm.dbb.task.TaskVariables object
// context → a com.ibm.dbb.task.BuildContext object
// log → an SLF4j logger

String sourceFilePath = config.get("FILE_PATH")
String environment    = context.get("BUILD_ENV")
String simulationEnv  = config.get("simulationEnv") ?: ''

// BUILD_GROUP may already be a resolved com.ibm.dbb.metadata.BuildGroup (the normal case in a
// running task context) or a plain group-name String (e.g. simpler test setups). Either way,
// derive the name for logging/rule matching, and keep the object itself (when present) so it
// can be reused directly instead of triggering a fresh DB2 connection for buildMapClientType=db2.
def buildGroupRaw          = context.get("BUILD_GROUP")
boolean buildGroupIsObject = buildGroupRaw != null && !(buildGroupRaw instanceof String)
String  buildGroup         = buildGroupIsObject ? buildGroupRaw.getName() : buildGroupRaw
Object  resolvedBuildGroup = buildGroupIsObject ? buildGroupRaw : null

File sourceFile = new File(sourceFilePath)
if (!sourceFile.exists()) {
    println "Source file does not exist: ${sourceFilePath}"
    System.exit(1)
}

// DBB_CONF/DBB_BUILD/DBB_HOME/APP_DIR are build-scoped values set by the zBuilder on the
// running BuildContext, not OS environment variables.
String dbbConf = context.get("DBB_CONF")
if (dbbConf == null) {
    println "Context variable DBB_CONF is not set."
    System.exit(1)
}
String dbbBuild = context.get("DBB_BUILD")
if (dbbBuild == null) {
    println "Context variable DBB_BUILD is not set."
    System.exit(1)
}
String dbbHome = context.get("DBB_HOME")
if (dbbHome == null) {
    println "Context variable DBB_HOME is not set."
    System.exit(1)
}
String appDir = context.get("APP_DIR")
if (appDir == null) {
    println "Context variable APP_DIR is not set."
    System.exit(1)
}

// Read PuliziaCassaforte property file from the script's app directory
Properties cfgProps = new Properties()
try {
    cfgProps.load(new FileInputStream(new File(appDir, "PuliziaCassaforte.properties")))
} catch (IOException e) {
    println "Could not read PuliziaCassaforte.properties: ${e.message}"
    System.exit(1)
}

// if buildMapClientType is not set in properties, default to 'db2'
if (!cfgProps.containsKey('buildMapClientType')) {
    cfgProps.setProperty('buildMapClientType', 'dbb')
}
// if fileOpsType is not set in properties, default to 'zos'
if (!cfgProps.containsKey('fileOpsType')) {
    cfgProps.setProperty('fileOpsType', 'zos')
}

def gcl = new GroovyClassLoader(this.class.classLoader)
gcl.parseClass(new File("${dbbBuild}/groovy/FullPuliziaCassaforte.groovy"))
def clazz = gcl.loadClass('com.intesasanpaolo.bes.pc.PuliziaCassaforteImpl')
def puliziaCassaforteImpl = clazz.getDeclaredConstructor().newInstance()

int errors = puliziaCassaforteImpl.doPuliziaPostBuild(sourceFilePath, environment, buildGroup, cfgProps, resolvedBuildGroup)
println "PuliziaCassaforte completed with ${errors} errors."
return errors