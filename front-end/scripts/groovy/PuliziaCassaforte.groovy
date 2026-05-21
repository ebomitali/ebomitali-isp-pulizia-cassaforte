// scripts/PuliziaCassaforte.groovy
// Invocation: groovyz -cp ${DBB_BUILD}/groovy/pulizia-cassaforte/lib/pulizia-cassaforte.jar:${DBB_BUILD}/groovy/pulizia-cassaforte/lib/pulizia-cassaforte-zos.jar PuliziaCassaforte.groovy [--dbid <user>] [--dbpf <pwFile>] [--db2-config <db2Connection.conf>] [--bmf <buildmap.json>] [--hlq <hlq>] <file-lista> <environment> <build-group>

// ─── CLI ─────────────────────────────────────────────────────────────────────

String dbid      = null
String dbpf      = null
String db2Config = null
String bmf       = null
String hlq       = ''
List<String> positionalArgs = []

int i = 0
while (i < args.size()) {
    if (args[i] == '--dbid') {
        if (i + 1 >= args.size()) { System.err.println "ERROR: --dbid requires an argument"; System.exit(1) }
        dbid = args[++i]
    } else if (args[i] == '--dbpf') {
        if (i + 1 >= args.size()) { System.err.println "ERROR: --dbpf requires an argument"; System.exit(1) }
        dbpf = args[++i]
    } else if (args[i] == '--db2-config') {
        if (i + 1 >= args.size()) { System.err.println "ERROR: --db2-config requires an argument"; System.exit(1) }
        db2Config = args[++i]
    } else if (args[i] == '--bmf') {
        if (i + 1 >= args.size()) { System.err.println "ERROR: --bmf requires an argument"; System.exit(1) }
        bmf = args[++i]
    } else if (args[i] == '--hlq') {
        if (i + 1 >= args.size()) { System.err.println "ERROR: --hlq requires an argument"; System.exit(1) }
        hlq = args[++i]
    } else {
        positionalArgs.add(args[i])
    }
    i++
}

if (positionalArgs.size() < 3) {
    System.err.println "Usage: PuliziaCassaforte.groovy [--dbid <user>] [--dbpf <pwFile>] [--db2-config <conf>] [--bmf <buildmap.json>] [--hlq <hlq>] <file-lista> <environment> <build-group>"
    System.exit(1)
}

def listFile    = positionalArgs[0]
def environment = positionalArgs[1]
def buildGroup  = positionalArgs[2]
int errors = 0

// ─── Validation && Run ─────────────────────────────────────────────────────────

File db2ConfigFile = null

if (dbid && dbpf) {
    def pwFile = new File(dbpf)
    if (!pwFile.exists()) {
        System.err.println "ERROR: password file not found: ${pwFile.canonicalPath}"
        System.exit(1)
    }
    String confDir = System.getenv('DBB_CONF') ?: "${System.getenv('DBB_HOME')}"
    db2ConfigFile  = db2Config ? new File(db2Config) : new File(confDir, 'db2Connection.conf')
    if (!db2ConfigFile.exists()) {
        System.err.println "ERROR: DB2 config file not found: ${db2ConfigFile.canonicalPath}"
        System.exit(1)
    }
    errors = new PuliziaCassaforteImpl().run(listFile, environment, buildGroup, dbid, dbpf, db2ConfigFile, hlq)
} else {
    def bmFile = bmf ? new File(bmf) : new File('.', 'build-data/buildmap.json')
    if (!bmFile.exists()) {
        System.err.println "ERROR: build map file not found: ${bmFile.canonicalPath}"
        System.exit(1)
    }
    errors = new PuliziaCassaforteImpl().run(listFile, environment, buildGroup, bmFile, hlq)
}

if (errors > 0) System.exit(1)
return 0
