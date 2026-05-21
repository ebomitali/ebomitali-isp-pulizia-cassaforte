// scripts/QueryBuildMap.groovy
// USS utility — query the DBB Db2 metadata store and display build maps.
// After upload to USS: chtag -tc IBM-1047 QueryBuildMap.groovy
//
// Usage:
//   groovyz QueryBuildMap.groovy <buildGroup>                    # list all build maps in group
//   groovyz QueryBuildMap.groovy <buildGroup> <sourcePath>       # show map for one source file
//   groovyz QueryBuildMap.groovy -u,--user <user> -f,--password-file <file> <buildGroup> [sourcePath]
//   groovyz QueryBuildMap.groovy -C,--db2-config <file> <buildGroup> [sourcePath]
//
// DB2 connection is read from -C option, ${DBB_CONF:-${DBB_HOME}/conf}/db2Connection.conf, or fails if not found

import com.ibm.dbb.metadata.BuildGroup
import com.ibm.dbb.metadata.BuildMap
import com.ibm.dbb.metadata.MetadataStoreFactory

// ─── CLI ─────────────────────────────────────────────────────────────────────

String userId = null
String pwFile = null
String db2Conf = null
List<String> positionalArgs = []

int i = 0
while (i < args.size()) {
    if (args[i] == '-u' || args[i] == '--user') {
        if (i + 1 >= args.size()) {
            System.err.println "ERROR: -u/--user requires an argument"
            System.exit(1)
        }
        userId = args[++i]
    } else if (args[i] == '-f' || args[i] == '--password-file') {
        if (i + 1 >= args.size()) {
            System.err.println "ERROR: -f/--password-file requires an argument"
            System.exit(1)
        }
        pwFile = args[++i]
    } else if (args[i] == '-C' || args[i] == '--db2-config') {
        if (i + 1 >= args.size()) {
            System.err.println "ERROR: -C/--db2-config requires an argument"
            System.exit(1)
        }
        db2Conf = args[++i]
    } else {
        positionalArgs.add(args[i])
    }
    i++
}

if (positionalArgs.size() < 1) {
    System.err.println "Usage: QueryBuildMap.groovy [-u|--user <user>] [-f|--password-file <file>] <buildGroup> [sourcePath]"
    System.exit(1)
}

String buildGroupName = positionalArgs[0]
String sourcePath     = positionalArgs.size() > 1 ? positionalArgs[1] : null

// ─── DB2 connection ───────────────────────────────────────────────────────────
// if db2ConfFile is provided via CLI, use it; otherwise look in ${DBB_CONF}/conf}/db2Connection.conf; fail if not found
File db2ConfFile = null
if (db2Conf) {
    db2ConfFile = new File(db2Conf)
} else {
    String confDir = System.getenv('DBB_CONF') ?: "${System.getenv('DBB_HOME')}/conf"
    db2ConfFile = new File(confDir, 'db2Connection.conf')
}
if (!db2ConfFile.exists()) {
    System.err.println "ERROR: DB2 conf not found: ${db2ConfFile.canonicalPath}"
    System.exit(1)
}

Properties db2ConnectionProps = new Properties()
InputStream is = db2ConfFile.newInputStream()
db2ConnectionProps.load(is)
is.close()

// create a configured connection
MetadataStore metadataStore = MetadataStoreFactory.createDb2MetadataStore(userId, pwFile, db2ConnectionProps)

// ─── Query ───────────────────────────────────────────────────────────────────

BuildGroup group = metadataStore.getBuildGroup(buildGroupName)
if (!group) {
    System.err.println "ERROR: build group '${buildGroupName}' not found in metadata store"
    System.exit(1)
}

if (sourcePath) {
    BuildMap bm = group.getBuildMap(sourcePath)
    if (!bm) {
        println "No build map found for source path: ${sourcePath}"
        System.exit(0)
    }
    printBuildMap(bm)
} else {
    List<BuildMap> all = group.getBuildMaps()
    println "Build group : ${buildGroupName}"
    println "Build maps  : ${all.size()}"
    println ''
    all.each { bm -> printBuildMap(bm) }
}

// ─── Output ──────────────────────────────────────────────────────────────────

def printBuildMap(BuildMap bm) {
    println '=' * 72
    println "BUILD FILE : ${bm.getBuildFile()}"
    println "GROUP      : ${bm.getGroup()}"
    println "RESULT     : ${bm.getResult() ?: 'N/A'}"
    println "CREATED    : ${bm.getCreated()}"

    def outputs = bm.getOutputs()
    if (outputs) {
        println "OUTPUTS    : ${outputs.size()}"
        outputs.each { out ->
            println "  member=${out.getMember()}  dataset=${out.getDataset()}  deployType=${out.getDeployType() ?: '-'}"
        }
    } else {
        println "OUTPUTS    : none"
    }

    def inputs = bm.getInputs()
    if (inputs) {
        println "INPUTS     : ${inputs.size()}"
        inputs.each { inp ->
            println "  lname=${inp.getLname()}  category=${inp.getCategory()}  path=${inp.getPath() ?: 'unresolved'}"
        }
    }
    println ''
}
