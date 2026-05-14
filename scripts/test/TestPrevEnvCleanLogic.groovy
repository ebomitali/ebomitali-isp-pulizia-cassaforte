// scripts/test/TestPrevEnvCleanLogic.groovy
import java.nio.file.*

// ── locate fixture files relative to this script ──────────────────────────────
def scriptDir = new File(getClass().protectionDomain.codeSource.location.toURI()).parentFile.toPath()
def rulesCsv  = scriptDir.resolve("fixtures/rules.csv").toFile().canonicalPath
def bmJson    = scriptDir.resolve("fixtures/buildmap.json").toFile().canonicalPath

// ── temp base dir ──────────────────────────────────────────────────────────────
def base = "/tmp/zos-sim-prev-${System.currentTimeMillis()}"
def ops  = new LocalFileOps(base)

// ── load rules and build map ───────────────────────────────────────────────────
def rules    = new DeletionRulesLoader().load(rulesCsv)
def buildMap = new LocalBuildMapClient(bmJson)

// ── create logic instances ─────────────────────────────────────────────────────
def deleteLogic = new DeleteCassaforteLogic(ops: ops, rules: rules, buildMap: buildMap)
def logic       = new PrevEnvCleanLogic(deleteLogic: deleteLogic)

// ══ Test A: current env is ST → deletes from predecessor ATO (stage O1) ════════
// Seed PGMCOBOL in ATO's COB library (stage O1)
def cobLib    = "LTM00.D9PO1.PE000.LING.COB@@@@@.@@.COPY"
def cobMember = Paths.get(base, cobLib, "PGMCOBOL")
Files.createDirectories(cobMember.parent)
Files.writeString(cobMember, "content")

assert ops.exists("//${cobLib}(PGMCOBOL)") : "TestA: PGMCOBOL should exist before execute"

def countA = logic.execute(
    '/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl',
    'ACPYCOB ',
    'ST', '', 'yn_r_01_ato_r1'
)
assert countA == 1 : "TestA: expected count=1, got ${countA}"
assert !ops.exists("//${cobLib}(PGMCOBOL)") : "TestA: PGMCOBOL should no longer exist in ATO's library after delete"

// ══ Test B: current env is ATO (no predecessor) → no-op ════════════════════════
def countB = logic.execute(
    '/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl',
    'ACPYCOB ',
    'ATO', '', 'yn_r_01_ato_r1'
)
assert countB == 0 : "TestB: expected count=0 for ATO (no predecessor), got ${countB}"

// ── Cleanup ────────────────────────────────────────────────────────────────────
new File(base).deleteDir()

println "TestPrevEnvCleanLogic: PASS"
