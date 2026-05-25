//This groovy script take three argumments from command line:
// 1. The path a file conteining <action>;<file to process> 
// 2. The environment (a string that represents the environment, e.g., "ATO")
// 3. The build group (a string that represents the build group, e.g., "ATO")
// This groovy script instantiate PuliziaCassaforte class and call the method run passing the three arguments.

import java.nio.file.Files
import java.nio.file.Paths

if (args.length != 3) {
    println "Usage: groovy PuliziaCassaforte.groovy <environment> <buildGroup> <inputFilePath>"
    System.exit(1)
}

def environment = args[0]
def buildGroup = args[1]
def inputFilePath = args[2]
def inputFile = new File(inputFilePath)
if (!inputFile.exists()) {
    println "Input file does not exist: ${inputFilePath}"
    System.exit(1)
}

def impl   = new PuliziaCassaforteImpl()
def errors = impl.run(lista, environment, buildGroup)
println "PuliziaCassaforte completed with ${errors} errors."