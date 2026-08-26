// Standalone runner — no DBB build context required.
// Uses a plain GroovyShell to load FullPuliziaCassaforte.groovy from the source tree.
// Run from this script's directory (full-fat-source/src/test/sh/local/):
//   groovyz RunPuliziaCassaforte.groovy <list path> <environment> <build group>
// Reads PuliziaCassaforte.properties from the current working directory.

if (args.length != 3) {
    println "Usage: groovyz RunPuliziaCassaforte.groovy <list path> <environment> <build group>"
    System.exit(1)
}

String fileList    = args[0]
String environment = args[1]
String buildGroup  = args[2]

File fileListFile = new File(fileList)
if (!fileListFile.exists()) {
    println "List does not exist: ${fileList}"
    System.exit(1)
}

Properties cfgProps = new Properties()
try {
    cfgProps.load(new FileInputStream("PuliziaCassaforte.properties"))
} catch (IOException e) {
    println "Could not read PuliziaCassaforte.properties: ${e.message}"
    System.exit(1)
}

// Locate FullPuliziaCassaforte.groovy relative to this script's source location.
// Layout: this script is at  …/full-fat-source/src/test/sh/local/
//         fat source lives at …/full-fat-source/src/main/groovy/
def scriptDir     = new File(getClass().protectionDomain.codeSource.location.toURI()).parentFile
def fatSourceFile = new File(scriptDir, "../../../main/groovy/FullPuliziaCassaforte.groovy").canonicalFile
if (!fatSourceFile.exists()) {
    println "FullPuliziaCassaforte.groovy not found at: ${fatSourceFile}"
    System.exit(1)
}

def shell  = new GroovyShell(this.class.classLoader, new Binding())
def loaded = shell.parse(fatSourceFile)
loaded.run()

def puliziaCassaforte = loaded.createPuliziaCassaforteImpl()
int errors = puliziaCassaforte.run(fileList, environment, buildGroup, cfgProps)
println "PuliziaCassaforte completed with ${errors} errors."
if (errors > 0) System.exit(1)
