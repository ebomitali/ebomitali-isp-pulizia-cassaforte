// Standalone runner — no DBB build context required.
// Uses a plain GroovyClassLoader to load FullPuliziaCassaforte.groovy from the current directory.
// Run from this script's directory (full-fat-source/src/test/sh/mac/), via mrunpct2.sh:
//   groovy RunPuliziaCassaforte.groovy <list path> <environment> <build group>
// Reads PuliziaCassaforte.properties from the current working directory.

if (args.length != 3) {
    println "Usage: groovy RunPuliziaCassaforte.groovy <list path> <environment> <build group>"
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

// FullPuliziaCassaforte.groovy is copied alongside this script by mrunpct2.sh
def fatSourceFile = new File("FullPuliziaCassaforte.groovy")
if (!fatSourceFile.exists()) {
    println "FullPuliziaCassaforte.groovy not found at: ${fatSourceFile.canonicalPath}"
    System.exit(1)
}

def gcl = new GroovyClassLoader(this.class.classLoader)
Class scriptClass = gcl.parseClass(fatSourceFile)
def scriptInstance = scriptClass.getDeclaredConstructor().newInstance()
def puliziaCassaforte = scriptInstance.createPuliziaCassaforteImpl()

int errors = puliziaCassaforte.doPuliziaCassaforte(fileListFile, environment, buildGroup, cfgProps)
println "PuliziaCassaforte completed with ${errors} errors."
if (errors > 0) System.exit(1)
