// scripts/test/TestSfilamentoLogic.groovy
import java.nio.file.*

// ── locate fixture files relative to this script ──────────────────────────────
def scriptDir = new File(getClass().protectionDomain.codeSource.location.toURI()).parentFile.toPath()
def rulesCsv  = scriptDir.resolve("fixtures/rules.csv").toFile().canonicalPath
def bmJson    = scriptDir.resolve("fixtures/buildmap.json").toFile().canonicalPath

// ══ Test A: SJCL restore happens ══════════════════════════════════════════════
def baseA = "/tmp/zos-sim-sfil-${System.currentTimeMillis()}"
def opsA  = new LocalFileOps(baseA)
def rules = new DeletionRulesLoader().load(rulesCsv)
def buildMap = new LocalBuildMapClient(bmJson)

def deleteLogicA = new DeleteCassaforteLogic(ops: opsA, rules: rules, buildMap: buildMap)
def sfilA        = new SfilamentoLogic(ops: opsA, deleteLogic: deleteLogicA, rules: rules)

// Seed MYJCL in ST's cassaforte SJCL library (stage S1)
def stSjclLib   = "LTM00.D9PS1.PE000.@@@@.@@@@@@@@.@@.SJCL"
def stSjclMember = Paths.get(baseA, stSjclLib, "MYJCL")
Files.createDirectories(stSjclMember.parent)
Files.writeString(stSjclMember, "st-jcl-content")

// Seed MYJCL in PR's cassaforte SJCL library (stage P1)
def prSjclLib    = "LTM00.D9PP1.PE000.@@@@.@@@@@@@@.@@.SJCL"
def prSjclMember = Paths.get(baseA, prSjclLib, "MYJCL")
Files.createDirectories(prSjclMember.parent)
Files.writeString(prSjclMember, "pr-jcl-content")

assert opsA.exists("//${stSjclLib}(MYJCL)") : "TestA: ST SJCL MYJCL should exist before execute"
assert opsA.exists("//${prSjclLib}(MYJCL)") : "TestA: PR SJCL MYJCL should exist before execute"

def resultA = sfilA.execute(
    '/dbb/DEE/IBM/yn_r_01_st_r1/src/jcl/batch/myjcl.jcl',
    'SJCL    ',
    'ST', '', 'yn_r_01_st_r1'
)

def tocolbLib = "LTM00.D9PS1.PE000.TO@@.COLB@@@@.@@.SJCL"

assert resultA == true         : "TestA: execute should return true, got ${resultA}"
assert !opsA.exists("//${stSjclLib}(MYJCL)") : "TestA: ST cassaforte SJCL MYJCL should be deleted"
assert opsA.exists("//${tocolbLib}(MYJCL)")  : "TestA: TOCOLB MYJCL should exist after restore"

new File(baseA).deleteDir()

// ══ Test B: non-JCL type — delete only, no restore ════════════════════════════
def baseB = "/tmp/zos-sim-sfil-b-${System.currentTimeMillis()}"
def opsB  = new LocalFileOps(baseB)

def deleteLogicB = new DeleteCassaforteLogic(ops: opsB, rules: rules, buildMap: buildMap)
def sfilB        = new SfilamentoLogic(ops: opsB, deleteLogic: deleteLogicB, rules: rules)

// Seed PGMCOBOL in ST's COB library (stage S1)
def copyCobLib    = "LTM00.D9PS1.PE000.LING.COB@@@@@.@@.COPY"
def copyCobMember = Paths.get(baseB, copyCobLib, "PGMCOBOL")
Files.createDirectories(copyCobMember.parent)
Files.writeString(copyCobMember, "cobol-content")

assert opsB.exists("//${copyCobLib}(PGMCOBOL)") : "TestB: PGMCOBOL should exist before execute"

def resultB = sfilB.execute(
    '/dbb/DEE/IBM/yn_r_01_st_r1/src/cobol/batch/pgmcobol.cbl',
    'ACPYCOB ',
    'ST', '', 'yn_r_01_st_r1'
)

assert resultB == false         : "TestB: execute should return false for non-JCL, got ${resultB}"
assert !opsB.exists("//${copyCobLib}(PGMCOBOL)") : "TestB: PGMCOBOL should be deleted"

new File(baseB).deleteDir()

println "TestSfilamentoLogic: PASS"
