// scripts/QueryBuildMapOnPuliziaCassaforteJar.groovy
// Local utility — queries a pre-captured DBB build map JSON using LocalBuildMapClient from the jar.
// No IBM/DBB deps required.
//
// Usage:
//   groovy -cp <path-to>/pulizia-cassaforte.jar QueryBuildMapOnPuliziaCassaforteJar.groovy <buildGroup> <sourcePath>
//   groovy -cp <path-to>/pulizia-cassaforte.jar QueryBuildMapOnPuliziaCassaforteJar.groovy [-bmf <buildmap.json>] <buildGroup> <sourcePath>
//
// Build map JSON defaults to build-data/buildmap.json in the current directory.

// ─── CLI ─────────────────────────────────────────────────────────────────────

String bmFilePath = 'build-data/buildmap.json'
List<String> positionalArgs = []

int i = 0
while (i < args.size()) {
    if (args[i] == '-bmf' || args[i] == '--build-map-file') {
        if (i + 1 >= args.size()) {
            System.err.println "ERROR: -bmf/--build-map-file requires an argument"
            System.exit(1)
        }
        bmFilePath = args[++i]
    } else {
        positionalArgs.add(args[i])
    }
    i++
}

if (positionalArgs.size() < 2) {
    System.err.println "Usage: QueryBuildMapOnPuliziaCassaforteJar.groovy [-bmf <buildmap.json>] <buildGroup> <sourcePath>"
    System.exit(1)
}

String buildGroupName = positionalArgs[0]
String sourcePath     = positionalArgs[1]

// ─── Query ───────────────────────────────────────────────────────────────────

File bmFile = new File(bmFilePath)
if (!bmFile.exists()) {
    System.err.println "ERROR: build map file not found: ${bmFile.canonicalPath}"
    System.exit(1)
}

BuildMapClient client = new LocalBuildMapClient(bmFile.canonicalPath)
List<Map<String, String>> results = client.getGeneratedObjects(sourcePath, buildGroupName)

// ─── Output ──────────────────────────────────────────────────────────────────

println "Build group : ${buildGroupName}"
println "Source path : ${sourcePath}"
println "Results     : ${results.size()}"
println ''

if (results.isEmpty()) {
    println "No generated objects found."
} else {
    results.each { obj ->
        println "  member=${obj.member}  library=${obj.library}"
    }
}
