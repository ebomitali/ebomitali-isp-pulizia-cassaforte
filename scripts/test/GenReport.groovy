if (args.length != 4) {
    println "Usage: groovyz GenReport.groovy <list-path> <environment> <build-group> <config-path>"
    System.exit(1)
}

String listFile    = args[0]
String environment = args[1]
String buildGroup  = args[2]
String configPath  = args[3]

if (!new File(listFile).exists()) {
    println "List file not found: ${listFile}"
    System.exit(1)
}

Properties cfgProps = new Properties()
try {
    cfgProps.load(new FileInputStream(configPath))
} catch (IOException e) {
    println "Could not read config '${configPath}': ${e.message}"
    System.exit(1)
}

def pcloaded = loadScript(new File("FullPuliziaCassaforte.groovy"))
def impl     = pcloaded.createPuliziaCassaforteImpl()
int errors   = impl.run(listFile, environment, buildGroup, cfgProps)
println "GenReport completed with ${errors} error(s)."
if (errors > 0) {
    System.exit(1)
}
