# Gradle Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate flat-classpath Groovy scripts project to canonical Gradle structure with `src/main/groovy`, `src/test/groovy`, and Spock test suite.

**Architecture:** All business logic classes (no IBM deps) move to `src/main/groovy/`. IBM USS-only code (`ZosFileOpsUSS`) moves to a `src/zos/groovy/` source set compiled only when IBM jars are present in `libs/`. Entry-point scripts (`PuliziaCassaforte.groovy`, `PuliziaPostBuild.groovy`) stay in `scripts/` — they are USS deployment artifacts, not compiled library code. Plain-Groovy assertion tests become Spock specifications.

**Tech Stack:** Groovy 4.0.25 (`org.apache.groovy:groovy-all`), Spock 2.3-groovy-4.0, JUnit 5, Gradle 8.12 (via wrapper bootstrapped from installed Gradle 6.3)

---

## File map after migration

```
src/
  main/groovy/
    ZosFileOps.groovy          (was scripts/lib/)
    LocalFileOps.groovy        (was scripts/lib/)
    BuildMapClient.groovy      (was scripts/tasks/)
    DeleteCassaforteLogic.groovy
    DeletionRule.groovy
    DeletionRulesLoader.groovy
    EnvironmentChain.groovy
    LibraryNameResolver.groovy
    LocalBuildMapClient.groovy
    PatternMatcher.groovy
    PrevEnvCleanLogic.groovy
    SfilamentoLogic.groovy
  zos/groovy/
    ZosFileOpsUSS.groovy       (was scripts/lib/ — IBM JZOS deps, not compiled locally)
  test/groovy/
    PatternMatcherSpec.groovy
    LibraryNameResolverSpec.groovy
    EnvironmentChainSpec.groovy
    LocalFileOpsSpec.groovy
    DeletionRulesLoaderSpec.groovy
    LocalBuildMapClientSpec.groovy
    DeleteCassaforteLogicSpec.groovy
    PrevEnvCleanLogicSpec.groovy
    SfilamentoLogicSpec.groovy
  test/resources/
    fixtures/
      rules.csv                (was scripts/test/fixtures/)
      buildmap.json            (was scripts/test/fixtures/)
scripts/
  PuliziaCassaforte.groovy    (unchanged — USS entry point)
  PuliziaPostBuild.groovy     (unchanged — DBB task entry point)
  build-data/                 (unchanged — deployed to USS alongside scripts)
libs/
  (IBM jars go here — not committed, see libs/README.md)
settings.gradle
build.gradle
.gitignore
gradle/wrapper/
  gradle-wrapper.jar
  gradle-wrapper.properties
gradlew
gradlew.bat
```

**Deleted after migration:**
- `scripts/lib/`
- `scripts/tasks/`
- `scripts/test/`

---

## Task 1: Bootstrap Gradle

**Files:**
- Create: `settings.gradle`
- Create: `build.gradle`
- Create: `.gitignore`
- Create: `libs/README.md`
- Run: `gradle wrapper --gradle-version 8.12`

- [ ] **Step 1: Create settings.gradle**

```groovy
rootProject.name = 'pulizia-cassaforte'
```

- [ ] **Step 2: Create build.gradle**

```groovy
plugins {
    id 'groovy'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.apache.groovy:groovy-all:4.0.25'
    testImplementation 'org.spockframework:spock-core:2.3-groovy-4.0'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.10.2'
}

test {
    useJUnitPlatform()
}

// ZosFileOpsUSS: separate source set requiring IBM jars in libs/.
// Only compiles when libs/*.jar are present; never included in main compilation or tests.
sourceSets {
    zos {
        groovy.srcDirs = ['src/zos/groovy']
        compileClasspath += main.output + configurations.compileClasspath + fileTree('libs') { include '*.jar' }
    }
}
```

- [ ] **Step 3: Create .gitignore**

```
.gradle/
build/
.DS_Store
.venv/
*.pyc
__pycache__/
libs/*.jar
```

- [ ] **Step 4: Create libs/README.md**

```markdown
# libs/

IBM JZOS and DBB jars required to compile `src/zos/groovy/ZosFileOpsUSS.groovy`.

These jars are NOT on Maven Central. Obtain from your z/OS installation or DBB toolkit:
- `jzos-2.x.x.jar` — from z/OS Java SDK (`/usr/lpp/java/...`)
- `dbb-zappbuild-*.jar` — from DBB toolkit

Place jars here, then run: `./gradlew compileZosGroovy`

The main build (`./gradlew build`) and tests do NOT require these jars.
```

- [ ] **Step 5: Generate Gradle wrapper**

```bash
gradle wrapper --gradle-version 8.12
```

Expected output ends with `BUILD SUCCESSFUL`. Creates `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`.

- [ ] **Step 6: Verify wrapper works**

```bash
./gradlew help
```

Expected: `BUILD SUCCESSFUL` (Gradle 8.12 downloads on first run, ~200MB).

- [ ] **Step 7: Commit**

```bash
git add settings.gradle build.gradle .gitignore libs/README.md gradlew gradlew.bat gradle/wrapper/
git commit -m "chore: bootstrap Gradle 8.12 project with Groovy 4 and Spock 2.3"
```

---

## Task 2: Write Spock specs (failing — no sources in Gradle classpath yet)

**Files:**
- Create: `src/test/groovy/PatternMatcherSpec.groovy`
- Create: `src/test/groovy/LibraryNameResolverSpec.groovy`
- Create: `src/test/groovy/EnvironmentChainSpec.groovy`
- Create: `src/test/groovy/LocalFileOpsSpec.groovy`
- Create: `src/test/groovy/DeletionRulesLoaderSpec.groovy`
- Create: `src/test/groovy/LocalBuildMapClientSpec.groovy`
- Create: `src/test/groovy/DeleteCassaforteLogicSpec.groovy`
- Create: `src/test/groovy/PrevEnvCleanLogicSpec.groovy`
- Create: `src/test/groovy/SfilamentoLogicSpec.groovy`

- [ ] **Step 1: Create src/test/groovy/PatternMatcherSpec.groovy**

```groovy
import spock.lang.Specification
import spock.lang.Unroll

class PatternMatcherSpec extends Specification {

    def matcher = new PatternMatcher()

    @Unroll
    def "matches('#pattern', '#value') == #expected"() {
        expect:
        matcher.matches(pattern, value) == expected

        where:
        pattern    | value         | expected
        '%CPYCOB*' | 'ACPYCOB '   | true
        '%CPYCOB*' | 'XCPYCOBABC' | true
        'SZFSSWG ' | 'SZFSSWG '   | true
        'SZFSSWG ' | 'SZFSSWGX'   | false
        '%CB2%R  ' | 'ACB2XR  '   | true
        '%CB2%R  ' | 'ACB2XRY '   | false
        'SJCL*'    | 'SJCL    '   | true
        'SJCL*'    | 'SJCLPROC'   | true
        'SJCL*'    | 'XJCL    '   | false
        '*'        | 'ANYTHING'   | true
    }
}
```

- [ ] **Step 2: Create src/test/groovy/LibraryNameResolverSpec.groovy**

```groovy
import spock.lang.Specification

class LibraryNameResolverSpec extends Specification {

    def resolver = new LibraryNameResolver()

    def "resolve substitutes C1STAGE placeholder"() {
        expect:
        resolver.resolve('LTM00.D9P${C1STAGE}.PE000.LING.COB@@@@@.@@.COPY', 'O1', '') ==
            'LTM00.D9PO1.PE000.LING.COB@@@@@.@@.COPY'
    }

    def "resolve substitutes both C1STAGE and C1SYSTEM"() {
        expect:
        resolver.resolve('LTM00.D9P${C1STAGE}.PE000.SYST.${C1SYSTEM}@@@@@@@.BT.LOAD', 'S1', 'MYSYS') ==
            'LTM00.D9PS1.PE000.SYST.MYSYS@@@@@@@.BT.LOAD'
    }

    def "toTocolbLibrary derives TOCOLB library from cassaforte library"() {
        expect:
        resolver.toTocolbLibrary('LTM00.D9PS1.PE000.@@@@.@@@@@@@@.@@.SJCL') ==
            'LTM00.D9PS1.PE000.TO@@.COLB@@@@.@@.SJCL'
    }

    def "toTocolbLibrary passes through library without @@@@ qualifiers unchanged"() {
        expect:
        resolver.toTocolbLibrary('LTM00.D9PS1.PE000.LING.COB@@@@@.@@.COPY') ==
            'LTM00.D9PS1.PE000.LING.COB@@@@@.@@.COPY'
    }
}
```

- [ ] **Step 3: Create src/test/groovy/EnvironmentChainSpec.groovy**

```groovy
import spock.lang.Specification
import spock.lang.Unroll

class EnvironmentChainSpec extends Specification {

    def chain = new EnvironmentChain()

    @Unroll
    def "getPredecessor('#env') == #expected"() {
        expect:
        chain.getPredecessor(env) == expected

        where:
        env   | expected
        'ST'  | 'ATO'
        'SAD' | 'ATO'
        'PR'  | 'ST'
        'PA'  | 'SAD'
        'ATO' | null
    }

    @Unroll
    def "getSuperiors('#env') == #expected"() {
        expect:
        chain.getSuperiors(env) == expected

        where:
        env   | expected
        'ST'  | ['PR', 'PA']
        'SAD' | ['PR', 'PA']
        'PR'  | []
    }

    @Unroll
    def "getStage('#env') == '#expected'"() {
        expect:
        chain.getStage(env) == expected

        where:
        env   | expected
        'ATI' | 'I1'
        'ATO' | 'O1'
        'ST'  | 'S1'
        'SAD' | 'S1'
        'PR'  | 'P1'
        'PA'  | 'P1'
    }

    def "getStage throws IllegalArgumentException on unknown environment"() {
        when:
        chain.getStage('UNKNOWN')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('UNKNOWN')
    }

    @Unroll
    def "requiresPrevEnvClean('#env') == #expected"() {
        expect:
        chain.requiresPrevEnvClean(env) == expected

        where:
        env   | expected
        'ST'  | true
        'PR'  | true
        'ATO' | false
    }

    @Unroll
    def "supportsSfilamento('#env') == #expected"() {
        expect:
        chain.supportsSfilamento(env) == expected

        where:
        env   | expected
        'ST'  | true
        'SAD' | true
        'ATO' | false
        'PR'  | false
    }
}
```

- [ ] **Step 4: Create src/test/groovy/LocalFileOpsSpec.groovy**

```groovy
import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import java.nio.file.*

class LocalFileOpsSpec extends Specification {

    @TempDir
    Path tempDir

    def "copy creates PDS member; delete removes it"() {
        given:
        def ops = new LocalFileOps(tempDir.toString())
        def src = tempDir.resolve('TEST.DS.SRC/MEMBSRC')
        Files.createDirectories(src.parent)
        Files.writeString(src, 'content')

        expect:
        !ops.exists('//TEST.DS(MEMBER1)')

        when:
        ops.copy('//TEST.DS.SRC(MEMBSRC)', '//TEST.DS(MEMBER1)')

        then:
        ops.exists('//TEST.DS(MEMBER1)')

        when:
        ops.delete('//TEST.DS(MEMBER1)')

        then:
        !ops.exists('//TEST.DS(MEMBER1)')
    }

    def "list returns PDS member names"() {
        given:
        def ops = new LocalFileOps(tempDir.toString())
        def member = tempDir.resolve('TEST.DS.SRC/MEMBSRC')
        Files.createDirectories(member.parent)
        Files.writeString(member, 'content')

        expect:
        ops.list('//TEST.DS.SRC').contains('MEMBSRC')
    }

    def "exists works for USS file path passthrough"() {
        given:
        def ops = new LocalFileOps(tempDir.toString())
        def ussFile = tempDir.resolve('uss-test.txt')
        Files.writeString(ussFile, 'x')

        expect:
        ops.exists(ussFile.toString())
        !ops.exists(tempDir.resolve('nonexistent.txt').toString())
    }
}
```

- [ ] **Step 5: Create src/test/groovy/DeletionRulesLoaderSpec.groovy**

```groovy
import spock.lang.Specification

class DeletionRulesLoaderSpec extends Specification {

    def "load parses all rules from fixture CSV skipping comment line"() {
        given:
        def rulesFile = new File(getClass().getResource('/fixtures/rules.csv').toURI()).canonicalPath

        when:
        def rules = new DeletionRulesLoader().load(rulesFile)

        then:
        rules.size() == 5
        rules[0].typePattern     == '%CPYCOB*'
        rules[0].libraryTemplate == 'LTM00.D9P${C1STAGE}.PE000.LING.COB@@@@@.@@.COPY'
        rules[0].useBuildMap     == false
        rules[1].typePattern     == 'SZFSSWG '
        rules[1].useBuildMap     == true
        rules[3].typePattern     == 'SJCL*   '
    }

    def "load throws IllegalArgumentException on malformed line"() {
        given:
        def tmp = File.createTempFile('rules', '.csv')
        tmp.text = 'BADLINE\n'
        tmp.deleteOnExit()

        when:
        new DeletionRulesLoader().load(tmp.canonicalPath)

        then:
        thrown(IllegalArgumentException)
    }
}
```

- [ ] **Step 6: Create src/test/groovy/LocalBuildMapClientSpec.groovy**

```groovy
import spock.lang.Specification

class LocalBuildMapClientSpec extends Specification {

    LocalBuildMapClient client

    def setup() {
        def jsonFile = new File(getClass().getResource('/fixtures/buildmap.json').toURI()).canonicalPath
        client = new LocalBuildMapClient(jsonFile)
    }

    def "getGeneratedObjects returns mapped objects for known mapasm source"() {
        when:
        def results = client.getGeneratedObjects(
            '/dbb/DEE/IBM/yn_r_01_ato_r1/src/mapasm/batch/mapobj.asm',
            'yn_r_01_ato_r1'
        )

        then:
        results.size() == 1
        results[0].library == 'LTM00.D9PO1.PE000.LING.MAP@@@@@.@@.COPY'
        results[0].member  == 'MAPOBJ'
    }

    def "getGeneratedObjects returns empty list for unknown source path"() {
        expect:
        client.getGeneratedObjects('/dbb/DEE/IBM/yn_r_01_ato_r1/src/unknown/file.cbl', 'yn_r_01_ato_r1') == []
    }
}
```

- [ ] **Step 7: Create src/test/groovy/DeleteCassaforteLogicSpec.groovy**

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
        def lib    = 'LTM00.D9PO1.PE000.LING.COB@@@@@.@@.COPY'
        def member = tempDir.resolve("${lib}/PGMCOBOL")
        Files.createDirectories(member.parent)
        Files.writeString(member, 'content')

        when:
        def count = logic.execute(
            '/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl',
            'ACPYCOB ', 'O1', '', 'yn_r_01_ato_r1'
        )

        then:
        count == 1
        !ops.exists("//${lib}(PGMCOBOL)")
    }

    def "execute is idempotent when member is already absent"() {
        expect:
        logic.execute(
            '/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl',
            'ACPYCOB ', 'O1', '', 'yn_r_01_ato_r1'
        ) == 0
    }

    def "execute resolves member name via BUILD MAP and deletes by generated object"() {
        given:
        def lib    = 'LTM00.D9PO1.PE000.LING.MAP@@@@@.@@.COPY'
        def member = tempDir.resolve("${lib}/MAPOBJ")
        Files.createDirectories(member.parent)
        Files.writeString(member, 'content')

        when:
        def count = logic.execute(
            '/dbb/DEE/IBM/yn_r_01_ato_r1/src/mapasm/batch/mapobj.asm',
            'SZFSSWG ', 'O1', '', 'yn_r_01_ato_r1'
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

- [ ] **Step 8: Create src/test/groovy/PrevEnvCleanLogicSpec.groovy**

```groovy
import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import java.nio.file.*

class PrevEnvCleanLogicSpec extends Specification {

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
        logic = new PrevEnvCleanLogic(deleteLogic: deleteLogic)
    }

    def "execute deletes from predecessor env library when current env has a predecessor"() {
        given:
        // ST's predecessor is ATO (stage O1)
        def lib    = 'LTM00.D9PO1.PE000.LING.COB@@@@@.@@.COPY'
        def member = tempDir.resolve("${lib}/PGMCOBOL")
        Files.createDirectories(member.parent)
        Files.writeString(member, 'content')

        when:
        def count = logic.execute(
            '/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl',
            'ACPYCOB ', 'ST', '', 'yn_r_01_ato_r1'
        )

        then:
        count == 1
        !ops.exists("//${lib}(PGMCOBOL)")
    }

    def "execute returns 0 when current env has no predecessor (ATO)"() {
        expect:
        logic.execute(
            '/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/pgmcobol.cbl',
            'ACPYCOB ', 'ATO', '', 'yn_r_01_ato_r1'
        ) == 0
    }
}
```

- [ ] **Step 9: Create src/test/groovy/SfilamentoLogicSpec.groovy**

```groovy
import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import java.nio.file.*

class SfilamentoLogicSpec extends Specification {

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
        sfilamento = new SfilamentoLogic(ops: ops, deleteLogic: deleteLogic, rules: rules)
    }

    def "execute deletes ST cassaforte SJCL member and restores from PR into TOCOLB"() {
        given:
        def stSjclLib = 'LTM00.D9PS1.PE000.@@@@.@@@@@@@@.@@.SJCL'
        def prSjclLib = 'LTM00.D9PP1.PE000.@@@@.@@@@@@@@.@@.SJCL'
        [stSjclLib, prSjclLib].each { lib ->
            def m = tempDir.resolve("${lib}/MYJCL")
            Files.createDirectories(m.parent)
            Files.writeString(m, "${lib}-content")
        }

        when:
        def result = sfilamento.execute(
            '/dbb/DEE/IBM/yn_r_01_st_r1/src/jcl/batch/myjcl.jcl',
            'SJCL    ', 'ST', '', 'yn_r_01_st_r1'
        )

        then:
        result == true
        !ops.exists("//${stSjclLib}(MYJCL)")
        ops.exists('//LTM00.D9PS1.PE000.TO@@.COLB@@@@.@@.SJCL(MYJCL)')
    }

    def "execute returns false and only deletes for non-JCL type (ACPYCOB)"() {
        given:
        def cobLib = 'LTM00.D9PS1.PE000.LING.COB@@@@@.@@.COPY'
        def member = tempDir.resolve("${cobLib}/PGMCOBOL")
        Files.createDirectories(member.parent)
        Files.writeString(member, 'cobol-content')

        when:
        def result = sfilamento.execute(
            '/dbb/DEE/IBM/yn_r_01_st_r1/src/cobol/batch/pgmcobol.cbl',
            'ACPYCOB ', 'ST', '', 'yn_r_01_st_r1'
        )

        then:
        result == false
        !ops.exists("//${cobLib}(PGMCOBOL)")
    }
}
```

- [ ] **Step 10: Verify specs compile (will fail — no sources yet)**

```bash
./gradlew compileTestGroovy 2>&1 | head -30
```

Expected: compilation errors like `unable to resolve class PatternMatcher`. This confirms the specs exist and Gradle picks them up. Proceed.

- [ ] **Step 11: Commit specs**

```bash
git add src/test/groovy/
git commit -m "test: add Spock specs for all business logic classes"
```

---

## Task 3: Move test fixtures to src/test/resources/

**Files:**
- Create: `src/test/resources/fixtures/rules.csv`
- Create: `src/test/resources/fixtures/buildmap.json`

- [ ] **Step 1: Create fixture dirs and copy files**

```bash
mkdir -p src/test/resources/fixtures
cp scripts/test/fixtures/rules.csv src/test/resources/fixtures/rules.csv
cp scripts/test/fixtures/buildmap.json src/test/resources/fixtures/buildmap.json
```

- [ ] **Step 2: Verify copies exist**

```bash
cat src/test/resources/fixtures/rules.csv
```

Expected: 6 lines (1 comment + 5 rules).

- [ ] **Step 3: Commit**

```bash
git add src/test/resources/
git commit -m "test: add Spock test fixtures to src/test/resources"
```

---

## Task 4: Move main sources to src/main/groovy/

Move every file from `scripts/lib/` and `scripts/tasks/` to `src/main/groovy/`. Remove the path comment (`// scripts/...`) from line 1 of each file.

**Files:**
- Create: `src/main/groovy/ZosFileOps.groovy`
- Create: `src/main/groovy/LocalFileOps.groovy`
- Create: `src/main/groovy/BuildMapClient.groovy`
- Create: `src/main/groovy/DeleteCassaforteLogic.groovy`
- Create: `src/main/groovy/DeletionRule.groovy`
- Create: `src/main/groovy/DeletionRulesLoader.groovy`
- Create: `src/main/groovy/EnvironmentChain.groovy`
- Create: `src/main/groovy/LibraryNameResolver.groovy`
- Create: `src/main/groovy/LocalBuildMapClient.groovy`
- Create: `src/main/groovy/PatternMatcher.groovy`
- Create: `src/main/groovy/PrevEnvCleanLogic.groovy`
- Create: `src/main/groovy/SfilamentoLogic.groovy`

- [ ] **Step 1: Create src/main/groovy/ZosFileOps.groovy**

```groovy
trait ZosFileOps {
    abstract boolean exists(String path)
    abstract void    delete(String path)
    abstract void    copy(String src, String dst)
    abstract List<String> list(String container)
}
```

- [ ] **Step 2: Create src/main/groovy/LocalFileOps.groovy**

```groovy
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

- [ ] **Step 3: Create src/main/groovy/BuildMapClient.groovy**

```groovy
trait BuildMapClient {
    abstract List<Map<String, String>> getGeneratedObjects(String sourcePath, String buildGroup)
}
```

- [ ] **Step 4: Create src/main/groovy/DeletionRule.groovy**

```groovy
@groovy.transform.Immutable
class DeletionRule {
    String typePattern
    String libraryTemplate
    boolean useBuildMap
}
```

- [ ] **Step 5: Create src/main/groovy/PatternMatcher.groovy**

```groovy
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

- [ ] **Step 6: Create src/main/groovy/LibraryNameResolver.groovy**

```groovy
class LibraryNameResolver {
    String resolve(String template, String stage, String system) {
        template
            .replace('${C1STAGE}', stage  ?: '')
            .replace('${C1SYSTEM}', system ?: '')
    }

    // Derives TOCOLB library from resolved cassaforte library.
    // 4th qualifier '@@@@' → 'TO@@', 5th qualifier '@@@@@@@@' → 'COLB@@@@'.
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

- [ ] **Step 7: Create src/main/groovy/EnvironmentChain.groovy**

```groovy
class EnvironmentChain {

    static final Map<String, String> PREDECESSORS = [
        ST: 'ATO', SAD: 'ATO',
        PR: 'ST',  PA:  'SAD',
    ]

    static final Map<String, List<String>> SUPERIORS = [
        ST:  ['PR', 'PA'],
        SAD: ['PR', 'PA'],
        ATI: ['ATO'],
        ATO: ['ST', 'SAD'],
    ]

    // TODO: verify these values against actual ISP build configuration.
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
        if (!stage) throw new IllegalArgumentException("Unknown environment: '${env ?: 'null'}'")
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

- [ ] **Step 8: Create src/main/groovy/DeletionRulesLoader.groovy**

```groovy
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

- [ ] **Step 9: Create src/main/groovy/LocalBuildMapClient.groovy**

```groovy
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

- [ ] **Step 10: Create src/main/groovy/DeleteCassaforteLogic.groovy**

```groovy
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

    static String memberName(String sourcePath) {
        def filename = sourcePath.tokenize('/').last()
        def name = filename.contains('.') ? filename.take(filename.lastIndexOf('.')) : filename
        name.toUpperCase()
    }
}
```

- [ ] **Step 11: Create src/main/groovy/PrevEnvCleanLogic.groovy**

```groovy
class PrevEnvCleanLogic {
    DeleteCassaforteLogic deleteLogic
    EnvironmentChain      envChain = new EnvironmentChain()

    // Returns number of deletes performed (0 if current env has no predecessor).
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

- [ ] **Step 12: Create src/main/groovy/SfilamentoLogic.groovy**

```groovy
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

- [ ] **Step 13: Run tests — must all pass**

```bash
./gradlew test
```

Expected output:
```
PatternMatcherSpec > matches('%CPYCOB*', 'ACPYCOB ') == true PASSED
...
BUILD SUCCESSFUL
10 tests passed
```

If any test fails, fix the source file (do not modify the spec). Re-run after each fix.

- [ ] **Step 14: Commit main sources**

```bash
git add src/main/groovy/
git commit -m "feat: move business logic to src/main/groovy Gradle source set"
```

---

## Task 5: Move ZosFileOpsUSS to src/zos/groovy/

**Files:**
- Create: `src/zos/groovy/ZosFileOpsUSS.groovy`

- [ ] **Step 1: Create src/zos/groovy/ZosFileOpsUSS.groovy**

Copy the file content verbatim from `scripts/lib/ZosFileOpsUSS.groovy`, removing only the top path comment:

```groovy
// Mainframe-only. Must be compiled and run with groovyz on z/OS USS.
// After upload to USS: chtag -tc IBM-1047 ZosFileOpsUSS.groovy
// ZFile javadoc https://www.ibm.com/docs/en/sdk-java-technology/8?topic=jzos-zfile
import com.ibm.jzos.ZFile
import com.ibm.jzos.ZFileException

class ZosFileOpsUSS implements ZosFileOps {

    boolean exists(String path) {
        if (path.startsWith('//')) {
            return ZFile.dsExists(mvsName(path))
        }
        new File(path).exists()
    }

    void delete(String path) {
        if (path.startsWith('//')) {
            def (dsn, member) = parseDsn(path)
            if (member) {
                // TODO: verify behaviour with ISP team — on some z/OS configurations
                // FREE DELETE on a PDS member reference scratches the whole PDS.
                // Alternative: use an IEHPROGM step or TSO DELETE command via ZUtil.
                def ddname = 'D' + UUID.randomUUID().toString().replace('-', '').substring(0, 7).toUpperCase()
                ZFile.bpxwdyn("alloc fi(${ddname}) da('${dsn}(${member})') old msg(2)")
                ZFile.bpxwdyn("free fi(${ddname}) delete")
            } else {
                def ddname = 'D' + UUID.randomUUID().toString().replace('-', '').substring(0, 7).toUpperCase()
                ZFile.bpxwdyn("alloc fi(${ddname}) da('${dsn}') old msg(2)")
                ZFile.bpxwdyn("free fi(${ddname}) delete")
            }
            return
        }
        new File(path).delete()
    }

    void copy(String src, String dst) {
        if (src.startsWith('//') && dst.startsWith('//')) {
            def (srcDsn, srcMember) = parseDsn(src)
            def (dstDsn, dstMember) = parseDsn(dst)
            def srcSpec = srcMember ? "${srcDsn}(${srcMember})" : srcDsn
            def dstSpec = dstMember ? "${dstDsn}(${dstMember})" : dstDsn
            def srcFile = new ZFile("//'${srcSpec}'", 'rb,type=record')
            try {
                def dstFile = new ZFile("//'${dstSpec}'", 'wb,type=record')
                try {
                    def buf = new byte[32760]
                    int len
                    while ((len = srcFile.read(buf)) >= 0) {
                        dstFile.write(buf, 0, len)
                    }
                } finally {
                    dstFile.close()
                }
            } finally {
                srcFile.close()
            }
            return
        }
        new File(dst).bytes = new File(src).bytes
    }

    List<String> list(String container) {
        if (container.startsWith('//')) {
            return ZFile.listMembers(mvsName(container))?.toList() ?: []
        }
        new File(container).list()?.toList() ?: []
    }

    private String mvsName(String path) {
        def inner = path.substring(2)
        def m = (inner =~ /^(.+?)\((.+?)\)$/)
        m.matches() ? m.group(1) : inner
    }

    private List<String> parseDsn(String path) {
        def inner = path.substring(2)
        def m = (inner =~ /^(.+?)\((.+?)\)$/)
        m.matches() ? [m.group(1), m.group(2)] : [inner, null]
    }
}
```

- [ ] **Step 2: Verify main tests still pass (zos source set must not break them)**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL` (same as before — zos source set is not compiled by default).

- [ ] **Step 3: Commit**

```bash
git add src/zos/groovy/ZosFileOpsUSS.groovy
git commit -m "chore: move ZosFileOpsUSS to src/zos/groovy isolated source set"
```

---

## Task 6: Delete old source directories

At this point all source is in Gradle canonical locations and all tests pass. Remove the old directories.

- [ ] **Step 1: Confirm tests still green before deleting**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Delete old source directories**

```bash
rm -rf scripts/lib scripts/tasks scripts/test
```

- [ ] **Step 3: Run tests again to confirm nothing was lost**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`. If any test fails, restore from git (`git checkout scripts/`) and investigate before re-deleting.

- [ ] **Step 4: Commit deletion**

```bash
git add -A
git commit -m "chore: remove old scripts/lib, scripts/tasks, scripts/test directories"
```

---

## Task 7: Update CLAUDE.md

Update the invocation instructions in `CLAUDE.md` to reflect the new structure.

- [ ] **Step 1: Locate the Scripts section in CLAUDE.md** (line 12–30 approx)

Replace the classpath invocation examples:

Old text to find:
```bash
groovyz -cp lib:tasks PuliziaCassaforte.groovy <file-lista> <build-group> <environment>
```

New text:
```bash
# Local dev (Gradle-built jar):
./gradlew jar
groovyz -cp build/libs/pulizia-cassaforte.jar scripts/PuliziaCassaforte.groovy <file-lista> <build-group> <environment>

# On USS (after uploading jar + scripts/ to USS):
groovyz -cp pulizia-cassaforte.jar PuliziaCassaforte.groovy <file-lista> <build-group> <environment>
```

- [ ] **Step 2: Update the Architecture section** to reflect new source paths

Replace:
```
CassaforteDeleteLogic  ←  ZosFileOps (trait)
                               ├── LocalFileOps     (local dev, java.nio)
                               └── ZosFileOpsUSS    (mainframe, ZFile/BPXWDYN)
```

With:
```
src/main/groovy/          — compiled into pulizia-cassaforte.jar
  DeleteCassaforteLogic  ←  ZosFileOps (trait)
                                 ├── LocalFileOps     (local dev, java.nio)
                                 └── (see src/zos/groovy)
src/zos/groovy/
  ZosFileOpsUSS            (mainframe-only, requires IBM jzos jars in libs/)
scripts/
  PuliziaCassaforte.groovy  (USS entry point, uses groovyz)
  PuliziaPostBuild.groovy   (DBB task entry point)
```

- [ ] **Step 3: Update Tooling section** — add Gradle commands

Add after the `generate_doc.sh` block:

```bash
# Run tests
./gradlew test

# Build jar (output: build/libs/pulizia-cassaforte.jar)
./gradlew jar

# Compile USS-only code (requires IBM jars in libs/)
./gradlew compileZosGroovy
```

- [ ] **Step 4: Update Local dev note** — remove reference to run_local.groovy if present

Replace:
```
- Local dev: `groovy -cp lib:tasks run_local.groovy`
- USS (non-DBB): `groovyz -cp lib:tasks run_uss.groovy`
```

With:
```
- Local dev: `./gradlew test` (Spock specs in src/test/groovy/ use LocalFileOps)
- USS entry: `groovyz -cp pulizia-cassaforte.jar PuliziaCassaforte.groovy`
```

- [ ] **Step 5: Run tests one final time**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md for Gradle-based project structure"
```

---

## Self-review

### Spec coverage

| Requirement | Task |
|---|---|
| All business logic classes compile under Gradle | Task 4 |
| ZosFileOpsUSS not on local classpath | Task 5 (zos source set) |
| All 9 test files converted to Spock | Task 2 |
| Fixture files accessible via classpath in tests | Task 3 |
| Old directories removed | Task 6 |
| CLAUDE.md invocation updated | Task 7 |
| `.gitignore` excludes build/ and IBM jars | Task 1 |

### Placeholder check

None found — all steps contain complete file content or exact commands.

### Type consistency

- `LocalFileOps(String baseDir)` — used with `tempDir.toString()` in all specs ✓
- `DeleteCassaforteLogic.memberName(String)` — static call matches class definition ✓
- `DeletionRulesLoader().load(String filePath)` — all callers pass `canonicalPath` ✓
- `LocalBuildMapClient(String jsonFilePath)` — all callers pass `canonicalPath` ✓
- `SfilamentoLogic` constructor uses named params — matches field names `ops`, `deleteLogic`, `rules` ✓
