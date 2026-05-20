// scripts/GetBuildMapFields.groovy
// USS utility — print all fields of a single DBB build map entry.
// After upload to USS: chtag -tc IBM-1047 GetBuildMapFields.groovy
//
// Usage:
//   groovyz GetBuildMapFields.groovy <buildGroup> <sourcePath>
//   groovyz GetBuildMapFields.groovy [--dbid <userId>] [--dbpf <passwordFile>] <buildGroup> <sourcePath>
//
// DB2 connection is read from ${DBB_CONF:-${DBB_HOME}/conf}/db2Connection.conf
// Defaults: --dbid GADBB01  --dbpf /prodotti/DEE/test/conf/DB01PSW.txt

import com.ibm.dbb.metadata.*
import com.ibm.dbb.build.*
import java.text.SimpleDateFormat

// ─── CLI ─────────────────────────────────────────────────────────────────────

String userId     = 'GADBB01'
String pwFilePath = '/prodotti/DEE/test/conf/DB01PSW.txt'
List<String> positionalArgs = []

int i = 0
while (i < args.size()) {
    if (args[i] == '--dbid') {
        if (i + 1 >= args.size()) {
            System.err.println "ERROR: --dbid requires an argument"
            System.exit(1)
        }
        userId = args[++i]
    } else if (args[i] == '--dbpf') {
        if (i + 1 >= args.size()) {
            System.err.println "ERROR: --dbpf requires an argument"
            System.exit(1)
        }
        pwFilePath = args[++i]
    } else {
        positionalArgs.add(args[i])
    }
    i++
}

if (positionalArgs.size() < 2) {
    System.err.println "Usage: GetBuildMapFields.groovy [--dbid <userId>] [--dbpf <passwordFile>] <buildGroup> <sourcePath>"
    System.exit(1)
}

String buildGroupName = positionalArgs[0]
String sourcePath     = positionalArgs[1]

// ─── DB2 connection ───────────────────────────────────────────────────────────

String confDir = System.getenv('DBB_CONF') ?: "${System.getenv('DBB_HOME')}/conf"
File db2ConfFile = new File(confDir, 'db2Connection.conf')
if (!db2ConfFile.exists()) {
    System.err.println "ERROR: DB2 conf not found: ${db2ConfFile.canonicalPath}"
    System.exit(1)
}

Properties properties = new Properties()
db2ConfFile.withInputStream { stream -> properties.load(stream) }

File pwFile = new File(pwFilePath)
MetadataStore metadataStore = MetadataStoreFactory.createDb2MetadataStore(userId, pwFile, properties)

// ─── Query ───────────────────────────────────────────────────────────────────

BuildGroup buildGroup = metadataStore.getBuildGroup(buildGroupName)
if (!buildGroup) {
    System.err.println "ERROR: build group '${buildGroupName}' not found in metadata store"
    System.exit(1)
}

BuildMap buildMap = buildGroup.getBuildMap(sourcePath)
if (!buildMap) {
    System.err.println "ERROR: no build map found for source path: ${sourcePath}"
    System.exit(1)
}

// ─── Output ──────────────────────────────────────────────────────────────────

Date bmCreationDate = buildMap.getCreated()
String bmCreation = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss.SSSSS Z").format(bmCreationDate)
println "Build Map Creation: " + bmCreation
println "Build File        : " + buildMap.getBuildFile()
println "Group             : " + buildMap.getGroup()

buildMap.getInputs().each { input ->
    println "Input Path: " + input.getPath()
}

buildMap.getOutputs().each { output ->
    println "Output Member: " + output.getMember() + " | Dataset: " + output.getDataset()
}
