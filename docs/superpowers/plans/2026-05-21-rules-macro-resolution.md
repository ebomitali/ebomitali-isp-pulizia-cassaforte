# Rules CSV Macro Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generalize library template macro resolution to support any `${VARNAME}` from a per-file variable map, wiring in stage-map.csv-derived `C1STAGE`, path-derived `C1SYSTEM`, and CLI-parameter `HLQ`.

**Architecture:** Two new classes (`StageMapLoader`, `PathVariableExtractor`) handle data loading and per-file variable extraction. `LibraryNameResolver.resolve()` gains a Map overload replacing the hardcoded `(stage, system)` signature. `DeleteCassaforteLogic`, `SfilamentoLogic`, and `PrevEnvCleanLogic` are updated to receive and propagate the vars map. `PuliziaCassaforteImpl` builds the vars map per file inside the processing loop and accepts an optional `hlq` parameter. Tasks proceed in dependency order; each keeps all tests green before committing.

**Tech Stack:** Groovy 4, Spock 2.3, JUnit 5, Gradle (`:library:test`)

---

## File Map

| Action | Path |
|---|---|
| Create | `library/src/main/groovy/StageMapLoader.groovy` |
| Create | `library/src/main/groovy/PathVariableExtractor.groovy` |
| Create | `library/src/test/groovy/StageMapLoaderSpec.groovy` |
| Create | `library/src/test/groovy/PathVariableExtractorSpec.groovy` |
| Create | `library/src/test/resources/fixtures/stage-map.csv` |
| Create | `library/src/test/resources/fixtures/rules-hlq.csv` |
| Modify | `library/src/main/groovy/LibraryNameResolver.groovy` |
| Modify | `library/src/test/groovy/LibraryNameResolverSpec.groovy` |
| Modify | `library/src/main/groovy/DeleteCassaforteLogic.groovy` |
| Modify | `library/src/test/groovy/DeleteCassaforteLogicSpec.groovy` |
| Modify | `library/src/main/groovy/SfilamentoLogic.groovy` |
| Modify | `library/src/test/groovy/SfilamentoLogicSpec.groovy` |
| Modify | `library/src/main/groovy/PrevEnvCleanLogic.groovy` |
| Modify | `library/src/test/groovy/PrevEnvCleanLogicSpec.groovy` |
| Modify | `library/src/main/groovy/PuliziaCassaforteImpl.groovy` |
| Modify | `library/src/test/groovy/PuliziaCassaforteImplSpec.groovy` |
| Modify | `front-end/scripts/groovy/PuliziaCassaforte.groovy` |
| Modify | `front-end/scripts/groovy/PuliziaCassaforteLocal.groovy` |

---

### Task 1: `StageMapLoader` — load stage-map.csv into a lookup map

**Files:**
- Create: `library/src/test/resources/fixtures/stage-map.csv`
- Create: `library/src/test/groovy/StageMapLoaderSpec.groovy`
- Create: `library/src/main/groovy/StageMapLoader.groovy`

- [ ] **Step 1: Create test fixture `library/src/test/resources/fixtures/stage-map.csv`**

```
      "01|ATI1";"X1A"
      "01|ATI2";"X1A"
      "01|ATO";"X2A"
      "01|ST";"XAD"
      "01|PR";"XPE"
      "01|EM";"XEB"
      "03|ATO";"Y2A"
      "03|ST";"YAD"
      "03|PR";"YPE"
      "03|EM";"YEB"
```

- [ ] **Step 2: Write the failing spec**

Create `library/src/test/groovy/StageMapLoaderSpec.groovy`:

```groovy
import spock.lang.Specification

class StageMapLoaderSpec extends Specification {

    def loader = new StageMapLoader()

    def "load returns map from valid stage-map.csv"() {
        given:
        def path = new File(getClass().getResource('/fixtures/stage-map.csv').toURI()).canonicalPath

        when:
        def map = loader.load(path)

        then:
        map['01|ATO'] == 'X2A'
        map['01|ST']  == 'XAD'
        map['03|ST']  == 'YAD'
        map['01|PR']  == 'XPE'
    }

    def "load strips surrounding quotes and whitespace from keys and values"() {
        given:
        def path = new File(getClass().getResource('/fixtures/stage-map.csv').toURI()).canonicalPath

        when:
        def map = loader.load(path)

        then:
        map.keySet().every { !it.contains('"') && !it.startsWith(' ') }
        map.values().every { !it.contains('"') && !it.startsWith(' ') }
    }

    def "load skips blank lines"() {
        given:
        def path = new File(getClass().getResource('/fixtures/stage-map.csv').toURI()).canonicalPath

        expect:
        loader.load(path).size() == 10
    }

    def "load throws on malformed row missing semicolon"() {
        given:
        def tmp = File.createTempFile('stage-map', '.csv')
        tmp.text = '"01|ATO" "X2A"\n'

        when:
        loader.load(tmp.canonicalPath)

        then:
        thrown(IllegalArgumentException)

        cleanup:
        tmp.delete()
    }
}
```

- [ ] **Step 3: Run — verify it fails**

```bash
cd /Users/bomitalievelino/Documents/Workspace/isp-ibm-mauden/repo/pulizia-cassaforte
./gradlew :library:test --tests StageMapLoaderSpec 2>&1 | tail -15
```

Expected: FAILED — `StageMapLoader` class not found.

- [ ] **Step 4: Create `library/src/main/groovy/StageMapLoader.groovy`**

```groovy
/**
 * Loads the ISP stage-map CSV into a lookup map.
 *
 * <p>CSV format (semicolon-delimited, keys and values wrapped in double-quotes,
 * with optional leading whitespace per line):
 * <pre>
 *   "01|ATO";"X2A"
 * </pre>
 * Key format: {@code PATH_LO|BUILD_ENV} (e.g. {@code "01|ATO"}).
 * Value: C1STAGE code (e.g. {@code "X2A"}).
 */
class StageMapLoader {

    Map<String, String> load(String csvPath) {
        new File(csvPath).readLines()
            .findAll { it.trim() }
            .collectEntries { line ->
                def parts = line.trim().split(';')
                if (parts.size() < 2)
                    throw new IllegalArgumentException("Malformed stage-map row: '$line'")
                def key   = parts[0].trim().replace('"', '')
                def value = parts[1].trim().replace('"', '')
                [key, value]
            }
    }
}
```

- [ ] **Step 5: Run — verify it passes**

```bash
./gradlew :library:test --tests StageMapLoaderSpec 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, 4 tests pass.

- [ ] **Step 6: Commit**

```bash
git add library/src/main/groovy/StageMapLoader.groovy \
        library/src/test/groovy/StageMapLoaderSpec.groovy \
        library/src/test/resources/fixtures/stage-map.csv
git commit -m "feat: add StageMapLoader for stage-map.csv lookup"
```

---

### Task 2: `PathVariableExtractor` — per-file variable map from source path

**Files:**
- Create: `library/src/test/groovy/PathVariableExtractorSpec.groovy`
- Create: `library/src/main/groovy/PathVariableExtractor.groovy`

- [ ] **Step 1: Write the failing spec**

Create `library/src/test/groovy/PathVariableExtractorSpec.groovy`:

```groovy
import spock.lang.Specification

class PathVariableExtractorSpec extends Specification {

    static final Map<String, String> STAGE_MAP = [
        '01|ATO': 'X2A', '01|ST': 'XAD', '01|PR': 'XPE',
        '03|ATO': 'Y2A', '03|ST': 'YAD',
    ]

    def extractor = new PathVariableExtractor()

    def "extracts C1SYSTEM and C1STAGE from standard path format"() {
        when:
        def vars = extractor.extract(
            'ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YO8AMADD.SJCLINP',
            'ATO', STAGE_MAP, null
        )

        then:
        vars['C1SYSTEM'] == 'y'
        vars['C1STAGE']  == 'X2A'
        vars['HLQ']      == ''
    }

    def "works with absolute path containing prefix before ENV segment"() {
        when:
        def vars = extractor.extract(
            '/repo/cloned/ATO/yo_y_01_ato_r1/src/COBOL/batch/pgm.cbl',
            'ATO', STAGE_MAP, null
        )

        then:
        vars['C1SYSTEM'] == 'y'
        vars['C1STAGE']  == 'X2A'
    }

    def "sets HLQ from parameter"() {
        when:
        def vars = extractor.extract(
            'ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YO8AMADD.SJCLINP',
            'ATO', STAGE_MAP, 'U0G9700'
        )

        then:
        vars['HLQ'] == 'U0G9700'
    }

    def "HLQ is empty string when null parameter passed"() {
        when:
        def vars = extractor.extract(
            'ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YO8AMADD.SJCLINP',
            'ATO', STAGE_MAP, null
        )

        then:
        vars['HLQ'] == ''
    }

    def "extracts from different PATH_LO yielding different C1STAGE"() {
        when:
        def vars = extractor.extract(
            'ATO/xo_n_03_ato_r1/src/COBOL/batch/pgm.cbl',
            'ATO', STAGE_MAP, null
        )

        then:
        vars['C1SYSTEM'] == 'n'
        vars['C1STAGE']  == 'Y2A'
    }

    def "throws IllegalArgumentException when PATH_LO|BUILD_ENV key not in stage map"() {
        when:
        extractor.extract(
            'ATO/yo_y_99_ato_r1/src/JCL/f.jcl',
            'ATO', STAGE_MAP, null
        )

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('99|ATO')
    }

    def "throws IllegalArgumentException when no application segment found in path"() {
        when:
        extractor.extract('/just/a/flat/path/file.ext', 'ATO', STAGE_MAP, null)

        then:
        thrown(IllegalArgumentException)
    }
}
```

- [ ] **Step 2: Run — verify it fails**

```bash
./gradlew :library:test --tests PathVariableExtractorSpec 2>&1 | tail -10
```

Expected: FAILED — `PathVariableExtractor` class not found.

- [ ] **Step 3: Create `library/src/main/groovy/PathVariableExtractor.groovy`**

```groovy
/**
 * Extracts per-file template variables from a source path.
 *
 * <p>Identifies the application segment in the path (the component whose
 * underscore-delimited tokens satisfy {@code tokens[2] =~ /\d+/}), then
 * looks up the C1STAGE in the stage map using {@code PATH_LO|buildEnv}.
 *
 * <p>Returns a map with keys {@code C1STAGE}, {@code C1SYSTEM}, {@code HLQ}.
 *
 * @see StageMapLoader
 * @see LibraryNameResolver
 */
class PathVariableExtractor {

    Map<String, String> extract(String sourcePath, String buildEnv,
                                Map<String, String> stageMap, String hlq) {
        def segment = sourcePath.tokenize('/').find { part ->
            def tokens = part.split('_')
            tokens.size() >= 5 && tokens[2] ==~ /\d+/
        }
        if (!segment)
            throw new IllegalArgumentException(
                "No application segment found in source path: '${sourcePath}'"
            )

        def tokens   = segment.split('_')
        def c1system = tokens[1]
        def pathLo   = tokens[2]
        def key      = "${pathLo}|${buildEnv}"
        def c1stage  = stageMap[key]
        if (!c1stage)
            throw new IllegalArgumentException(
                "No stage-map entry for '${key}' (path: '${sourcePath}')"
            )

        [C1STAGE: c1stage, C1SYSTEM: c1system, HLQ: hlq ?: '']
    }
}
```

- [ ] **Step 4: Run — verify it passes**

```bash
./gradlew :library:test --tests PathVariableExtractorSpec 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, 7 tests pass.

- [ ] **Step 5: Run full suite — no regressions**

```bash
./gradlew :library:test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add library/src/main/groovy/PathVariableExtractor.groovy \
        library/src/test/groovy/PathVariableExtractorSpec.groovy
git commit -m "feat: add PathVariableExtractor for per-file variable map"
```

---

### Task 3: `LibraryNameResolver` — add Map-based `resolve()` overload

**Files:**
- Modify: `library/src/main/groovy/LibraryNameResolver.groovy`
- Modify: `library/src/test/groovy/LibraryNameResolverSpec.groovy`

The old `resolve(String, String, String)` is kept in this task — it will be removed in Task 7 once all callers are migrated.

- [ ] **Step 1: Add new failing tests to `LibraryNameResolverSpec.groovy`**

Append these test methods inside the class (after the existing tests):

```groovy
def "resolve(Map) substitutes C1STAGE and C1SYSTEM"() {
    expect:
    resolver.resolve(
        'LTM00.D9P${C1STAGE}.PE000.SYST.${C1SYSTEM}@@@@@@@.BT.LOAD',
        [C1STAGE: 'X2A', C1SYSTEM: 'y', HLQ: '']
    ) == 'LTM00.D9PX2A.PE000.SYST.y@@@@@@@.BT.LOAD'
}

def "resolve(Map) substitutes HLQ"() {
    expect:
    resolver.resolve(
        '${HLQ}.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.ZARA',
        [C1STAGE: 'X2A', C1SYSTEM: 'y', HLQ: 'U0G9700']
    ) == 'U0G9700.D9PX2A.PE000.@@@@.@@@@@@@@.@@.ZARA'
}

def "resolve(Map) HLQ empty string substitutes to empty string without error"() {
    expect:
    resolver.resolve(
        '${HLQ}.D9P${C1STAGE}',
        [C1STAGE: 'X2A', C1SYSTEM: '', HLQ: '']
    ) == '.D9PX2A'
}

def "resolve(Map) throws IllegalStateException on unresolved macro"() {
    when:
    resolver.resolve('LTM00.D9P${UNKNOWN}.PE000', [C1STAGE: 'X2A'])

    then:
    def e = thrown(IllegalStateException)
    e.message.contains('${UNKNOWN}')
}
```

- [ ] **Step 2: Run — verify new tests fail**

```bash
./gradlew :library:test --tests LibraryNameResolverSpec 2>&1 | tail -15
```

Expected: FAILED — `No signature of method LibraryNameResolver.resolve() is applicable for argument types: (String, LinkedHashMap)`.

- [ ] **Step 3: Add Map overload to `LibraryNameResolver.groovy`**

Add the following method inside the class (after the existing `resolve(String, String, String)` method):

```groovy
String resolve(String template, Map<String, String> vars) {
    def result = vars.inject(template) { acc, key, val ->
        acc.replace('${' + key + '}', val ?: '')
    }
    def unresolved = (result =~ /\$\{[^}]+\}/)
    if (unresolved) {
        throw new IllegalStateException(
            "Unresolved macro: ${unresolved[0]} in template: '${template}'"
        )
    }
    result
}
```

- [ ] **Step 4: Run — verify all LibraryNameResolverSpec tests pass**

```bash
./gradlew :library:test --tests LibraryNameResolverSpec 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, all tests pass (4 existing + 4 new).

- [ ] **Step 5: Run full suite — no regressions**

```bash
./gradlew :library:test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add library/src/main/groovy/LibraryNameResolver.groovy \
        library/src/test/groovy/LibraryNameResolverSpec.groovy
git commit -m "feat: add Map-based resolve() overload to LibraryNameResolver"
```

---

### Task 4: `DeleteCassaforteLogic` — migrate `execute()` to `Map<String,String> vars`

**Files:**
- Modify: `library/src/main/groovy/DeleteCassaforteLogic.groovy`
- Modify: `library/src/test/groovy/DeleteCassaforteLogicSpec.groovy`
- Modify: `library/src/main/groovy/SfilamentoLogic.groovy` (stub fix for compilation)
- Modify: `library/src/main/groovy/PrevEnvCleanLogic.groovy` (stub fix for compilation)
- Modify: `library/src/main/groovy/PuliziaCassaforteImpl.groovy` (stub fix for compilation)

The stage codes in `DeleteCassaforteLogicSpec` change from `O1` (EnvironmentChain hardcoded) to `X2A` (stage-map value for `01|ATO`) because vars are now caller-supplied. C1SYSTEM for segment `yn_r_01_ato_r1` = `r` (tokens[1]).

- [ ] **Step 1: Update `DeleteCassaforteLogicSpec.groovy` to use vars map**

Replace the entire file content with:

```groovy
import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import java.nio.file.*

class DeleteCassaforteLogicSpec extends Specification {

    @TempDir
    Path tempDir

    LocalFileOps ops
    DeleteCassaforteLogic logic

    def setup() {
        def rulesFile = new File(getClass().getResource('/fixtures/rules.csv').toURI()).canonicalPath
        def bmFile    = new File(getClass().getResource('/fixtures/buildmap.json').toURI()).canonicalPath
        ops   = new LocalFileOps(tempDir.toString())
        logic = new DeleteCassaforteLogic(
            ops:      ops,
            rules:    new DeletionRulesLoader().load(rulesFile),
            buildMap: new LocalBuildMapClient(bmFile)
        )
    }

    def "execute deletes member by source name (NO flag)"() {
        given:
        def lib    = 'LTM00.D9PX2A.PE000.LING.COB@@@@@.@@.COPY'
        def member = tempDir.resolve("${lib}/PGMCOBOL")
        Files.createDirectories(member.parent)
        Files.writeString(member, 'content')

        when:
        def count = logic.execute(
            '/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl',
            'ACPYCOB ',
            [C1STAGE: 'X2A', C1SYSTEM: 'r', HLQ: ''],
            'yn_r_01_ato_r1'
        )

        then:
        count == 1
        !ops.exists("//${lib}(PGMCOBOL)")
    }

    def "execute is idempotent when member is already absent"() {
        expect:
        logic.execute(
            '/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl',
            'ACPYCOB ',
            [C1STAGE: 'X2A', C1SYSTEM: 'r', HLQ: ''],
            'yn_r_01_ato_r1'
        ) == 0
    }

    def "execute resolves member name via BUILD MAP and deletes by generated object"() {
        given:
        def sourcePath = '/dbb/DEE/IBM/yn_r_01_ato_r1/src/mapasm/batch/mapobj.asm'
        def buildGroup = 'yn_r_01_ato_r1'
        def lib        = 'LTM00.D9PX2A.PE000.LING.MAP@@@@@.@@.COPY'
        def member     = tempDir.resolve("${lib}/MAPOBJ")
        Files.createDirectories(member.parent)
        Files.writeString(member, 'content')

        def buildMapMock = [getGeneratedObjects: { sp, bg ->
            sp == sourcePath && bg == buildGroup
                ? [[library: lib, member: 'MAPOBJ']]
                : []
        }] as BuildMapClient

        def localLogic = new DeleteCassaforteLogic(
            ops:      ops,
            rules:    logic.rules,
            buildMap: buildMapMock
        )

        when:
        def count = localLogic.execute(
            sourcePath, 'SZFSSWG ',
            [C1STAGE: 'X2A', C1SYSTEM: 'r', HLQ: ''],
            buildGroup
        )

        then:
        count == 1
        !ops.exists("//${lib}(MAPOBJ)")
    }

    def "memberName extracts uppercase stem without extension"() {
        expect:
        DeleteCassaforteLogic.memberName('/path/to/abcdef.cbl') == 'ABCDEF'
        DeleteCassaforteLogic.memberName('/path/to/NOEEXT')     == 'NOEEXT'
    }
}
```

- [ ] **Step 2: Run — verify tests fail (old signature)**

```bash
./gradlew :library:test --tests DeleteCassaforteLogicSpec 2>&1 | tail -15
```

Expected: FAILED — signature mismatch on `execute()`.

- [ ] **Step 3: Update `DeleteCassaforteLogic.groovy` — replace execute() signature**

Replace the `execute()` method with:

```groovy
int execute(String sourcePath, String fileType, Map<String,String> vars, String buildGroup) {
    def member   = memberName(sourcePath)
    def matching = rules.findAll { matcher.matches(it.typePattern, fileType) }
    int count    = 0

    matching.each { rule ->
        def lib = resolver.resolve(rule.libraryTemplate, vars)
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
```

- [ ] **Step 4: Fix callers of the old `execute()` signature so the suite compiles**

In `library/src/main/groovy/SfilamentoLogic.groovy`, replace the one call to `deleteLogic.execute()`:

```groovy
// was: deleteLogic.execute(sourcePath, fileType, stage, system, buildGroup)
deleteLogic.execute(sourcePath, fileType, [C1STAGE: stage, C1SYSTEM: system, HLQ: ''], buildGroup)
```

In `library/src/main/groovy/PrevEnvCleanLogic.groovy`, replace the one call:

```groovy
// was: deleteLogic.execute(sourcePath, fileType, prevStage, system, buildGroup)
deleteLogic.execute(sourcePath, fileType, [C1STAGE: prevStage, C1SYSTEM: system, HLQ: ''], buildGroup)
```

In `library/src/main/groovy/PuliziaCassaforteImpl.groovy`, replace the one call (inside `private execute()`, `case 'C'`):

```groovy
// was: deleteLogic.execute(sourcePath, fileType, stage, system, buildGroup)
deleteLogic.execute(sourcePath, fileType, [C1STAGE: stage, C1SYSTEM: system, HLQ: ''], buildGroup)
```

- [ ] **Step 5: Run all tests — verify full suite passes**

```bash
./gradlew :library:test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add library/src/main/groovy/DeleteCassaforteLogic.groovy \
        library/src/test/groovy/DeleteCassaforteLogicSpec.groovy \
        library/src/main/groovy/SfilamentoLogic.groovy \
        library/src/main/groovy/PrevEnvCleanLogic.groovy \
        library/src/main/groovy/PuliziaCassaforteImpl.groovy
git commit -m "refactor: migrate DeleteCassaforteLogic.execute() to Map<String,String> vars"
```

---

### Task 5: `SfilamentoLogic` — inject `PathVariableExtractor`, full per-env vars

**Files:**
- Modify: `library/src/main/groovy/SfilamentoLogic.groovy`
- Modify: `library/src/test/groovy/SfilamentoLogicSpec.groovy`
- Modify: `library/src/main/groovy/PuliziaCassaforteImpl.groovy` (update call site for new execute() signature)

Stage codes after change (path `yn_r_01_st_r1`, env ST):
- C1SYSTEM = `r`, PATH_LO = `01`
- ST: `01|ST` → `XAD` → library `LTM00.D9PXAD.PE000.@@@@.@@@@@@@@.@@.SJCL`
- PR (superior of ST): `01|PR` → `XPE` → library `LTM00.D9PXPE.PE000.@@@@.@@@@@@@@.@@.SJCL`
- TOCOLB from `LTM00.D9PXAD.PE000.@@@@.@@@@@@@@.@@.SJCL` → `LTM00.D9PXAD.PE000.TO@@.COLB@@@@.@@.SJCL`

- [ ] **Step 1: Update `SfilamentoLogicSpec.groovy`**

Replace the entire file content with:

```groovy
import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import java.nio.file.*

class SfilamentoLogicSpec extends Specification {

    static final Map<String, String> STAGE_MAP = [
        '01|ATO': 'X2A', '01|ST': 'XAD', '01|PR': 'XPE',
        '03|ATO': 'Y2A', '03|ST': 'YAD',
    ]

    @TempDir
    Path tempDir

    SfilamentoLogic sfilamento
    LocalFileOps ops

    def setup() {
        def rulesFile = new File(getClass().getResource('/fixtures/rules.csv').toURI()).canonicalPath
        def bmFile    = new File(getClass().getResource('/fixtures/buildmap.json').toURI()).canonicalPath
        ops = new LocalFileOps(tempDir.toString())
        def rules       = new DeletionRulesLoader().load(rulesFile)
        def deleteLogic = new DeleteCassaforteLogic(
            ops: ops, rules: rules,
            buildMap: new LocalBuildMapClient(bmFile)
        )
        sfilamento = new SfilamentoLogic(
            ops:        ops,
            deleteLogic: deleteLogic,
            rules:      rules,
            extractor:  new PathVariableExtractor(),
            stageMap:   STAGE_MAP,
            hlq:        ''
        )
    }

    def "execute deletes ST cassaforte SJCL member and restores from PR into TOCOLB"() {
        given:
        def stSjclLib = 'LTM00.D9PXAD.PE000.@@@@.@@@@@@@@.@@.SJCL'
        def prSjclLib = 'LTM00.D9PXPE.PE000.@@@@.@@@@@@@@.@@.SJCL'
        [stSjclLib, prSjclLib].each { lib ->
            def m = tempDir.resolve("${lib}/MYJCL")
            Files.createDirectories(m.parent)
            Files.writeString(m, "${lib}-content")
        }

        when:
        def result = sfilamento.execute(
            '/dbb/DEE/IBM/yn_r_01_st_r1/src/jcl/batch/myjcl.jcl',
            'SJCL    ', 'ST', 'yn_r_01_st_r1'
        )

        then:
        result == true
        !ops.exists("//${stSjclLib}(MYJCL)")
        ops.exists('//LTM00.D9PXAD.PE000.TO@@.COLB@@@@.@@.SJCL(MYJCL)')
    }

    def "execute returns false and only deletes for non-JCL type (ACPYCOB)"() {
        given:
        def cobLib = 'LTM00.D9PXAD.PE000.LING.COB@@@@@.@@.COPY'
        def member = tempDir.resolve("${cobLib}/PGMCOBOL")
        Files.createDirectories(member.parent)
        Files.writeString(member, 'cobol-content')

        when:
        def result = sfilamento.execute(
            '/dbb/DEE/IBM/yn_r_01_st_r1/src/cobol/batch/pgmcobol.cbl',
            'ACPYCOB ', 'ST', 'yn_r_01_st_r1'
        )

        then:
        result == false
        !ops.exists("//${cobLib}(PGMCOBOL)")
    }
}
```

- [ ] **Step 2: Run — verify tests fail (old signature)**

```bash
./gradlew :library:test --tests SfilamentoLogicSpec 2>&1 | tail -15
```

Expected: FAILED — `execute()` signature mismatch.

- [ ] **Step 3: Update `SfilamentoLogic.groovy`**

Replace the entire file content with:

```groovy
class SfilamentoLogic {
    ZosFileOps            ops
    DeleteCassaforteLogic deleteLogic
    List<DeletionRule>    rules
    PathVariableExtractor extractor  = new PathVariableExtractor()
    Map<String, String>   stageMap   = [:]
    String                hlq        = ''
    PatternMatcher        matcher    = new PatternMatcher()
    LibraryNameResolver   resolver   = new LibraryNameResolver()
    EnvironmentChain      envChain   = new EnvironmentChain()

    boolean execute(String sourcePath, String fileType, String environment, String buildGroup) {
        def currentVars = extractor.extract(sourcePath, environment, stageMap, hlq)
        deleteLogic.execute(sourcePath, fileType, currentVars, buildGroup)

        if (!matcher.matches('SJCL*', fileType)) return false
        if (!envChain.supportsSfilamento(environment)) return false

        def member   = DeleteCassaforteLogic.memberName(sourcePath)
        def matching = rules.findAll { matcher.matches(it.typePattern, fileType) }

        for (String superEnv : envChain.getSuperiors(environment)) {
            def superVars = extractor.extract(sourcePath, superEnv, stageMap, hlq)
            for (def rule : matching) {
                def srcLib = resolver.resolve(rule.libraryTemplate, superVars)
                def src    = "//${srcLib}(${member})"
                if (ops.exists(src)) {
                    def localLib = resolver.resolve(rule.libraryTemplate, currentVars)
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

- [ ] **Step 4: Update `PuliziaCassaforteImpl.groovy` — remove `system` from sfilamento call**

In the `private execute()` method, replace the `case 'S'` line:

```groovy
// was: sfilamento.execute(sourcePath, fileType, environment, system, buildGroup)
sfilamento.execute(sourcePath, fileType, environment, buildGroup)
```

Also update the `SfilamentoLogic` instantiation inside `private execute()` to inject `extractor`, `stageMap`, and `hlq` (these are available after Task 8; for now leave the instantiation — the fields have defaults):

```groovy
def sfilamento = new SfilamentoLogic(
    ops: ops, deleteLogic: deleteLogic, rules: rules, envChain: envChain
)
```

(The `extractor`, `stageMap`, `hlq` fields default to a new `PathVariableExtractor()`, `[:]`, and `''` respectively. The stageMap will be empty — this will fail at runtime for 'S' actions until Task 8 wires it fully. For now, existing tests only use the 'C' path, so they pass.)

- [ ] **Step 5: Run all tests — verify full suite passes**

```bash
./gradlew :library:test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add library/src/main/groovy/SfilamentoLogic.groovy \
        library/src/test/groovy/SfilamentoLogicSpec.groovy \
        library/src/main/groovy/PuliziaCassaforteImpl.groovy
git commit -m "feat: inject PathVariableExtractor into SfilamentoLogic for per-env vars"
```

---

### Task 6: `PrevEnvCleanLogic` — inject `PathVariableExtractor`, full per-env vars

**Files:**
- Modify: `library/src/main/groovy/PrevEnvCleanLogic.groovy`
- Modify: `library/src/test/groovy/PrevEnvCleanLogicSpec.groovy`

ST predecessor is ATO. Path `yn_r_01_ato_r1`, env ATO: `01|ATO` → `X2A`.
Library for ATO stage: `LTM00.D9PX2A.PE000.LING.COB@@@@@.@@.COPY`.

- [ ] **Step 1: Update `PrevEnvCleanLogicSpec.groovy`**

Replace the entire file content with:

```groovy
import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import java.nio.file.*

class PrevEnvCleanLogicSpec extends Specification {

    static final Map<String, String> STAGE_MAP = [
        '01|ATO': 'X2A', '01|ST': 'XAD', '01|PR': 'XPE',
    ]

    @TempDir
    Path tempDir

    PrevEnvCleanLogic logic
    LocalFileOps ops

    def setup() {
        def rulesFile = new File(getClass().getResource('/fixtures/rules.csv').toURI()).canonicalPath
        def bmFile    = new File(getClass().getResource('/fixtures/buildmap.json').toURI()).canonicalPath
        ops = new LocalFileOps(tempDir.toString())
        def deleteLogic = new DeleteCassaforteLogic(
            ops:      ops,
            rules:    new DeletionRulesLoader().load(rulesFile),
            buildMap: new LocalBuildMapClient(bmFile)
        )
        logic = new PrevEnvCleanLogic(
            deleteLogic: deleteLogic,
            extractor:   new PathVariableExtractor(),
            stageMap:    STAGE_MAP,
            hlq:         ''
        )
    }

    def "execute deletes from predecessor env library when current env has a predecessor"() {
        given:
        // ST predecessor is ATO → stage X2A
        def lib    = 'LTM00.D9PX2A.PE000.LING.COB@@@@@.@@.COPY'
        def member = tempDir.resolve("${lib}/PGMCOBOL")
        Files.createDirectories(member.parent)
        Files.writeString(member, 'content')

        when:
        def count = logic.execute(
            '/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl',
            'ACPYCOB ', 'ST', 'yn_r_01_ato_r1'
        )

        then:
        count == 1
        !ops.exists("//${lib}(PGMCOBOL)")
    }

    def "execute returns 0 when current env has no predecessor (ATO)"() {
        expect:
        logic.execute(
            '/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl',
            'ACPYCOB ', 'ATO', 'yn_r_01_ato_r1'
        ) == 0
    }
}
```

- [ ] **Step 2: Run — verify tests fail**

```bash
./gradlew :library:test --tests PrevEnvCleanLogicSpec 2>&1 | tail -15
```

Expected: FAILED.

- [ ] **Step 3: Update `PrevEnvCleanLogic.groovy`**

Replace entire file content with:

```groovy
class PrevEnvCleanLogic {
    DeleteCassaforteLogic deleteLogic
    PathVariableExtractor extractor  = new PathVariableExtractor()
    Map<String, String>   stageMap   = [:]
    String                hlq        = ''
    EnvironmentChain      envChain   = new EnvironmentChain()

    int execute(String sourcePath, String fileType, String environment, String buildGroup) {
        if (!envChain.requiresPrevEnvClean(environment)) return 0
        def prevEnv  = envChain.getPredecessor(environment)
        def prevVars = extractor.extract(sourcePath, prevEnv, stageMap, hlq)
        deleteLogic.execute(sourcePath, fileType, prevVars, buildGroup)
    }
}
```

- [ ] **Step 4: Run all tests — full suite passes**

```bash
./gradlew :library:test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add library/src/main/groovy/PrevEnvCleanLogic.groovy \
        library/src/test/groovy/PrevEnvCleanLogicSpec.groovy
git commit -m "feat: inject PathVariableExtractor into PrevEnvCleanLogic for per-env vars"
```

---

### Task 7: Remove old `LibraryNameResolver.resolve(String, String, String)`

**Files:**
- Modify: `library/src/main/groovy/LibraryNameResolver.groovy`
- Modify: `library/src/test/groovy/LibraryNameResolverSpec.groovy`

After Tasks 4–6, the only remaining callers of the old 3-arg method are the existing `LibraryNameResolverSpec` tests. Remove the old method and update those tests.

- [ ] **Step 1: Remove old tests from `LibraryNameResolverSpec.groovy`**

Remove (or replace) the two tests that use the old 3-arg signature:

```groovy
// DELETE these two methods:
def "resolve substitutes C1STAGE placeholder"() { ... }
def "resolve substitutes both C1STAGE and C1SYSTEM"() { ... }
```

Replace them with updated versions using the Map overload:

```groovy
def "resolve substitutes C1STAGE placeholder"() {
    expect:
    resolver.resolve(
        'LTM00.D9P${C1STAGE}.PE000.LING.COB@@@@@.@@.COPY',
        [C1STAGE: 'O1', C1SYSTEM: '', HLQ: '']
    ) == 'LTM00.D9PO1.PE000.LING.COB@@@@@.@@.COPY'
}

def "resolve substitutes both C1STAGE and C1SYSTEM"() {
    expect:
    resolver.resolve(
        'LTM00.D9P${C1STAGE}.PE000.SYST.${C1SYSTEM}@@@@@@@.BT.LOAD',
        [C1STAGE: 'S1', C1SYSTEM: 'MYSYS', HLQ: '']
    ) == 'LTM00.D9PS1.PE000.SYST.MYSYS@@@@@@@.BT.LOAD'
}
```

- [ ] **Step 2: Run — verify tests still pass with old method present**

```bash
./gradlew :library:test --tests LibraryNameResolverSpec 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Remove old `resolve(String, String, String)` from `LibraryNameResolver.groovy`**

Delete this method:

```groovy
String resolve(String template, String stage, String system) {
    template
        .replace('${C1STAGE}', stage  ?: '')
        .replace('${C1SYSTEM}', system ?: '')
}
```

- [ ] **Step 4: Run all tests — full suite passes**

```bash
./gradlew :library:test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add library/src/main/groovy/LibraryNameResolver.groovy \
        library/src/test/groovy/LibraryNameResolverSpec.groovy
git commit -m "refactor: remove deprecated resolve(String,String,String) from LibraryNameResolver"
```

---

### Task 8: `PuliziaCassaforteImpl` — wire `StageMapLoader`/`PathVariableExtractor` per-file, add `hlq`

**Files:**
- Modify: `library/src/main/groovy/PuliziaCassaforteImpl.groovy`
- Modify: `library/src/test/groovy/PuliziaCassaforteImplSpec.groovy`
- Create: `library/src/test/resources/fixtures/rules-hlq.csv`

- [ ] **Step 1: Create test fixture `library/src/test/resources/fixtures/rules-hlq.csv`**

```
# HLQ test fixture — single rule with ${HLQ} in library template
SZFSSWG ;${HLQ}.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.ZARA;NO
```

- [ ] **Step 2: Add HLQ end-to-end test to `PuliziaCassaforteImplSpec.groovy`**

Add these constants and test method to the existing class:

```groovy
static final String HLQ_SOURCE_PATH =
    '/repo/cloned/ATO/yo_y_01_ato_r1/src/mapasm/batch/TESTMEM.SZFSSWG'
static final String HLQ_LIBRARY =
    'U0G9700.D9PX2A.PE000.@@@@.@@@@@@@@.@@.ZARA'
static final String HLQ_MEMBER  = 'TESTMEM'
```

```groovy
def "C action with HLQ resolves template and deletes correct member"() {
    given:
    def hlqImpl = new PuliziaCassaforteImpl()
    hlqImpl.rulesPath     = new File(getClass().getResource('/fixtures/rules-hlq.csv').toURI()).canonicalPath
    hlqImpl.stageMapPath  = new File(getClass().getResource('/fixtures/stage-map.csv').toURI()).canonicalPath

    def member = tempDir.resolve("${HLQ_LIBRARY}/${HLQ_MEMBER}")
    Files.createDirectories(member.parent)
    Files.writeString(member, 'content')

    def lista = listFile("C,${HLQ_SOURCE_PATH}")

    when:
    def errors = hlqImpl.run(lista, 'ATO', 'yo_y_01_ato_r1', bmFile, ops, 'U0G9700')

    then:
    errors == 0
    !ops.exists("//${HLQ_LIBRARY}(${HLQ_MEMBER})")
}
```

- [ ] **Step 3: Run — verify new test fails**

```bash
./gradlew :library:test --tests PuliziaCassaforteImplSpec."C action with HLQ*" 2>&1 | tail -15
```

Expected: FAILED.

- [ ] **Step 4: Update `PuliziaCassaforteImpl.groovy`**

Replace the entire file content with:

```groovy
class PuliziaCassaforteImpl {

    String rulesPath     = new File('.', 'build-data/rules.csv').canonicalPath
    String stageMapPath  = new File('.', 'build-data/stage-map.csv').canonicalPath

    int run(String listFile, String environment, String buildGroup,
            String userId, String pwFilePath, File db2ConfigFile) {
        run(listFile, environment, buildGroup, userId, pwFilePath, db2ConfigFile, '')
    }

    int run(String listFile, String environment, String buildGroup,
            String userId, String pwFilePath, File db2ConfigFile, String hlq) {
        BuildMapClient buildMap
        try {
            buildMap = BuildMapClientFactory.fromConf(buildGroup, userId, pwFilePath, db2ConfigFile)
        } catch (IllegalStateException e) {
            System.err.println "WARN: ${e.message} — build map lookups will return empty"
            System.exit(1)
        }
        execute(listFile, environment, buildGroup, buildMap, ZosFileOpsFactory.createOnZos(), hlq)
    }

    int run(String listFile, String environment, String buildGroup, File bmFile) {
        run(listFile, environment, buildGroup, bmFile, ZosFileOpsFactory.mockZos(), '')
    }

    int run(String listFile, String environment, String buildGroup, File bmFile, ZosFileOps ops) {
        run(listFile, environment, buildGroup, bmFile, ops, '')
    }

    int run(String listFile, String environment, String buildGroup, File bmFile, ZosFileOps ops, String hlq) {
        execute(listFile, environment, buildGroup, BuildMapClientFactory.fromJson(bmFile), ops, hlq)
    }

    private int execute(String listFile, String environment, String buildGroup,
                        BuildMapClient buildMap, ZosFileOps ops, String hlq) {
        def rules      = new DeletionRulesLoader().load(rulesPath)
        def stageMap   = new StageMapLoader().load(stageMapPath)
        def extractor  = new PathVariableExtractor()
        def envChain   = new EnvironmentChain()
        def deleteLogic = new DeleteCassaforteLogic(ops: ops, rules: rules, buildMap: buildMap)
        def sfilamento  = new SfilamentoLogic(
            ops:         ops,
            deleteLogic: deleteLogic,
            rules:       rules,
            envChain:    envChain,
            extractor:   extractor,
            stageMap:    stageMap,
            hlq:         hlq
        )

        int processed = 0, errors = 0
        new File(listFile).eachLine { raw ->
            def line = raw.trim()
            if (!line || line.startsWith('#')) return
            def comma = line.indexOf(',')
            if (comma < 0) {
                System.err.println "Skipping malformed line: '$line'"
                errors++
                return
            }
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
        return errors
    }

    private static String resolveFileType(String sourcePath) {
        def filename = sourcePath.tokenize('/').last()
        def ext = filename.contains('.') ? filename.substring(filename.lastIndexOf('.') + 1) : filename
        ext.toUpperCase().padRight(8).take(8)
    }
}
```

Note: `extractSystem(buildGroup)` is removed — C1SYSTEM is now derived per-file by `PathVariableExtractor`.

- [ ] **Step 5: Update `PuliziaCassaforteImplSpec` — add `stageMapPath` to existing tests**

In the `setup()` method, add:

```groovy
impl.stageMapPath = new File(getClass().getResource('/fixtures/stage-map.csv').toURI()).canonicalPath
```

The existing tests use `SOURCE_PATH = '/repo/cloned/ATO/yn_r_01_ato_r1/src/mapasm/batch/TESTMEM.SZFSSWG'` (segment `yn_r_01_ato_r1`, PATH_LO=`01`, env=`ATO`, C1STAGE=`X2A`). The fixture rules.csv uses `${C1STAGE}`. The existing test only checks `errors == 0` (no member pre-created → no delete attempted → 0 errors). This still passes.

- [ ] **Step 6: Run all tests — full suite passes**

```bash
./gradlew :library:test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add library/src/main/groovy/PuliziaCassaforteImpl.groovy \
        library/src/test/groovy/PuliziaCassaforteImplSpec.groovy \
        library/src/test/resources/fixtures/rules-hlq.csv
git commit -m "feat: wire StageMapLoader/PathVariableExtractor per-file in PuliziaCassaforteImpl, add hlq param"
```

---

### Task 9: `PuliziaCassaforte.groovy` and `PuliziaCassaforteLocal.groovy` — add `--hlq` CLI arg

**Files:**
- Modify: `front-end/scripts/groovy/PuliziaCassaforte.groovy`
- Modify: `front-end/scripts/groovy/PuliziaCassaforteLocal.groovy`

- [ ] **Step 1: Update `front-end/scripts/groovy/PuliziaCassaforte.groovy`**

Add `--hlq` parsing alongside existing flags, and pass `hlq` to both `run()` call sites.

Replace the entire file content with:

```groovy
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
```

Wait — `run(listFile, environment, buildGroup, bmFile, hlq)` needs a matching overload. In Task 8, the overloads defined are:
- `run(listFile, env, group, bmFile)` → no hlq
- `run(listFile, env, group, bmFile, ops)` → no hlq
- `run(listFile, env, group, bmFile, ops, hlq)` → has hlq

There is no `run(listFile, env, group, bmFile, hlq)` overload. Add it to `PuliziaCassaforteImpl.groovy`:

```groovy
int run(String listFile, String environment, String buildGroup, File bmFile, String hlq) {
    execute(listFile, environment, buildGroup, BuildMapClientFactory.fromJson(bmFile), ZosFileOpsFactory.mockZos(), hlq)
}
```

Add this overload to `PuliziaCassaforteImpl.groovy` before the `private execute()` method.

- [ ] **Step 2: Update `front-end/scripts/groovy/PuliziaCassaforteLocal.groovy`**

Update the inline logic to use `PathVariableExtractor` per-file (replacing the `stage`/`system` pre-computation and the old `execute()` call signatures):

Replace the main processing block (from `def stage = ...` through the `eachLine` loop) with:

```groovy
def stageMap = new StageMapLoader().load(new File('.', 'build-data/stage-map.csv').canonicalPath)
def extractor = new PathVariableExtractor()
def hlq = ''  // override for local testing if needed

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
```

Also remove `def stage = ...` and `def system = ...` lines, and update the `SfilamentoLogic` instantiation to inject `extractor`, `stageMap`, `hlq`:

```groovy
def sfilamento = new SfilamentoLogic(
    ops: ops, deleteLogic: deleteLogic, rules: rules, envChain: envChain,
    extractor: extractor, stageMap: stageMap, hlq: hlq
)
```

Also remove the `extractSystem()` helper method (no longer used).

- [ ] **Step 3: Add missing `run(listFile, env, group, bmFile, hlq)` overload to `PuliziaCassaforteImpl.groovy`**

In `library/src/main/groovy/PuliziaCassaforteImpl.groovy`, add before `private int execute(...)`:

```groovy
int run(String listFile, String environment, String buildGroup, File bmFile, String hlq) {
    execute(listFile, environment, buildGroup, BuildMapClientFactory.fromJson(bmFile), ZosFileOpsFactory.mockZos(), hlq)
}
```

- [ ] **Step 4: Run all tests — full suite passes**

```bash
./gradlew :library:test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add front-end/scripts/groovy/PuliziaCassaforte.groovy \
        front-end/scripts/groovy/PuliziaCassaforteLocal.groovy \
        library/src/main/groovy/PuliziaCassaforteImpl.groovy
git commit -m "feat: add --hlq CLI argument to PuliziaCassaforte.groovy"
```

---

## Notes

- `PuliziaPostBuild.groovy` and `PuliziaPostBuildLocal.groovy` call `PrevEnvCleanLogic.execute()` with the old 5-arg signature. These scripts will break at runtime after Task 6. Update them in a separate task (out of scope here).
- Production `rules.csv` (`front-end/scripts/build-data/rules.csv`) contains malformed `${C1STAGE` (missing `}`) on several lines. These will throw `IllegalStateException` when resolved. Fix the CSV as a separate task.
