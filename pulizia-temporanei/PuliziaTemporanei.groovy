#!/usr/bin/env groovy

import com.intesasanpaolo.bes.pt.PuliziaTemporaneiImpl

def cli = new CliBuilder(usage: 'PuliziaTemporanei <DSN-pattern> [options]')
cli.h('show help')
cli.c('configuration file (default: PuliziaTemporanei.properties)', args: 1)

def options = cli.parse(args)
if (options.h) {
    cli.usage()
    return
}

def remainingArgs = options.arguments()
if (remainingArgs.isEmpty()) {
    System.err.println('ERROR: DSN pattern argument required')
    cli.usage()
    System.exit(1)
}

def dsnPattern = remainingArgs[0]
def configFile = options.c ?: 'PuliziaTemporanei.properties'

try {
    def impl = new PuliziaTemporaneiImpl()
    int count = impl.doPuliziaTemporanei(dsnPattern, configFile)
    println("Successfully deleted ${count} dataset(s)")
    System.exit(0)
} catch (Exception e) {
    System.err.println("ERROR: ${e.message}")
    e.printStackTrace()
    System.exit(1)
}
