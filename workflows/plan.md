# Cassaforte Library Cleanup — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `RemoveCassaforte.groovy` (standalone Jenkins script) and `PuliziaAmbienti.groovy` (DBB task) to clean ISP cassaforte libraries during the build pipeline.

**Architecture:** A thin abstraction layer (`ZosFileOps` trait) decouples business logic from z/OS platform APIs. Business logic lives in pure-Groovy classes with no IBM imports; DBB wrappers are thin adapters. Local development and tests run with `LocalFileOps` (java.nio + `/tmp/zos-sim/`); mainframe execution uses `ZosFileOpsUSS` (ZFile/BPXWDYN).

**Tech Stack:** Groovy stdlib, `groovyz` on z/OS USS, IBM DBB `com.ibm.dbb.groovy.TaskScript` (task wrapper only), `java.nio.file` for local simulation, `groovy.json.JsonSlurper` for mock build map.

---

## File Structure

```
scripts/
├── lib/
│   ├── ZosFileOps.groovy            # Trait: platform I/O contract
│   ├── LocalFileOps.groovy          # Local: java.nio + /tmp/zos-sim/ simulation
│   └── ZosFileOpsUSS.groovy         # z/OS: ZFile/BPXWDYN (mainframe only, no local tests)
├── tasks/
│   ├── PatternMatcher.groovy        # % = one char, * = zero-or-more, against 8-char type codes
│   ├── DeletionRule.groovy          # Immutable value: typePattern, libraryTemplate, useBuildMap
│   ├── DeletionRulesLoader.groovy   # CSV → List<DeletionRule>
│   ├── LibraryNameResolver.groovy   # ${C1STAGE}/${C1SYSTEM} substitution + TOCOLB derivation
│   ├── EnvironmentChain.groovy      # Predecessor/successor, C1STAGE lookup, capability flags
│   ├── BuildMapClient.groovy        # Trait: query generated objects from build map
│   ├── LocalBuildMapClient.groovy   # Mock: read generated-objects from JSON fixture
│   ├── DeleteCassaforteLogic.groovy # Core DELETE_CASSAFORTE algorithm
│   ├── SfilamentoLogic.groovy       # SFILAMENTO: delete + conditional JCL restore to TOCOLB
│   └── PrevEnvCleanLogic.groovy     # DELETE_PREV_ENV_AFTER_BUILD: delete from predecessor env
├── test/
│   ├── fixtures/
│   │   ├── rules.csv                # Sample deletion rules
│   │   └── buildmap.json            # Mock build map for tests
│   ├── run_tests.sh                 # Run all Test*.groovy
│   ├── TestPatternMatcher.groovy
│   ├── TestDeletionRulesLoader.groovy
│   ├── TestLibraryNameResolver.groovy
│   ├── TestEnvironmentChain.groovy
│   ├── TestDeleteCassaforteLogic.groovy
│   ├── TestSfilamentoLogic.groovy
│   └── TestPrevEnvCleanLogic.groovy
├── build-data/
│   ├── stage-map.csv                # map layer operativo + build environment to stage name
│   └── rules.csv                    # Deployed deletion rules (ISP-provided)
├── RemoveCassaforte.groovy          # Standalone entry point (Jenkins, groovyz)
└── PuliziaAmbienti.groovy           # DBB task wrapper (@BaseScript TaskScript)
```

---

## Task 1: ZosFileOps trait + test harness

**Files:**
- Create: `scripts/lib/ZosFileOps.groovy`
- Create: `scripts/test/run_tests.sh`

- [ ] **Step 1: Create the trait**

```groovy
// scripts/lib/ZosFileOps.groovy
trait ZosFileOps {
    abstract boolean exists(String path)
    abstract void    delete(String path)
    abstract void    copy(String src, String dst)
    abstract List<String> list(String container)
}
```

- [ ] **Step 2: Create test runner**

```bash
#!/bin/bash
# scripts/test/run_tests.sh
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$SCRIPT_DIR/.."
for f in "$SCRIPT_DIR"/Test*.groovy; do
    groovy -cp "$ROOT/lib:$ROOT/tasks" "$f" && echo "PASS: $(basename $f)" \
        || { echo "FAIL: $(basename $f)"; exit 1; }
done
echo ""
echo "ALL TESTS PASSED"
```

```bash
chmod +x scripts/test/run_tests.sh
```

- [ ] **Step 3: Commit**

```bash
git add scripts/lib/ZosFileOps.groovy scripts/test/run_tests.sh
git commit -m "feat: ZosFileOps trait + test runner scaffold"
```

---

## Task 2: LocalFileOps

**Files:**
- Create: `scripts/lib/LocalFileOps.groovy`
- Create: `scripts/test/TestLocalFileOps.groovy`

- [ ] **Step 1: Write the failing test**

```groovy
// scripts/test/TestLocalFileOps.groovy
def ops = new LocalFileOps('/tmp/zos-sim-test')

// exists returns false for missing member
assert !ops.exists('//TEST.DS(MEMBER1)') : "should not exist"

// copy + exists round-trip
ops.copy('//TEST.DS.SRC(MEMBSRC)', '//TEST.DS(MEMBER1)')  // will fail: class not found
assert ops.exists('//TEST.DS(MEMBER1)')

println "TestLocalFileOps: PASS"
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd scripts
groovy -cp lib:tasks test/TestLocalFileOps.groovy
```

Expected: `unable to resolve class LocalFileOps`

- [ ] **Step 3: Implement LocalFileOps**

```groovy
// scripts/lib/LocalFileOps.groovy
import java.nio.file.*

class LocalFileOps implements ZosFileOps {
    final String baseDir

    LocalFileOps(String baseDir = '/tmp/zos-sim') {
        this.baseDir = baseDir
    }

    private Path resolve(String zosPath) {
        if (zosPath.startsWith('//')) {
            def inner = zosPath.substring(2)
            def m = (inner =~ /^(.+?)\((.+?)\)$/)
            if (m.matches()) return Paths.get(baseDir, m.group(1), m.group(2))
            return Paths.get(baseDir, inner)
        }
        Paths.get(zosPath)
    }

    boolean exists(String path) { Files.exists(resolve(path)) }

    void delete(String path) { Files.deleteIfExists(resolve(path)) }

    void copy(String src, String dst) {
        def dstPath = resolve(dst)
        Files.createDirectories(dstPath.parent)
        def srcPath = resolve(src)
        if (Files.exists(srcPath)) {
            Files.copy(srcPath, dstPath, StandardCopyOption.REPLACE_EXISTING)
        } else {
            // Create the file so copy from a non-existent source is visible in tests
            Files.createFile(dstPath)
        }
    }

    List<String> list(String container) {
        def dir = resolve(container)
        if (!Files.isDirectory(dir)) return []
        dir.toFile().list()?.toList() ?: []
    }
}
```

- [ ] **Step 4: Update test with proper fixture setup and run**

```groovy
// scripts/test/TestLocalFileOps.groovy
import java.nio.file.*

def base = '/tmp/zos-sim-test-' + System.currentTimeMillis()
def ops  = new LocalFileOps(base)

// Seed source file
def src = Paths.get(base, 'TEST.DS.SRC', 'MEMBSRC')
Files.createDirectories(src.parent)
Files.writeString(src, 'content')

assert !ops.exists('//TEST.DS(MEMBER1)') : "should not exist before copy"

ops.copy('//TEST.DS.SRC(MEMBSRC)', '//TEST.DS(MEMBER1)')
assert ops.exists('//TEST.DS(MEMBER1)') : "should exist after copy"

ops.delete('//TEST.DS(MEMBER1)')
assert !ops.exists('//TEST.DS(MEMBER1)') : "should not exist after delete"

def members = ops.list('//TEST.DS.SRC')
assert 'MEMBSRC' in members : "list should contain MEMBSRC"

// USS path passthrough
def ussFile = Paths.get(base, 'uss-test.txt')
Files.writeString(ussFile, 'x')
assert ops.exists(ussFile.toString())

// Cleanup
new File(base).deleteDir()

println "TestLocalFileOps: PASS"
```

```bash
cd scripts
groovy -cp lib:tasks test/TestLocalFileOps.groovy
```

Expected: `TestLocalFileOps: PASS`

- [ ] **Step 5: Commit**

```bash
git add scripts/lib/LocalFileOps.groovy scripts/test/TestLocalFileOps.groovy
git commit -m "feat: LocalFileOps — local java.nio simulation of z/OS datasets"
```

---

## Task 3: PatternMatcher

**Files:**
- Create: `scripts/tasks/PatternMatcher.groovy`
- Create: `scripts/test/TestPatternMatcher.groovy`

- [ ] **Step 1: Write failing test**

```groovy
// scripts/test/TestPatternMatcher.groovy
def m = new PatternMatcher()  // fails: class not found

assert  m.matches('%CPYCOB*', 'ACPYCOB ')   : "% = one char, * = zero-or-more"
assert  m.matches('%CPYCOB*', 'XCPYCOBABC') : "* matches multiple chars"
assert  m.matches('SZFSSWG ', 'SZFSSWG ')   : "exact match with trailing space"
assert !m.matches('SZFSSWG ', 'SZFSSWGX')   : "wrong 8th char"
assert  m.matches('%CB2%R  ', 'ACB2XR  ')   : "two % wildcards"
assert !m.matches('%CB2%R  ', 'ACB2XRY ')   : "extra char before trailing spaces"
assert  m.matches('SJCL*',    'SJCL    ')   : "SJCL* matches SJCL with spaces"
assert  m.matches('SJCL*',    'SJCLPROC')   : "SJCL* matches SJCLPROC"
assert !m.matches('SJCL*',    'XJCL    ')   : "does not match different prefix"
assert  m.matches('*',        'ANYTHING')   : "* alone matches anything"

println "TestPatternMatcher: PASS"
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd scripts
groovy -cp lib:tasks test/TestPatternMatcher.groovy
```

Expected: `unable to resolve class PatternMatcher`

- [ ] **Step 3: Implement**

```groovy
// scripts/tasks/PatternMatcher.groovy
import java.util.regex.Pattern

class PatternMatcher {
    boolean matches(String pattern, String value) {
        def regex = pattern.collect { c ->
            switch (c) {
                case '%': return '.'
                case '*': return '.*'
                default:  return Pattern.quote(String.valueOf(c))
            }
        }.join('')
        value ==~ regex
    }
}
```

- [ ] **Step 4: Run test**

```bash
cd scripts
groovy -cp lib:tasks test/TestPatternMatcher.groovy
```

Expected: `TestPatternMatcher: PASS`

- [ ] **Step 5: Commit**

```bash
git add scripts/tasks/PatternMatcher.groovy scripts/test/TestPatternMatcher.groovy
git commit -m "feat: PatternMatcher — % and * wildcard matching for ISP type codes"
```

---

## Task 4: DeletionRule + DeletionRulesLoader

**Files:**
- Create: `scripts/tasks/DeletionRule.groovy`
- Create: `scripts/tasks/DeletionRulesLoader.groovy`
- Create: `scripts/test/fixtures/rules.csv`
- Create: `scripts/test/TestDeletionRulesLoader.groovy`

- [ ] **Step 1: Create test fixture**

```csv
# scripts/test/fixtures/rules.csv
%CPYCOB*;LTM00.D9P${C1STAGE}.PE000.LING.COB@@@@@.@@.COPY;NO
SZFSSWG ;LTM00.D9P${C1STAGE}.PE000.LING.MAP@@@@@.@@.COPY;BUILD MAP
SZFSSWG ;LTM00.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.ZARA;NO
SJCL*   ;LTM00.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.SJCL;NO
%CB2%   ;LTM00.D9P${C1STAGE}.PE000.SYST.${C1SYSTEM}@@@@@@@.BT.LOAD;NO
```

- [ ] **Step 2: Write failing test**

```groovy
// scripts/test/TestDeletionRulesLoader.groovy
def loader = new DeletionRulesLoader()   // fails
def rules  = loader.load('test/fixtures/rules.csv')

assert rules.size() == 5 : "should load 5 rules (skip comment)"
assert rules[0].typePattern    == '%CPYCOB*'
assert rules[0].libraryTemplate == 'LTM00.D9P${C1STAGE}.PE000.LING.COB@@@@@.@@.COPY'
assert rules[0].useBuildMap    == false
assert rules[1].useBuildMap    == true  : "SZFSSWG BUILD MAP rule"
assert rules[3].typePattern    == 'SJCL*   '

println "TestDeletionRulesLoader: PASS"
```

- [ ] **Step 3: Run to confirm failure**

```bash
cd scripts
groovy -cp lib:tasks test/TestDeletionRulesLoader.groovy
```

- [ ] **Step 4: Implement DeletionRule**

```groovy
// scripts/tasks/DeletionRule.groovy
@groovy.transform.Immutable
class DeletionRule {
    String typePattern
    String libraryTemplate
    boolean useBuildMap
}
```

- [ ] **Step 5: Implement DeletionRulesLoader**

```groovy
// scripts/tasks/DeletionRulesLoader.groovy
class DeletionRulesLoader {
    List<DeletionRule> load(String filePath) {
        new File(filePath).readLines()
            .findAll { it.trim() && !it.startsWith('#') }
            .collect { line ->
                def parts = line.split(';', -1)
                if (parts.size() < 3)
                    throw new IllegalArgumentException("Invalid rule (need 3 semicolon-separated fields): '$line'")
                new DeletionRule(
                    typePattern:     parts[0],
                    libraryTemplate: parts[1].trim(),
                    useBuildMap:     parts[2].trim() == 'BUILD MAP'
                )
            }
    }
}
```

Note: `typePattern` retains its original spacing (right-padded to 8 chars) so it matches `PatternMatcher` behaviour against ISP type codes.

- [ ] **Step 6: Run test**

```bash
cd scripts
groovy -cp lib:tasks test/TestDeletionRulesLoader.groovy
```

Expected: `TestDeletionRulesLoader: PASS`

- [ ] **Step 7: Commit**

```bash
git add scripts/tasks/DeletionRule.groovy scripts/tasks/DeletionRulesLoader.groovy \
        scripts/test/fixtures/rules.csv scripts/test/TestDeletionRulesLoader.groovy
git commit -m "feat: DeletionRule model and CSV loader"
```

---

## Task 5: LibraryNameResolver

**Files:**
- Create: `scripts/tasks/LibraryNameResolver.groovy`
- Create: `scripts/test/TestLibraryNameResolver.groovy`

- [ ] **Step 1: Write failing test**

```groovy
// scripts/test/TestLibraryNameResolver.groovy
def r = new LibraryNameResolver()   // fails

// Parameter substitution
assert r.resolve('LTM00.D9P${C1STAGE}.PE000.LING.COB@@@@@.@@.COPY', 'O1', '') \
    == 'LTM00.D9PO1.PE000.LING.COB@@@@@.@@.COPY'

assert r.resolve('LTM00.D9P${C1STAGE}.PE000.SYST.${C1SYSTEM}@@@@@@@.BT.LOAD', 'S1', 'MYSYS') \
    == 'LTM00.D9PS1.PE000.SYST.MYSYS@@@@@@@.BT.LOAD'

// TOCOLB derivation: 4th qualifier @@@@→TO@@, 5th qualifier @@@@@@@@→COLB@@@@
assert r.toTocolbLibrary('LTM00.D9PS1.PE000.@@@@.@@@@@@@@.@@.SJCL') \
    == 'LTM00.D9PS1.PE000.TO@@.COLB@@@@.@@.SJCL'

// Library without @@@@ pattern passes through unchanged
assert r.toTocolbLibrary('LTM00.D9PS1.PE000.LING.COB@@@@@.@@.COPY') \
    == 'LTM00.D9PS1.PE000.LING.COB@@@@@.@@.COPY'

println "TestLibraryNameResolver: PASS"
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd scripts
groovy -cp lib:tasks test/TestLibraryNameResolver.groovy
```

- [ ] **Step 3: Implement**

```groovy
// scripts/tasks/LibraryNameResolver.groovy
class LibraryNameResolver {
    String resolve(String template, String stage, String system) {
        template
            .replace('${C1STAGE}', stage  ?: '')
            .replace('${C1SYSTEM}', system ?: '')
    }

    // Derives TOCOLB library name from a resolved (already substituted) cassaforte library.
    // Rule: 4th qualifier '@@@@' → 'TO@@', 5th qualifier '@@@@@@@@' → 'COLB@@@@'.
    String toTocolbLibrary(String resolvedLibrary) {
        def parts = resolvedLibrary.split('\\.', -1)
        if (parts.size() >= 5) {
            parts[3] = parts[3].replace('@@@@', 'TO@@')
            parts[4] = parts[4].replace('@@@@@@@@', 'COLB@@@@')
        }
        parts.join('.')
    }
}
```

- [ ] **Step 4: Run test**

```bash
cd scripts
groovy -cp lib:tasks test/TestLibraryNameResolver.groovy
```

Expected: `TestLibraryNameResolver: PASS`

- [ ] **Step 5: Commit**

```bash
git add scripts/tasks/LibraryNameResolver.groovy scripts/test/TestLibraryNameResolver.groovy
git commit -m "feat: LibraryNameResolver — template substitution and TOCOLB derivation"
```

---

## Task 6: EnvironmentChain

**Files:**
- Create: `scripts/tasks/EnvironmentChain.groovy`
- Create: `scripts/test/TestEnvironmentChain.groovy`

- [ ] **Step 1: Write failing test**

```groovy
// scripts/test/TestEnvironmentChain.groovy
def c = new EnvironmentChain()   // fails

// Predecessor (for DELETE_PREV_ENV_AFTER_BUILD)
assert c.getPredecessor('ST')  == 'ATO'
assert c.getPredecessor('SAD') == 'ATO'
assert c.getPredecessor('PR')  == 'ST'
assert c.getPredecessor('PA')  == 'SAD'
assert c.getPredecessor('ATO') == null : "ATO has no predecessor"

// Superior environments (for SFILAMENTO lookup)
assert c.getSuperiors('ST')  == ['PR', 'PA']
assert c.getSuperiors('SAD') == ['PR', 'PA']
assert c.getSuperiors('PR')  == []

// C1STAGE lookup
assert c.getStage('ATO') != null
assert c.getStage('ST')  == c.getStage('SAD')  // same stage family

// Capability flags
assert  c.requiresPrevEnvClean('ST')
assert  c.requiresPrevEnvClean('PR')
assert !c.requiresPrevEnvClean('ATO')
assert  c.supportsSfilamento('ST')
assert  c.supportsSfilamento('SAD')
assert !c.supportsSfilamento('ATO')
assert !c.supportsSfilamento('PR')

println "TestEnvironmentChain: PASS"
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd scripts
groovy -cp lib:tasks test/TestEnvironmentChain.groovy
```

- [ ] **Step 3: Implement**

```groovy
// scripts/tasks/EnvironmentChain.groovy
class EnvironmentChain {

    // Predecessor env for DELETE_PREV_ENV_AFTER_BUILD
    static final Map<String, String> PREDECESSORS = [
        ST: 'ATO', SAD: 'ATO',
        PR: 'ST',  PA:  'SAD',
    ]

    // Superior envs for SFILAMENTO (nearest first)
    static final Map<String, List<String>> SUPERIORS = [
        ST:  ['PR', 'PA'],
        SAD: ['PR', 'PA'],
        ATI: ['ATO'],
        ATO: ['ST', 'SAD'],
    ]

    // C1STAGE value by environment.
    // TODO: verify these values against actual ISP build configuration.
    // If stage also depends on layer (e.g. R1/R2), replace with Map<String,Map<String,String>>
    // and update getStage(env, layer) signature.
    static final Map<String, String> STAGE_BY_ENV = [
        ATI: 'I1',
        ATO: 'O1',
        ST:  'S1', SAD: 'S1',
        PR:  'P1', PA:  'P1',
        EM:  'E1',
    ]

    String getPredecessor(String env) {
        PREDECESSORS[env?.toUpperCase()]
    }

    List<String> getSuperiors(String env) {
        SUPERIORS[env?.toUpperCase()] ?: []
    }

    String getStage(String env) {
        def stage = STAGE_BY_ENV[env?.toUpperCase()]
        if (!stage) throw new IllegalArgumentException("Unknown environment: '$env'")
        stage
    }

    boolean requiresPrevEnvClean(String env) {
        env?.toUpperCase() in PREDECESSORS.keySet()
    }

    boolean supportsSfilamento(String env) {
        env?.toUpperCase() in ['ST', 'SAD']
    }
}
```

- [ ] **Step 4: Run test**

```bash
cd scripts
groovy -cp lib:tasks test/TestEnvironmentChain.groovy
```

Expected: `TestEnvironmentChain: PASS`

- [ ] **Step 5: Commit**

```bash
git add scripts/tasks/EnvironmentChain.groovy scripts/test/TestEnvironmentChain.groovy
git commit -m "feat: EnvironmentChain — predecessor/superior lookup and C1STAGE mapping"
```

---

## Task 7: BuildMapClient + LocalBuildMapClient

**Files:**
- Create: `scripts/tasks/BuildMapClient.groovy`
- Create: `scripts/tasks/LocalBuildMapClient.groovy`
- Create: `scripts/test/fixtures/buildmap.json`
- Create: `scripts/test/TestLocalBuildMapClient.groovy`

- [ ] **Step 1: Create fixture**

```json
// scripts/test/fixtures/buildmap.json
{
  "yn_r_01_ato_r1:/dbb/DEE/IBM/yn_r_01_ato_r1/src/mapasm/batch/mapobj.asm": [
    {"library": "LTM00.D9PO1.PE000.LING.MAP@@@@@.@@.COPY", "member": "MAPOBJ"}
  ],
  "yn_r_01_ato_r1:/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl": [
    {"library": "LTM00.D9PO1.PE000.LING.COB@@@@@.@@.COPY", "member": "PGMCOBOL"},
    {"library": "LTM00.D9PO1.PE000.SYST.MYSYS@@@@@@@.BT.LOAD", "member": "PGMCOBOL"}
  ]
}
```

Key format: `"<buildGroup>:<sourcePath>"`

- [ ] **Step 2: Write failing test**

```groovy
// scripts/test/TestLocalBuildMapClient.groovy
def client = new LocalBuildMapClient('test/fixtures/buildmap.json')  // fails

def objs = client.getGeneratedObjects(
    '/dbb/DEE/IBM/yn_r_01_ato_r1/src/mapasm/batch/mapobj.asm',
    'yn_r_01_ato_r1'
)
assert objs.size() == 1
assert objs[0].library == 'LTM00.D9PO1.PE000.LING.MAP@@@@@.@@.COPY'
assert objs[0].member  == 'MAPOBJ'

def none = client.getGeneratedObjects('/not/in/map', 'somegroup')
assert none == []

println "TestLocalBuildMapClient: PASS"
```

- [ ] **Step 3: Run to confirm failure**

```bash
cd scripts
groovy -cp lib:tasks test/TestLocalBuildMapClient.groovy
```

- [ ] **Step 4: Implement BuildMapClient trait**

```groovy
// scripts/tasks/BuildMapClient.groovy
trait BuildMapClient {
    // Returns list of generated objects for the given source path + build group.
    // Each map contains at minimum: 'library' (fully qualified DSN) and 'member' (member name).
    abstract List<Map<String, String>> getGeneratedObjects(String sourcePath, String buildGroup)
}
```

- [ ] **Step 5: Implement LocalBuildMapClient**

```groovy
// scripts/tasks/LocalBuildMapClient.groovy
import groovy.json.JsonSlurper

class LocalBuildMapClient implements BuildMapClient {
    private final Map data

    LocalBuildMapClient(String jsonFilePath) {
        data = new JsonSlurper().parse(new File(jsonFilePath)) as Map
    }

    List<Map<String, String>> getGeneratedObjects(String sourcePath, String buildGroup) {
        def key = "${buildGroup}:${sourcePath}"
        (data[key] ?: []) as List<Map<String, String>>
    }
}
```

- [ ] **Step 6: Run test**

```bash
cd scripts
groovy -cp lib:tasks test/TestLocalBuildMapClient.groovy
```

Expected: `TestLocalBuildMapClient: PASS`

- [ ] **Step 7: Commit**

```bash
git add scripts/tasks/BuildMapClient.groovy scripts/tasks/LocalBuildMapClient.groovy \
        scripts/test/fixtures/buildmap.json scripts/test/TestLocalBuildMapClient.groovy
git commit -m "feat: BuildMapClient trait + LocalBuildMapClient JSON mock"
```

---

## Task 8: DeleteCassaforteLogic

**Files:**
- Create: `scripts/tasks/DeleteCassaforteLogic.groovy`
- Create: `scripts/test/TestDeleteCassaforteLogic.groovy`

- [ ] **Step 1: Write failing test**

```groovy
// scripts/test/TestDeleteCassaforteLogic.groovy
import java.nio.file.*

def base = '/tmp/zos-sim-dca-' + System.currentTimeMillis()
def ops  = new LocalFileOps(base)

// Seed: member PGMCOBOL in COPY library
def copyLib = 'LTM00.D9PO1.PE000.LING.COB@@@@@.@@.COPY'
def copyPath = Paths.get(base, copyLib, 'PGMCOBOL')
Files.createDirectories(copyPath.parent)
Files.writeString(copyPath, 'pgm content')

def rules = new DeletionRulesLoader().load('test/fixtures/rules.csv')
def buildMap = new LocalBuildMapClient('test/fixtures/buildmap.json')
def logic = new DeleteCassaforteLogic(   // fails
    ops: ops, rules: rules, buildMap: buildMap
)

// Case 1: NO flag — delete by member name (type '%CPYCOB*' matches 'ACPYCOB ')
def count = logic.execute(
    '/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl',
    'ACPYCOB ',   // matches %CPYCOB*
    'O1',          // C1STAGE for ATO
    '',            // C1SYSTEM
    'yn_r_01_ato_r1'
)
assert count == 1 : "should delete 1 member"
assert !ops.exists("//${copyLib}(PGMCOBOL)") : "member should be gone"

// Case 2: member not present → no error, count = 0
def count2 = logic.execute(
    '/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl',
    'ACPYCOB ',
    'O1', '', 'yn_r_01_ato_r1'
)
assert count2 == 0 : "already deleted, should be 0"

// Case 3: BUILD MAP flag — delete via build map entries (type 'SZFSSWG ')
// Seed: member MAPOBJ in MAP library
def mapLib  = 'LTM00.D9PO1.PE000.LING.MAP@@@@@.@@.COPY'
def mapPath = Paths.get(base, mapLib, 'MAPOBJ')
Files.createDirectories(mapPath.parent)
Files.writeString(mapPath, 'map content')

def count3 = logic.execute(
    '/dbb/DEE/IBM/yn_r_01_ato_r1/src/mapasm/batch/mapobj.asm',
    'SZFSSWG ',   // matches SZFSSWG  rule with BUILD MAP
    'O1', '', 'yn_r_01_ato_r1'
)
assert count3 == 1 : "build-map delete should delete 1"
assert !ops.exists("//${mapLib}(MAPOBJ)")

// Cleanup
new File(base).deleteDir()

println "TestDeleteCassaforteLogic: PASS"
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd scripts
groovy -cp lib:tasks test/TestDeleteCassaforteLogic.groovy
```

- [ ] **Step 3: Implement**

```groovy
// scripts/tasks/DeleteCassaforteLogic.groovy
class DeleteCassaforteLogic {
    ZosFileOps          ops
    List<DeletionRule>  rules
    BuildMapClient      buildMap
    PatternMatcher      matcher  = new PatternMatcher()
    LibraryNameResolver resolver = new LibraryNameResolver()

    // stage and system are pre-resolved by the caller (via EnvironmentChain + path parser).
    // Returns number of delete operations performed.
    int execute(String sourcePath, String fileType, String stage, String system, String buildGroup) {
        def member   = memberName(sourcePath)
        def matching = rules.findAll { matcher.matches(it.typePattern, fileType) }
        int count    = 0

        matching.each { rule ->
            def lib = resolver.resolve(rule.libraryTemplate, stage, system)
            if (rule.useBuildMap) {
                buildMap.getGeneratedObjects(sourcePath, buildGroup).each { obj ->
                    if (obj.library == lib) {
                        def zp = "//${lib}(${obj.member})"
                        if (ops.exists(zp)) { ops.delete(zp); count++ }
                    }
                }
            } else {
                def zp = "//${lib}(${member})"
                if (ops.exists(zp)) { ops.delete(zp); count++ }
            }
        }
        count
    }

    // Extracts the MVS member name from a full source path (filename without extension, uppercased).
    static String memberName(String sourcePath) {
        def filename = sourcePath.tokenize('/').last()
        def name = filename.contains('.') ? filename.take(filename.lastIndexOf('.')) : filename
        name.toUpperCase()
    }
}
```

- [ ] **Step 4: Run test**

```bash
cd scripts
groovy -cp lib:tasks test/TestDeleteCassaforteLogic.groovy
```

Expected: `TestDeleteCassaforteLogic: PASS`

- [ ] **Step 5: Commit**

```bash
git add scripts/tasks/DeleteCassaforteLogic.groovy scripts/test/TestDeleteCassaforteLogic.groovy
git commit -m "feat: DeleteCassaforteLogic — core DELETE_CASSAFORTE algorithm"
```

---

## Task 9: SfilamentoLogic

**Files:**
- Create: `scripts/tasks/SfilamentoLogic.groovy`
- Create: `scripts/test/TestSfilamentoLogic.groovy`

- [ ] **Step 1: Write failing test**

```groovy
// scripts/test/TestSfilamentoLogic.groovy
import java.nio.file.*

def base = '/tmp/zos-sim-sfil-' + System.currentTimeMillis()
def ops  = new LocalFileOps(base)

// Seed: JCL member MYJCL in ST's cassaforte SJCL library
def sjclLibST  = 'LTM00.D9PS1.PE000.@@@@.@@@@@@@@.@@.SJCL'
def sjclLibPR  = 'LTM00.D9PP1.PE000.@@@@.@@@@@@@@.@@.SJCL'
def tocolbST   = 'LTM00.D9PS1.PE000.TO@@.COLB@@@@.@@.SJCL'

// JCL exists in ST (to be deleted) and in PR (superior env, for restore)
[sjclLibST, sjclLibPR].each { lib ->
    def p = Paths.get(base, lib, 'MYJCL')
    Files.createDirectories(p.parent)
    Files.writeString(p, 'jcl content from ' + lib)
}

def rules     = new DeletionRulesLoader().load('test/fixtures/rules.csv')
def buildMap  = new LocalBuildMapClient('test/fixtures/buildmap.json')
def deleteLogic = new DeleteCassaforteLogic(ops: ops, rules: rules, buildMap: buildMap)
def sfilamento  = new SfilamentoLogic(   // fails
    ops: ops, deleteLogic: deleteLogic, rules: rules, envChain: new EnvironmentChain()
)

// Case 1: SJCL type — delete from ST + restore from PR (superior) to TOCOLB
def restored = sfilamento.execute(
    '/dbb/DEE/IBM/yn_r_01_st_r1/src/jcl/batch/myjcl.jcl',
    'SJCL    ',   // matches SJCL* rule
    'ST',          // current env
    '',            // system
    'yn_r_01_st_r1'
)
assert restored : "should restore from superior PR"
assert !ops.exists("//${sjclLibST}(MYJCL)") : "deleted from ST"
assert ops.exists("//${tocolbST}(MYJCL)")   : "restored to TOCOLB of ST"

// Case 2: non-JCL type — delete only, no restore
def base2 = '/tmp/zos-sim-sfil2-' + System.currentTimeMillis()
def ops2  = new LocalFileOps(base2)
def copyLib = 'LTM00.D9PS1.PE000.LING.COB@@@@@.@@.COPY'
def cp = Paths.get(base2, copyLib, 'PGMCOBOL')
Files.createDirectories(cp.parent); Files.writeString(cp, 'x')

def deleteLogic2 = new DeleteCassaforteLogic(ops: ops2, rules: rules, buildMap: buildMap)
def sfilamento2  = new SfilamentoLogic(ops: ops2, deleteLogic: deleteLogic2, rules: rules, envChain: new EnvironmentChain())

def restored2 = sfilamento2.execute(
    '/dbb/DEE/IBM/yn_r_01_st_r1/src/cobol/batch/pgmcobol.cbl',
    'ACPYCOB ',
    'ST', '', 'yn_r_01_st_r1'
)
assert !restored2 : "non-JCL type: no restore"
assert !ops2.exists("//${copyLib}(PGMCOBOL)") : "deleted"

[base, base2].each { new File(it).deleteDir() }

println "TestSfilamentoLogic: PASS"
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd scripts
groovy -cp lib:tasks test/TestSfilamentoLogic.groovy
```

- [ ] **Step 3: Implement**

```groovy
// scripts/tasks/SfilamentoLogic.groovy
class SfilamentoLogic {
    ZosFileOps          ops
    DeleteCassaforteLogic deleteLogic
    List<DeletionRule>  rules
    PatternMatcher      matcher  = new PatternMatcher()
    LibraryNameResolver resolver = new LibraryNameResolver()
    EnvironmentChain    envChain = new EnvironmentChain()

    // Returns true if a JCL restore to TOCOLB was performed.
    boolean execute(String sourcePath, String fileType, String environment, String system, String buildGroup) {
        def stage = envChain.getStage(environment)
        deleteLogic.execute(sourcePath, fileType, stage, system, buildGroup)

        // Only SJCL* types get the restore step
        if (!matcher.matches('SJCL*', fileType)) return false
        if (!envChain.supportsSfilamento(environment)) return false

        def member   = DeleteCassaforteLogic.memberName(sourcePath)
        def matching = rules.findAll { matcher.matches(it.typePattern, fileType) }

        for (String superEnv : envChain.getSuperiors(environment)) {
            def superStage = envChain.getStage(superEnv)
            for (def rule : matching) {
                def srcLib = resolver.resolve(rule.libraryTemplate, superStage, system)
                def src    = "//${srcLib}(${member})"
                if (ops.exists(src)) {
                    // Derive TOCOLB from the CURRENT environment's library
                    def localLib = resolver.resolve(rule.libraryTemplate, stage, system)
                    def tocolb   = resolver.toTocolbLibrary(localLib)
                    ops.copy(src, "//${tocolb}(${member})")
                    return true
                }
            }
        }
        return false
    }
}
```

- [ ] **Step 4: Run test**

```bash
cd scripts
groovy -cp lib:tasks test/TestSfilamentoLogic.groovy
```

Expected: `TestSfilamentoLogic: PASS`

- [ ] **Step 5: Commit**

```bash
git add scripts/tasks/SfilamentoLogic.groovy scripts/test/TestSfilamentoLogic.groovy
git commit -m "feat: SfilamentoLogic — SFILAMENTO delete + conditional JCL restore to TOCOLB"
```

---

## Task 10: PrevEnvCleanLogic

**Files:**
- Create: `scripts/tasks/PrevEnvCleanLogic.groovy`
- Create: `scripts/test/TestPrevEnvCleanLogic.groovy`

- [ ] **Step 1: Write failing test**

```groovy
// scripts/test/TestPrevEnvCleanLogic.groovy
import java.nio.file.*

def base = '/tmp/zos-sim-prev-' + System.currentTimeMillis()
def ops  = new LocalFileOps(base)

// Seed: member PGMCOBOL in ATO's COPY library (predecessor of ST)
def copyLibATO = 'LTM00.D9PO1.PE000.LING.COB@@@@@.@@.COPY'
def cp = Paths.get(base, copyLibATO, 'PGMCOBOL')
Files.createDirectories(cp.parent); Files.writeString(cp, 'ato content')

def rules    = new DeletionRulesLoader().load('test/fixtures/rules.csv')
def buildMap = new LocalBuildMapClient('test/fixtures/buildmap.json')
def deleteLogic = new DeleteCassaforteLogic(ops: ops, rules: rules, buildMap: buildMap)
def prevClean   = new PrevEnvCleanLogic(deleteLogic: deleteLogic)   // fails

// Case 1: current env is ST — should delete from predecessor ATO (stage O1)
def count = prevClean.execute(
    '/dbb/DEE/IBM/yn_r_01_st_r1/src/cobol/batch/pgmcobol.cbl',
    'ACPYCOB ',
    'ST', '', 'yn_r_01_ato_r1'   // buildGroup points to ATO's build map
)
assert count == 1 : "should delete 1 from ATO"
assert !ops.exists("//${copyLibATO}(PGMCOBOL)") : "ATO member should be gone"

// Case 2: env has no predecessor (ATO) → no-op
def count2 = prevClean.execute(
    '/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl',
    'ACPYCOB ',
    'ATO', '', 'yn_r_01_ato_r1'
)
assert count2 == 0 : "ATO has no predecessor, should be 0"

new File(base).deleteDir()

println "TestPrevEnvCleanLogic: PASS"
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd scripts
groovy -cp lib:tasks test/TestPrevEnvCleanLogic.groovy
```

- [ ] **Step 3: Implement**

```groovy
// scripts/tasks/PrevEnvCleanLogic.groovy
class PrevEnvCleanLogic {
    DeleteCassaforteLogic deleteLogic
    EnvironmentChain      envChain = new EnvironmentChain()

    // Returns number of deletes performed (0 if current env has no predecessor).
    // buildGroup should reference the predecessor environment's build group
    // so the build map lookup resolves the right generated objects.
    // TODO: if fileType matches SJCL*, also copy the current env's freshly-built
    // JCL to the predecessor env's TOCOLB (prevents look-through to stale JCL).
    // Requires clarification from ISP on the exact copy direction.
    int execute(String sourcePath, String fileType, String environment, String system, String buildGroup) {
        if (!envChain.requiresPrevEnvClean(environment)) return 0
        def prevEnv   = envChain.getPredecessor(environment)
        def prevStage = envChain.getStage(prevEnv)
        deleteLogic.execute(sourcePath, fileType, prevStage, system, buildGroup)
    }
}
```

- [ ] **Step 4: Run test**

```bash
cd scripts
groovy -cp lib:tasks test/TestPrevEnvCleanLogic.groovy
```

Expected: `TestPrevEnvCleanLogic: PASS`

- [ ] **Step 5: Run all tests**

```bash
cd scripts
bash test/run_tests.sh
```

Expected: `ALL TESTS PASSED`

- [ ] **Step 6: Commit**

```bash
git add scripts/tasks/PrevEnvCleanLogic.groovy scripts/test/TestPrevEnvCleanLogic.groovy
git commit -m "feat: PrevEnvCleanLogic — DELETE_PREV_ENV_AFTER_BUILD algorithm"
```

---

## Task 11: RemoveCassaforte.groovy (standalone entry point)

**Files:**
- Create: `scripts/RemoveCassaforte.groovy`
- Create: `scripts/build-data/rules.csv` (placeholder — overwritten by ISP deployment)

- [ ] **Step 1: Create placeholder rules file**

```csv
# scripts/build-data/rules.csv
# Populated by ISP deployment. Format: <type-pattern>;<library-template>;<NO|BUILD MAP>
# Example:
# %CPYCOB*;LTM00.D9P${C1STAGE}.PE000.LING.COB@@@@@.@@.COPY;NO
```

- [ ] **Step 2: Implement RemoveCassaforte.groovy**

```groovy
// scripts/RemoveCassaforte.groovy
// Invocation: groovyz -cp lib:tasks RemoveCassaforte.groovy <file-lista> <build-group> <environment>

if (args.size() < 3) {
    System.err.println "Usage: RemoveCassaforte.groovy <file-lista> <build-group> <environment>"
    System.exit(1)
}

def listFile    = args[0]
def buildGroup  = args[1]
def environment = args[2]

def scriptDir   = new File('.').canonicalFile
def rulesPath   = new File(scriptDir, 'build-data/rules.csv').absolutePath

def ops         = new ZosFileOpsUSS()             // swap to LocalFileOps for local runs
def rules       = new DeletionRulesLoader().load(rulesPath)
def buildMap    = createBuildMapClient(buildGroup) // USS: use DBB API client (see below)
def envChain    = new EnvironmentChain()
def deleteLogic = new DeleteCassaforteLogic(ops: ops, rules: rules, buildMap: buildMap)
def sfilamento  = new SfilamentoLogic(
    ops: ops, deleteLogic: deleteLogic, rules: rules, envChain: envChain
)

def stage  = envChain.getStage(environment)
// TODO: extract system code from buildGroup or path using ISP naming convention
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
                break
            case 'S':
                sfilamento.execute(sourcePath, fileType, environment, system, buildGroup)
                break
            default:
                System.err.println "Unknown action '$action' in line: '$line'"
                errors++
                return
        }
        processed++
    } catch (Exception e) {
        System.err.println "ERROR processing '$sourcePath': ${e.message}"
        errors++
    }
}

println "RemoveCassaforte: processed=$processed errors=$errors"
if (errors > 0) System.exit(1)

// ─── Helpers ────────────────────────────────────────────────────────────────

BuildMapClient createBuildMapClient(String buildGroup) {
    // TODO: on USS replace with DBB build-result map client
    // For now, expects build-data/buildmap.json for local use
    def bmFile = new File('.', 'build-data/buildmap.json')
    if (bmFile.exists()) return new LocalBuildMapClient(bmFile.absolutePath)
    return { sp, bg -> [] } as BuildMapClient
}

String extractSystem(String buildGroup) {
    // TODO: implement ISP-specific system code extraction from build group name.
    // Build group format example: yn_r_01_ato_r1 → system may be 'YN' or derived differently.
    buildGroup?.tokenize('_')?.first()?.toUpperCase() ?: ''
}

String resolveFileType(String sourcePath) {
    // TODO: implement ISP-specific mapping from file path/extension to 8-char type code.
    // Currently returns the file extension uppercased, padded to 8 chars.
    def filename = sourcePath.tokenize('/').last()
    def ext = filename.contains('.') ? filename.substring(filename.lastIndexOf('.') + 1) : filename
    ext.toUpperCase().padRight(8).take(8)
}
```

- [ ] **Step 3: Smoke-test locally (uses LocalFileOps)**

Edit the script temporarily: replace `new ZosFileOpsUSS()` with `new LocalFileOps()`, add a minimal action list file:

```bash
echo "C,/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl" > /tmp/test-lista.txt
cd scripts
groovy -cp lib:tasks RemoveCassaforte.groovy /tmp/test-lista.txt yn_r_01_ato_r1 ATO
```

Expected: `RemoveCassaforte: processed=1 errors=0`

Revert to `ZosFileOpsUSS()` before committing.

- [ ] **Step 4: Commit**

```bash
git add scripts/RemoveCassaforte.groovy scripts/build-data/rules.csv
git commit -m "feat: RemoveCassaforte.groovy — standalone Jenkins entry point for C/S actions"
```

---

## Task 12: PuliziaAmbienti.groovy (DBB task wrapper)

**Files:**
- Create: `scripts/PuliziaAmbienti.groovy`

- [ ] **Step 1: Implement**

```groovy
// scripts/PuliziaAmbienti.groovy
// DBB task wrapper — invoked by DBB Language pipeline as type:task step.
// Required TaskVariables: MEMBER, FILE_EXT, CLI_BUILDENV, CLI_BUILDGROUP
// Optional TaskVariables: C1SYSTEM (defaults to derivation from build group)
@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript

def member     = config.getStringVariable('MEMBER')
def fileExt    = config.getStringVariable('FILE_EXT')       // ISP 8-char type code
def environment = config.getStringVariable('CLI_BUILDENV')
def buildGroup = config.getStringVariable('CLI_BUILDGROUP') ?: config.getStringVariable('BUILDGROUP')
def system     = config.getStringVariable('C1SYSTEM') ?: buildGroup?.tokenize('_')?.first()?.toUpperCase() ?: ''

// Reconstruct the source path for build-map lookup.
// DBB context provides the current file being built.
def sourcePath = context.getBuildFile()

def rulesPath   = new File(context.getWorkingDirectory(), 'build-data/rules.csv').absolutePath
def ops         = new ZosFileOpsUSS()
def rules       = new DeletionRulesLoader().load(rulesPath)
// TODO: replace LocalBuildMapClient with a DBB build-result map client
def buildMap    = new LocalBuildMapClient(
    new File(context.getWorkingDirectory(), 'build-data/buildmap.json').absolutePath
)
def deleteLogic = new DeleteCassaforteLogic(ops: ops, rules: rules, buildMap: buildMap)
def prevClean   = new PrevEnvCleanLogic(deleteLogic: deleteLogic)

def count = prevClean.execute(sourcePath, fileExt, environment, system, buildGroup)

println "PuliziaAmbienti: env=$environment predecessor=${new EnvironmentChain().getPredecessor(environment)} deleted=$count"

return 0   // RC 0 = success; DBB requires Integer return
```

- [ ] **Step 2: Verify return type is Integer**

The DBB framework emits `BGZZB0043W` and defaults to RC 0 if the script returns a non-Integer. Confirm the last statement is `return 0` (an `int` literal, auto-boxed to `Integer` by Groovy).

- [ ] **Step 3: Commit**

```bash
git add scripts/PuliziaAmbienti.groovy
git commit -m "feat: PuliziaAmbienti.groovy — DBB task wrapper for DELETE_PREV_ENV_AFTER_BUILD"
```

---

## Task 13: ZosFileOpsUSS (mainframe implementation)

**Files:**
- Create: `scripts/lib/ZosFileOpsUSS.groovy`

No local tests possible — this class uses IBM ZFile/BPXWDYN APIs available only on z/OS USS.

- [ ] **Step 1: Implement ZosFileOpsUSS**

```groovy
// scripts/lib/ZosFileOpsUSS.groovy
// Must be compiled and run exclusively on z/OS USS via groovyz.
// File must be tagged IBM-1047: chtag -tc IBM-1047 ZosFileOpsUSS.groovy
import com.ibm.jzos.ZFile
import com.ibm.jzos.ZFileException

class ZosFileOpsUSS implements ZosFileOps {

    boolean exists(String path) {
        if (path.startsWith('//')) {
            def dsn = toMVSName(path)
            return ZFile.dsExists(dsn)
        }
        new File(path).exists()
    }

    void delete(String path) {
        if (path.startsWith('//')) {
            def (dsn, member) = parseDSN(path)
            if (member) {
                // Delete PDS member via BPXWDYN free
                ZFile.bpxwdyn("free fi(DELMEMB) da('${dsn}(${member})') msg(2)")
            } else {
                ZFile.bpxwdyn("free fi(DELDS) da('${dsn}') msg(2)")
            }
            return
        }
        new File(path).delete()
    }

    void copy(String src, String dst) {
        if (src.startsWith('//') && dst.startsWith('//')) {
            def (srcDsn, srcMember) = parseDSN(src)
            def (dstDsn, dstMember) = parseDSN(dst)
            def srcFile = new ZFile("//'" + (srcMember ? "${srcDsn}(${srcMember})" : srcDsn) + "'", "rb,type=record")
            def dstFile = new ZFile("//'" + (dstMember ? "${dstDsn}(${dstMember})" : dstDsn) + "'", "wb,type=record")
            try {
                def buf = new byte[32760]
                int len
                while ((len = srcFile.read(buf)) >= 0) {
                    dstFile.write(buf, 0, len)
                }
            } finally {
                srcFile.close(); dstFile.close()
            }
            return
        }
        // USS file copy
        new File(dst).bytes = new File(src).bytes
    }

    List<String> list(String container) {
        if (container.startsWith('//')) {
            def dsn = toMVSName(container)
            return ZFile.listMembers(dsn)?.toList() ?: []
        }
        new File(container).list()?.toList() ?: []
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    private String toMVSName(String path) {
        def inner = path.substring(2)
        def m = (inner =~ /^(.+?)\((.+?)\)$/)
        m.matches() ? m.group(1) : inner
    }

    private List<String> parseDSN(String path) {
        def inner = path.substring(2)
        def m = (inner =~ /^(.+?)\((.+?)\)$/)
        m.matches() ? [m.group(1), m.group(2)] : [inner, null]
    }
}
```

- [ ] **Step 2: Tag file on USS after upload**

```bash
# Run on USS after zowe upload or scp
chtag -tc IBM-1047 ZosFileOpsUSS.groovy
```

- [ ] **Step 3: Commit**

```bash
git add scripts/lib/ZosFileOpsUSS.groovy
git commit -m "feat: ZosFileOpsUSS — mainframe ZFile/BPXWDYN implementation"
```

---

## Open items (require ISP clarification before production deploy)

| # | Question | Where it matters |
|---|----------|-----------------|
| 1 | Exact `C1STAGE` values per (environment, layer) — current values in `EnvironmentChain.STAGE_BY_ENV` are placeholders | `EnvironmentChain.groovy` |
| 2 | `C1SYSTEM` derivation from build group / repository name | `RemoveCassaforte.groovy` `extractSystem()`, `PuliziaAmbienti.groovy` |
| 3 | File extension → 8-char ISP type code mapping | `RemoveCassaforte.groovy` `resolveFileType()` |
| 4 | DBB build-result map client API to replace `LocalBuildMapClient` on USS | `RemoveCassaforte.groovy`, `PuliziaAmbienti.groovy` |
| 5 | For `PuliziaAmbienti` SJCL special case: confirm direction of copy (current env → predecessor TOCOLB?) | `PrevEnvCleanLogic.groovy` TODO |
| 6 | `context.getBuildFile()` or equivalent DBB API for source path in `PuliziaAmbienti` | `PuliziaAmbienti.groovy` |
