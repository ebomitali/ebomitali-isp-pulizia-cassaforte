// Entry point for running PuliziaTemporanei from the command line 
// via groovy RunPuliziaTemporanei.groovy
// Simple argument parser without CliBuilder to avoid classpath issues
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

try {
    // Load fat source from current directory (FullPuliziaTemporanei.groovy)
    def fatFile = new File('FullPuliziaTemporanei.groovy')
    if (!fatFile.exists()) {
        System.err.println("ERROR: FullPuliziaTemporanei.groovy not found in current directory")
        System.exit(1)
    }

    def gcl = new GroovyClassLoader(this.class.classLoader)
    gcl.parseClass(fatFile)
    // After parsing, load the specific implementation class we need
    def clazz = gcl.loadClass('com.intesasanpaolo.bes.pt.PuliziaTemporaneiImpl')
    def impl = clazz.getDeclaredConstructor().newInstance()

    println("Running PuliziaTemporanei with DSN pattern: ${dsnPattern}")
    int count = impl.doPuliziaTemporaneiFromFile(dsnPattern, configFile)
    println("Successfully deleted ${count} dataset(s)")
    System.exit(0)
} catch (Exception e) {
    System.err.println("ERROR: ${e.message}")
    e.printStackTrace()
    System.exit(1)
}
