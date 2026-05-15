// scripts/PuliziaCassaforte.groovy
// Invocation: groovyz -cp ${DBB_BUILD}/groovy/pulizia-cassaforte/lib/pulizia-cassaforte.jar:${DBB_BUILD}/groovy/pulizia-cassaforte/lib/pulizia-cassaforte-zos.jar PuliziaCassaforte.groovy <file-lista> <environment> <build-group>

if (args.size() < 3) {
    System.err.println "Usage: PuliziaCassaforte.groovy <file-lista> <environment> <build-group>"
    System.exit(1)
}

def listFile    = args[0]
def environment = args[1]
def buildGroup  = args[2]

def rulesPath = new File('.', 'build-data/rules.csv').canonicalPath

def ops         = new LocalFileOps()            // on USS: swap to ZosFileOpsUSS
def rules       = new DeletionRulesLoader().load(rulesPath)
def buildMap    = buildMapClient(buildGroup)
def envChain    = new EnvironmentChain()
def deleteLogic = new DeleteCassaforteLogic(ops: ops, rules: rules, buildMap: buildMap)
def sfilamento  = new SfilamentoLogic(
    ops: ops, deleteLogic: deleteLogic, rules: rules, envChain: envChain
)

def stage  = envChain.getStage(environment)
def system = extractSystem(buildGroup)

int processed = 0, errors = 0
new File(listFile).eachLine { raw ->
    def line = raw.trim()
    if (!line || line.startsWith('#')) return
    def comma = line.indexOf(',')
    if (comma < 0) { System.err.println "Skipping malformed line: '$line'"; errors++; return }
    def action     = line.substring(0, comma).trim().toUpperCase()
    def sourcePath = line.substring(comma + 1).trim()
    def fileType   = resolveFileType(sourcePath)

    try {
        switch (action) {
            case 'C':
                deleteLogic.execute(sourcePath, fileType, stage, system, buildGroup)
                processed++
                break
            case 'S':
                sfilamento.execute(sourcePath, fileType, environment, system, buildGroup)
                processed++
                break
            default:
                System.err.println "Unknown action '$action' in line: '$line'"
                errors++
        }
    } catch (Exception e) {
        System.err.println "ERROR processing '$sourcePath': ${e.message}"
        errors++
    }
}

println "PuliziaCassaforte: processed=${processed} errors=${errors}"
if (errors > 0) System.exit(1)

// ─── helpers ────────────────────────────────────────────────────────────────

BuildMapClient buildMapClient(String bg) {
    // TODO: on USS replace with DBB build-result map client
    def f = new File('.', 'build-data/buildmap.json')
    if (f.exists()) return new LocalBuildMapClient(f.canonicalPath)
    return [getGeneratedObjects: { sp, g -> [] }] as BuildMapClient
}

String extractSystem(String buildGroup) {
    // TODO: implement ISP-specific system code extraction from build group name.
    // Build group format example: yn_r_01_ato_r1 → first token as system prefix.
    buildGroup?.tokenize('_')?.first()?.toUpperCase() ?: ''
}

String resolveFileType(String sourcePath) {
    // TODO: implement ISP-specific mapping from file path/extension to 8-char type code.
    // Currently returns the file extension uppercased and padded to 8 chars.
    def filename = sourcePath.tokenize('/').last()
    def ext = filename.contains('.') ? filename.substring(filename.lastIndexOf('.') + 1) : filename
    ext.toUpperCase().padRight(8).take(8)
}
