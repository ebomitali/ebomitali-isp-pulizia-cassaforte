// scripts/test/TestDeleteCassaforteLogic.groovy
import java.nio.file.*

// ── locate fixture files relative to this script ──────────────────────────────
def scriptDir = new File(getClass().protectionDomain.codeSource.location.toURI()).parentFile.toPath()
def rulesCsv  = scriptDir.resolve("fixtures/rules.csv").toFile().canonicalPath
def bmJson    = scriptDir.resolve("fixtures/buildmap.json").toFile().canonicalPath

// ── temp base dir ──────────────────────────────────────────────────────────────
def base = "/tmp/zos-sim-dca-${System.currentTimeMillis()}"
def ops  = new LocalFileOps(base)

// ── load rules and build map ───────────────────────────────────────────────────
def rules    = new DeletionRulesLoader().load(rulesCsv)
def buildMap = new LocalBuildMapClient(bmJson)

// ── create logic instance ──────────────────────────────────────────────────────
def logic = new DeleteCassaforteLogic(ops: ops, rules: rules, buildMap: buildMap)

// ══ Test case A: NO flag ═══════════════════════════════════════════════════════
// Seed PGMCOBOL in the COB library
def cobLib  = "LTM00.D9PO1.PE000.LING.COB@@@@@.@@.COPY"
def cobMember = Paths.get(base, cobLib, "PGMCOBOL")
Files.createDirectories(cobMember.parent)
Files.writeString(cobMember, "content")

assert ops.exists("//${cobLib}(PGMCOBOL)") : "PGMCOBOL should exist before execute"

def countA = logic.execute(
    '/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl',
    'ACPYCOB ',
    'O1', '', 'yn_r_01_ato_r1'
)
assert countA == 1 : "Test A: expected count=1, got ${countA}"
assert !ops.exists("//${cobLib}(PGMCOBOL)") : "Test A: PGMCOBOL should no longer exist after delete"

// ══ Test case B: idempotent (already deleted) ══════════════════════════════════
def countB = logic.execute(
    '/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl',
    'ACPYCOB ',
    'O1', '', 'yn_r_01_ato_r1'
)
assert countB == 0 : "Test B: expected count=0 (idempotent), got ${countB}"

// ══ Test case C: BUILD MAP flag ════════════════════════════════════════════════
// Seed MAPOBJ in the MAP library
def mapLib    = "LTM00.D9PO1.PE000.LING.MAP@@@@@.@@.COPY"
def mapMember = Paths.get(base, mapLib, "MAPOBJ")
Files.createDirectories(mapMember.parent)
Files.writeString(mapMember, "content")

assert ops.exists("//${mapLib}(MAPOBJ)") : "MAPOBJ should exist before execute"

def countC = logic.execute(
    '/dbb/DEE/IBM/yn_r_01_ato_r1/src/mapasm/batch/mapobj.asm',
    'SZFSSWG ',
    'O1', '', 'yn_r_01_ato_r1'
)
assert countC == 1 : "Test C: expected count=1, got ${countC}"
assert !ops.exists("//${mapLib}(MAPOBJ)") : "Test C: MAPOBJ should no longer exist after delete"

// ══ Test case D: memberName static method ═════════════════════════════════════
assert DeleteCassaforteLogic.memberName('/path/to/abcdef.cbl') == 'ABCDEF' : \
    "Test D1: expected ABCDEF"
assert DeleteCassaforteLogic.memberName('/path/to/NOEEXT') == 'NOEEXT' : \
    "Test D2: expected NOEEXT"

// ── Cleanup ────────────────────────────────────────────────────────────────────
new File(base).deleteDir()

println "TestDeleteCassaforteLogic: PASS"
