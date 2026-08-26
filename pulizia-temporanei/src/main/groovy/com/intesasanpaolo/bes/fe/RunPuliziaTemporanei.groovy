#!/usr/bin/env groovy

import com.intesasanpaolo.bes.pt.PuliziaTemporaneiImpl

// Simple argument parser without CliBuilder to avoid classpath issues in integration tests
String dsnPattern = null
String configFile = 'PuliziaTemporanei.properties'
boolean showHelp = false

int i = 0
while (i < args.length) {
    String arg = args[i]
    if (arg == '-h' || arg == '--help') {
        showHelp = true
        break
    } else if (arg == '-c') {
        i++
        if (i < args.length) {
            configFile = args[i]
        }
    } else if (!arg.startsWith('-')) {
        if (dsnPattern == null) {
            dsnPattern = arg
        }
    }
    i++
}

if (showHelp) {
    println('Usage: PuliziaTemporanei <DSN-pattern> [options]')
    println('Options:')
    println('  -h, --help                   show help')
    println('  -c <file>                    configuration file')
    System.exit(0)
}

if (dsnPattern == null) {
    System.err.println('ERROR: DSN pattern argument required')
    System.exit(1)
}

String dbbBuild = System.getenv("DBB_BUILD")
if (dbbBuild == null) {
    println "Environment variable DBB_BUILD is not set."
    System.exit(1)
}

// Read PuliziaCassaforte property file from current directory
Properties cfgProps = new Properties()
try {
    cfgProps.load(new FileInputStream("PuliziaCassaforte.properties"))
} catch (IOException e) {
    println "PuliziaTemporanei.properties not found, using default values"
}

// if fileOpsType is not set in properties, default to 'zos'
if (!cfgProps.containsKey('fileOpsType')) {
    cfgProps.setProperty('fileOpsType', 'zos')
}


try {

    def gcl = new GroovyClassLoader(this.class.classLoader)
    gcl.parseClass(new File("FullPuliziaTemporanei.groovy"))
    def clazz = gcl.loadClass('com.intesasanpaolo.bes.pt.PuliziaTemporaneiImpl')

    def impl = clazz.getDeclaredConstructor().newInstance()
    int count = impl.doPuliziaTemporanei(dsnPattern, configFile)
    println("Successfully deleted ${count} dataset(s)")
    System.exit(0)
} catch (Exception e) {
    System.err.println("ERROR: ${e.message}")
    e.printStackTrace()
    System.exit(1)
}
