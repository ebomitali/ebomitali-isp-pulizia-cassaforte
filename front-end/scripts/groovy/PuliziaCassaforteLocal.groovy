// scripts/PuliziaCassaforteLocal.groovy
// Invocation: groovyz -cp ${DBB_BUILD}/groovy/pulizia-cassaforte/lib/pulizia-cassaforte.jar:${DBB_BUILD}/groovy/pulizia-cassaforte/lib/pulizia-cassaforte-zos.jar PuliziaCassaforteLocal.groovy <file-lista> <environment> <build-group>

if (args.size() < 3) {
    System.err.println "Usage: PuliziaCassaforteLocal.groovy <file-lista> <environment> <build-group>"
    System.exit(1)
}

def listFile    = args[0]
def environment = args[1]
def buildGroup  = args[2]

def rulesPath     = new File('.', 'build-data/rules.csv').canonicalPath
def stageMapPath  = new File('.', 'build-data/stage-map.csv').canonicalPath
def hlq           = ''   // override for local testing, e.g. hlq = 'U0G9700'

def ops         = loadFileOps()
def rules       = new DeletionRulesLoader().load(rulesPath)
def stageMap    = new StageMapLoader().load(stageMapPath)
def extractor   = new PathVariableExtractor()
def buildMap    = buildMapClient(buildGroup)
def envChain    = new EnvironmentChain()
def deleteLogic = new DeleteCassaforteLogic(ops: ops, rules: rules, buildMap: buildMap)
def sfilamento  = new SfilamentoLogic(
    ops: ops, deleteLogic: deleteLogic, rules: rules, envChain: envChain,
    extractor: extractor, stageMap: stageMap, hlq: hlq
)

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
                def vars = extractor.extract(sourcePath, environment, stageMap, hlq)
                deleteLogic.execute(sourcePath, fileType, vars, buildGroup)
                processed++
                break
            case 'S':
                sfilamento.execute(sourcePath, fileType, environment, buildGroup)
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

ZosFileOps loadFileOps() {
    try {
        return Class.forName('ZosFileOpsUSS').newInstance() as ZosFileOps
    } catch (ClassNotFoundException ignored) {
        return new LocalFileOps()
    }
}

BuildMapClient buildMapClient(String bg) {
    def f = new File('.', 'build-data/buildmap.json')
    if (f.exists()) return new LocalBuildMapClient(f.canonicalPath)
    return [getGeneratedObjects: { sp, g -> [] }] as BuildMapClient
}

String resolveFileType(String sourcePath) {
    def filename = sourcePath.tokenize('/').last()
    def ext = filename.contains('.') ? filename.substring(filename.lastIndexOf('.') + 1) : filename
    ext.toUpperCase().padRight(8).take(8)
}
