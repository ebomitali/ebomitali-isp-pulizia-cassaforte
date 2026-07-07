# PuliziaPostBuild Spock Spec Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an in-process Spock spec (`mac` module) that exercises `PuliziaPostBuild.groovy`'s real `TaskScript`/`config`/`context` wiring against `PuliziaCassaforteImpl.doPuliziaPostBuild`, after fixing the stub-API conflict and script bugs that currently make this impossible.

**Architecture:** `stubs/` currently defines `com.ibm.dbb.task.BuildContext` twice (Java + Groovy source sets colliding in one Gradle sourceSet), so only one definition survives into `stubs.jar` — and `library/.../DbbBuildMapClient.groovy` already depends on the Java one's `get(String)` method. Fix: merge both APIs into the single Groovy-source `BuildContext`, add a matching `get(String)` to `TaskVariables`, delete the stale Java file. Then fix `PuliziaPostBuild.groovy`'s five bugs (wrong keys, undefined `parseClass` target, `File`/`String` mismatch, missing `Integer` return). The spec drives the script via `GroovyShell.parse()` + fake `TaskVariables`/`BuildContext` doubles — no subprocess needed, since `type: task` DBB steps run in-process in real life too.

**Tech Stack:** Groovy 4, Spock 2.3, Gradle multi-module (`stubs`, `library`, `fat-source`, `front-end`, `mac`).

**Reference:** `docs/superpowers/specs/2026-07-07-mac-puliziapostbuild-spec-design.md` (design), `docs/superpowers/specs/2026-07-07-mac-mrun-spock-spec-design.md` (sibling design, deferred).

---

### Task 1: Merge the duplicate `BuildContext` stub, extend `TaskVariables`

**Files:**
- Modify: `stubs/src/main/groovy/com/ibm/dbb/task/BuildContext.groovy`
- Delete: `stubs/src/main/java/com/ibm/dbb/task/BuildContext.java`
- Modify: `stubs/src/main/groovy/com/ibm/dbb/task/TaskVariables.groovy`

**Why:** `stubs` currently compiles `BuildContext` from both `src/main/java` (methods `get(String)`, `getBuildFile()`, `getWorkingDirectory()`) and `src/main/groovy` (methods `getStringVariable`, `setVariable`, `getVariable`, `getIntVariable`, `getBooleanVariable`, `hasVariable`) — same class, same sourceSet, two files. Verified via `unzip -l stubs/build/libs/stubs.jar` + `javap` that only the Java one survives in the packaged jar. `library/src/main/groovy/com/intesasanpaolo/bes/pc/DbbBuildMapClient.groovy:70` already calls `context.get('BUILD_GROUP') as BuildGroup` — that call only compiles against the Java stub. Merge both method sets into the Groovy file (the one that also carries `TaskVariables`, keeping stub definitions together), delete the Java duplicate, and add a matching `get(String)` to `TaskVariables` so `PuliziaPostBuild.groovy`'s `config.get(...)` calls resolve too.

- [ ] **Step 1: Rewrite `stubs/src/main/groovy/com/ibm/dbb/task/BuildContext.groovy`**

Replace the entire file content with:

```groovy
package com.ibm.dbb.task

import org.apache.commons.cli.CommandLine

/**
 * ============================================================================
 *  STUB CLASS -- compileOnly, off-host use only.
 *  Real implementation is provided by dbb.jar ($DBB_HOME/lib) on z/OS USS.
 *  DO NOT ship, execute, or package this class -- it exists only so that
 *  Groovy task scripts referencing DBB API classes can be syntax/type
 *  checked and compiled (groovyc / IDE) outside of USS (Strategy D).
 * ============================================================================
 *
 * com.ibm.dbb.task.BuildContext
 *
 * Build-scoped variable store, shared across the entire zBuilder execution
 * (as opposed to TaskVariables, which is per-file/per-task scoped). Made
 * available to Groovy TaskScript-based scripts as `context`, and to Java
 * tasks (AbstractTask subclasses) as the `context` member set by the
 * required (BuildContext, TaskVariables) constructor.
 *
 * Methods below marked "confirmed" are directly evidenced in the DBB 3.0.3
 * tutorials (CLILogEncoding / GitUtilities examples). Methods marked
 * "typical / verify" follow the same naming convention documented for
 * TaskVariables but were not directly quoted in the excerpts reviewed --
 * confirm exact signatures against the real dbb.jar Javadoc / API guide
 * before depending on them for anything host-critical.
 *
 * get(String)/getBuildFile()/getWorkingDirectory() are confirmed in active
 * use by DbbBuildMapClient.groovy (context.get('BUILD_GROUP')) -- kept here
 * merged with the getXxxVariable family below, which previously lived in a
 * second, colliding class definition under src/main/java.
 */
class BuildContext {

    // ---- confirmed ---------------------------------------------------
    /**
     * Returns the org.apache.commons.cli.CommandLine object registered
     * under the given key (typically TaskConstants.COMMAND_LINE), placed
     * into the context by the zBuilder for the active lifecycle's CLI
     * options.
     */
    CommandLine getCommandLine(String key) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    /**
     * Sets/overwrites a build-scoped context variable, e.g.:
     *   context.setVariable("CLI_LOG_ENCODING", encoding)
     */
    void setVariable(String name, Object value) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    /**
     * Returns the raw Object value of a build-scoped context variable, or
     * null if not set. Confirmed in active use as context.get('BUILD_GROUP').
     */
    Object get(String key) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    String getBuildFile() {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    File getWorkingDirectory() {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    // ---- typical / verify against real dbb.jar before relying on it --
    Object getVariable(String name) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    String getStringVariable(String name) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    Integer getIntVariable(String name) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    Boolean getBooleanVariable(String name) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    boolean hasVariable(String name) {
        throw new UnsupportedOperationException("stub - not for execution")
    }
}
```

- [ ] **Step 2: Delete the duplicate Java stub**

```bash
git rm stubs/src/main/java/com/ibm/dbb/task/BuildContext.java
```

- [ ] **Step 3: Add `get(String)` to `TaskVariables`**

In `stubs/src/main/groovy/com/ibm/dbb/task/TaskVariables.groovy`, the file currently ends with:

```groovy
    boolean hasVariable(String name) {
        throw new UnsupportedOperationException("stub - not for execution")
    }
}
```

Change it to:

```groovy
    boolean hasVariable(String name) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    /**
     * Returns the raw Object value of a task-scoped variable, or null if not
     * set. Mirrors BuildContext.get(String) — added so TaskScript-based
     * scripts can use the same .get("KEY") convention for both config and
     * context.
     */
    Object get(String name) {
        throw new UnsupportedOperationException("stub - not for execution")
    }
}
```

- [ ] **Step 4: Verify the stub, library, and fat-source modules still compile with one unambiguous `BuildContext`**

Run:
```bash
./gradlew :stubs:compileGroovy :library:compileGroovy :fat-source:compileGroovy --console=plain
```
Expected: `BUILD SUCCESSFUL`. Note: this also re-runs `:fat-source:generateFullFatSource`, which regenerates `fat-source/src/main/groovy/FullPuliziaCassaforte.groovy` from `library/src/main/groovy` — expect no `git diff` on that file (library sources didn't change in this task), but check with `git diff --stat fat-source/src/main/groovy/FullPuliziaCassaforte.groovy` to be sure.

Then confirm the jar now carries the merged class:
```bash
./gradlew :stubs:jar --console=plain -q
javap -cp stubs/build/libs/stubs.jar com.ibm.dbb.task.BuildContext
```
Expected output includes both `get(java.lang.String)` and `getStringVariable(java.lang.String)`.

- [ ] **Step 5: Commit**

```bash
git add stubs/src/main/groovy/com/ibm/dbb/task/BuildContext.groovy stubs/src/main/groovy/com/ibm/dbb/task/TaskVariables.groovy
git commit -m "$(cat <<'EOF'
Merge duplicate BuildContext stub, add TaskVariables.get()

stubs/ defined com.ibm.dbb.task.BuildContext twice (src/main/java and
src/main/groovy), colliding in the same Gradle sourceSet; only one
definition survived into stubs.jar, silently dropping the other.
DbbBuildMapClient.groovy already depends on the Java one's get(String).
Merge both method sets into one class and add a matching get(String)
to TaskVariables so config/context can use the same convention.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Add fake `TaskVariables`/`BuildContext` test doubles

**Files:**
- Create: `mac/src/test/groovy/FakeTaskContext.groovy`

**Why:** `TaskVariables`/`BuildContext` are real (non-final) stub classes on `mac`'s test classpath (`testImplementation project(':stubs')`), but every method throws `UnsupportedOperationException` — they exist for compile-time type checking only. To drive `PuliziaPostBuild.groovy` in-process, the spec needs subclasses that actually hold values.

- [ ] **Step 1: Create the file**

```groovy
import com.ibm.dbb.task.TaskVariables
import com.ibm.dbb.task.BuildContext

class FakeTaskVariables extends TaskVariables {
    Map<String, String> vars = [:]

    @Override
    Object get(String name) {
        vars[name]
    }
}

class FakeBuildContext extends BuildContext {
    Map<String, String> vars = [:]

    @Override
    Object get(String name) {
        vars[name]
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :mac:compileTestGroovy --console=plain
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add mac/src/test/groovy/FakeTaskContext.groovy
git commit -m "$(cat <<'EOF'
Add fake TaskVariables/BuildContext test doubles for mac

Real stub methods all throw UnsupportedOperationException (compile-only
placeholders). FakeTaskVariables/FakeBuildContext hold an actual
Map<String,String> so PuliziaPostBuildSpec can inject config/context
values the way the DBB zBuilder would.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Add `PuliziaPostBuildFixture` helper

**Files:**
- Create: `mac/src/test/groovy/PuliziaPostBuildFixture.groovy`

**Why:** Ports the same "write config / write rules / write stagemap / seed simulated PDS" responsibilities the shell-script tests already do (see `mac/src/test/sh/mac/mrunppb.sh`), into Groovy, plus the `GroovyShell`-based script loader that plays the role of the DBB zBuilder (compiles `PuliziaPostBuild.groovy`, injects `context`/`config` before `run()`).

- [ ] **Step 1: Create the file**

```groovy
class PuliziaPostBuildFixture {

    final File cwd
    final File dbbBuildDir
    final File zosSimDir

    PuliziaPostBuildFixture(File cwd, File dbbBuildDir, File zosSimDir) {
        this.cwd = cwd
        this.dbbBuildDir = dbbBuildDir
        this.zosSimDir = zosSimDir
    }

    private File rulesFile() {
        new File(dbbBuildDir, 'build-data/rules.csv')
    }

    private File stageMapFile() {
        new File(dbbBuildDir, 'build-data/stagemap.csv')
    }

    void writeRules(String rulesCsvContent) {
        def f = rulesFile()
        f.parentFile.mkdirs()
        f.text = rulesCsvContent
    }

    void writeStageMap(File stageMapFixture) {
        def f = stageMapFile()
        f.parentFile.mkdirs()
        f.text = stageMapFixture.text
    }

    void writeConfig(String buildMapPath) {
        def props = new Properties()
        props.setProperty('fileOpsType', 'macos')
        props.setProperty('buildMapClientType', 'json')
        props.setProperty('buildMapPath', buildMapPath)
        props.setProperty('uxBasedir', zosSimDir.absolutePath)
        props.setProperty('rulesPath', rulesFile().absolutePath)
        props.setProperty('stageMapPath', stageMapFile().absolutePath)
        def cfgFile = new File(cwd, 'PuliziaCassaforte.properties')
        cfgFile.withOutputStream { props.store(it, null) }
    }

    File dataset(String dsName) {
        def dir = new File(zosSimDir, dsName)
        dir.mkdirs()
        dir
    }

    void member(File datasetDir, String name, String content = '') {
        new File(datasetDir, name).text = content
    }

    Script loadPostBuild(File postBuildFile, FakeTaskVariables config, FakeBuildContext context) {
        def shell = new GroovyShell(this.class.classLoader)
        def script = shell.parse(postBuildFile)
        script.config = config
        script.context = context
        script
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :mac:compileTestGroovy --console=plain
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add mac/src/test/groovy/PuliziaPostBuildFixture.groovy
git commit -m "$(cat <<'EOF'
Add PuliziaPostBuildFixture test helper

Writes PuliziaCassaforte.properties/rules.csv/stagemap.csv into the
simulated $DBB_BUILD layout and loads PuliziaPostBuild.groovy via
GroovyShell, injecting config/context the way the DBB zBuilder would.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Wire `mac/build.gradle` test environment

**Files:**
- Modify: `mac/build.gradle`

**Why:** `PuliziaPostBuild.groovy` reads `DBB_BUILD`/`DBB_CONF`/`DBB_HOME` via `System.getenv()` and `PuliziaCassaforte.properties` via a CWD-relative `FileInputStream`. Since the spec runs in-process (no subprocess), these must be set for the whole `test` JVM/working directory, and `FullPuliziaCassaforte.groovy` must be deployed once to the simulated `$DBB_BUILD/groovy/` before tests run.

- [ ] **Step 1: Add the prepare task and test wiring**

The current end of `mac/build.gradle` is:

```groovy
test {
    useJUnitPlatform()
    outputs.upToDateWhen { false }
    dependsOn tasks.named('testPuliziaCassaforte')
    dependsOn tasks.named('testPuliziaPostBuild')
}
```

Replace it with:

```groovy
def dbbBuildSimDir  = layout.buildDirectory.dir('dbb-build-sim')
def postBuildCwdDir = layout.buildDirectory.dir('postbuild-test-cwd')

tasks.register('preparePuliziaPostBuildTestEnv') {
    description = 'Deploys FullPuliziaCassaforte.groovy into the simulated $DBB_BUILD for PuliziaPostBuildSpec'
    def fatSourceFile = project(':fat-source').file('src/main/groovy/FullPuliziaCassaforte.groovy')
    def targetFile = dbbBuildSimDir.get().dir('groovy').file('FullPuliziaCassaforte.groovy').asFile
    def cwdDir = postBuildCwdDir.get().asFile
    inputs.file(fatSourceFile)
    outputs.file(targetFile)
    doLast {
        targetFile.parentFile.mkdirs()
        targetFile.text = fatSourceFile.text
        cwdDir.mkdirs()
    }
}

test {
    useJUnitPlatform()
    outputs.upToDateWhen { false }
    dependsOn tasks.named('testPuliziaCassaforte')
    dependsOn tasks.named('testPuliziaPostBuild')
    dependsOn tasks.named('preparePuliziaPostBuildTestEnv')

    workingDir = postBuildCwdDir.get().asFile
    environment 'DBB_BUILD', dbbBuildSimDir.get().asFile.absolutePath
    environment 'DBB_CONF', dbbBuildSimDir.get().asFile.absolutePath
    environment 'DBB_HOME', dbbBuildSimDir.get().asFile.absolutePath

    systemProperty 'dbbBuildSimDir', dbbBuildSimDir.get().asFile.absolutePath
    systemProperty 'frontEndPostBuildFile', project(':front-end').file('src/main/groovy/PuliziaPostBuild.groovy').absolutePath
    systemProperty 'buildMapFixture', file('src/test/resources/fixtures/buildmap.json').absolutePath
    systemProperty 'stageMapFixture', file('src/test/resources/fixtures/stagemap.csv').absolutePath
}
```

- [ ] **Step 2: Verify Gradle configuration is valid**

```bash
./gradlew :mac:tasks --console=plain -q | grep -i preparePulizia
```
Expected: `preparePuliziaPostBuildTestEnv` listed.

```bash
./gradlew :mac:preparePuliziaPostBuildTestEnv --console=plain
ls -la mac/build/dbb-build-sim/groovy/FullPuliziaCassaforte.groovy mac/build/postbuild-test-cwd
```
Expected: both exist.

- [ ] **Step 3: Commit**

```bash
git add mac/build.gradle
git commit -m "$(cat <<'EOF'
Wire mac test env for in-process PuliziaPostBuild testing

Sets DBB_BUILD/DBB_CONF/DBB_HOME and a fixed test working directory
for the mac test task, and deploys FullPuliziaCassaforte.groovy into
the simulated $DBB_BUILD/groovy/ before tests run, so PuliziaPostBuild
.groovy's GroovyClassLoader.parseClass and relative
PuliziaCassaforte.properties read both resolve correctly.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Write `PuliziaPostBuildSpec` (RED), fix `PuliziaPostBuild.groovy` (GREEN)

**Files:**
- Create: `mac/src/test/groovy/PuliziaPostBuildSpec.groovy`
- Modify: `front-end/src/main/groovy/PuliziaPostBuild.groovy`

**Why:** Prove the interface bug exists (spec fails against the current broken script), then fix it. Scenario data derived directly from the real business logic (`EnvironmentChain`, `PathVariableExtractor`, `LibraryNameResolver`, `MacosFileService` in `fat-source/src/main/groovy/FullPuliziaCassaforte.groovy`) and the real `stagemap.csv` fixture — not copied from the older shell scripts, which use dataset names (`X1A`) inconsistent with the current `stagemap.csv` fixture (`01|ATO` → `X2A`).

Scenario walkthrough (env=ST): `EnvironmentChain.requiresPrevEnvClean('ST')` is true, `getPredecessor('ST')` = `'ATO'`. Source `ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLCA7/YO810BDD.SJCLCA7` → `PathVariableExtractor.extract(...)` finds application segment `yo_y_01_ato_r1` (tokens `[yo,y,01,ato,r1]`, `tokens[2]='01'` matches `\d+`) → `C1SYSTEM='y'`, key `01|ATO` → `stagemap.csv` gives `C1STAGE='X2A'`. Rule `SJCL*;LTM00.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.SJCL;NO` matches fileType `SJCLCA7` (from `.tokenize`/`resolveFileType`) and resolves to library `LTM00.D9PX2A.PE000.@@@@.@@@@@@@@.@@.SJCL`, member `YO810BDD` (from `memberName()`). `MacosFileService` maps `//LTM00.D9PX2A.PE000.@@@@.@@@@@@@@.@@.SJCL(YO810BDD)` to `<uxBasedir>/LTM00.D9PX2A.PE000.@@@@.@@@@@@@@.@@.SJCL/YO810BDD` on disk.

- [ ] **Step 1: Write the spec (against the still-broken script)**

```groovy
import spock.lang.Specification
import spock.lang.TempDir
import java.nio.file.Path

class PuliziaPostBuildSpec extends Specification {

    @TempDir Path zosSimDirPath

    static final String RULES  = 'SJCL*   ;LTM00.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.SJCL;NO'
    static final String SOURCE = 'ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLCA7/YO810BDD.SJCLCA7'
    static final String ATO_LIBRARY = 'LTM00.D9PX2A.PE000.@@@@.@@@@@@@@.@@.SJCL'

    PuliziaPostBuildFixture fixture
    File postBuildFile

    def setup() {
        def dbbBuildDir = new File(System.getProperty('dbbBuildSimDir'))
        def cwd = new File('.').canonicalFile
        postBuildFile = new File(System.getProperty('frontEndPostBuildFile'))

        fixture = new PuliziaPostBuildFixture(cwd, dbbBuildDir, zosSimDirPath.toFile())
        fixture.writeRules(RULES)
        fixture.writeStageMap(new File(System.getProperty('stageMapFixture')))
        fixture.writeConfig(new File(System.getProperty('buildMapFixture')).absolutePath)
    }

    def "ST env deletes stale member from ATO predecessor cassaforte library"() {
        given:
        def atoLib = fixture.dataset(ATO_LIBRARY)
        fixture.member(atoLib, 'YO810BDD')

        def config  = new FakeTaskVariables(vars: [FILE_PATH: SOURCE])
        def context = new FakeBuildContext(vars: [BUILD_ENV: 'ST', BUILD_GROUP: 'ST'])
        def script  = fixture.loadPostBuild(postBuildFile, config, context)

        when:
        def result = script.run()

        then:
        result == 0
        !new File(atoLib, 'YO810BDD').exists()
    }

    def "ATO env has no predecessor to clean, member stays untouched"() {
        given:
        def atoLib = fixture.dataset(ATO_LIBRARY)
        fixture.member(atoLib, 'YO810BDD')

        def config  = new FakeTaskVariables(vars: [FILE_PATH: SOURCE])
        def context = new FakeBuildContext(vars: [BUILD_ENV: 'ATO', BUILD_GROUP: 'ATO'])
        def script  = fixture.loadPostBuild(postBuildFile, config, context)

        when:
        def result = script.run()

        then:
        result == 0
        new File(atoLib, 'YO810BDD').exists()
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

```bash
./gradlew :mac:test --tests "PuliziaPostBuildSpec" --console=plain -i 2>&1 | tail -60
```
Expected: both feature methods FAIL. The first bug hit is the `config.get("file_path")`/`context.get("build_env")` key-case mismatch (the fixture seeds uppercase `FILE_PATH`/`BUILD_ENV`/`BUILD_GROUP` keys, matching the design's uppercase convention; the current script reads lowercase keys) — `sourceFilePath` ends up `null`, and `new File((String) null)` throws `NullPointerException`. Confirm the failure output mentions `NullPointerException` around `PuliziaPostBuild.groovy`.

- [ ] **Step 3: Fix `front-end/src/main/groovy/PuliziaPostBuild.groovy`**

The current full file content is:

```groovy
@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript
//This groovy script is intended to be called from DBB within a step of type task
// so it should inherit from TaskScript to access DBB interface
// Parameters are given as context/config variables
// source is single source file, config.file_path
// environemnt is context build_env
// build group is context build_group
// if no simulationEnv is set in config or it is empty, default PuliziaCassaforteConfig has fileOpsType to 'zos' and buildMapClientType to 'db2'
// simulationEnv is set to macos set fileOpsType to 'zos' and buildMapClientType to 'json'
// simulationEnv is set to ussux set fileOpsType to 'uss', buildMapClientType to 'db2'
// simulationEnv is set to usszos, set fileOpsType to 'zos', buildMapClientType to 'db2', hlq is set to user

// BaseScript transform with com.ibm.dbb.groovy.TaskScript allows this script to access 
// the DBB interface and use context/config,log variables.
// config → a com.ibm.dbb.task.TaskVariables object
// context → a com.ibm.dbb.task.BuildContext object
// log → an SLF4j logger

String sourceFilePath = config.get("file_path")
String environment    = context.get("build_env")
String buildGroup     = context.get("build_group")
String simulationEnv  = config.get("simulationEnv") ?: ''

File sourceFile = new File(sourceFilePath)
if (!sourceFile.exists()) {
    println "Source file does not exist: ${sourceFilePath}"
    System.exit(1)
}

// Read env var DBB_CONF
String dbbConf = System.getenv("DBB_CONF")
if (dbbConf == null) {
    println "Environment variable DBB_CONF is not set."
    System.exit(1)
}
String dbbBuild = System.getenv("DBB_BUILD")
if (dbbBuild == null) {
    println "Environment variable DBB_BUILD is not set."
    System.exit(1)
}
String dbbHome = System.getenv("DBB_HOME")
if (dbbHome == null) {
    println "Environment variable DBB_HOME is not set."
    System.exit(1)
}

// Read PuliziaCassaforte property file from current directory
Properties cfgProps = new Properties()
try {
    cfgProps.load(new FileInputStream("PuliziaCassaforte.properties"))
} catch (IOException e) {
    println "Could not read PuliziaCassaforte.properties: ${e.message}"
    System.exit(1)
}

// if buildMapClientType is not set in properties, default to 'db2'
if (!cfgProps.containsKey('buildMapClientType')) {
    cfgProps.setProperty('buildMapClientType', 'dbb')
}
// if fileOpsType is not set in properties, default to 'zos'
if (!cfgProps.containsKey('fileOpsType')) {
    cfgProps.setProperty('fileOpsType', 'zos')
}

def gcl = new GroovyClassLoader(this.class.classLoader)
gcl.parseClass("${DBB_BUILD}/groovy/cassaforte/fatSourceFile")
def clazz = gcl.loadClass('com.intesasanpaolo.bes.pc.PuliziaCassaforteImpl')
def puliziaCassaforteImpl = clazz.getDeclaredConstructor().newInstance()

int errors = puliziaCassaforteImpl.doPuliziaPostBuild(sourceFile, environment, buildGroup, cfgProps)
println "PuliziaCassaforte completed with ${errors} errors."
if (errors > 0) System.exit(1)
```

Replace it with:

```groovy
@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript
//This groovy script is intended to be called from DBB within a step of type task
// so it should inherit from TaskScript to access DBB interface
// Parameters are given as context/config variables
// source is single source file, config.get("FILE_PATH")
// environemnt is context.get("BUILD_ENV")
// build group is context.get("BUILD_GROUP")
// if no simulationEnv is set in config or it is empty, default PuliziaCassaforteConfig has fileOpsType to 'zos' and buildMapClientType to 'db2'
// simulationEnv is set to macos set fileOpsType to 'zos' and buildMapClientType to 'json'
// simulationEnv is set to ussux set fileOpsType to 'uss', buildMapClientType to 'db2'
// simulationEnv is set to usszos, set fileOpsType to 'zos', buildMapClientType to 'db2', hlq is set to user

// BaseScript transform with com.ibm.dbb.groovy.TaskScript allows this script to access 
// the DBB interface and use context/config,log variables.
// config → a com.ibm.dbb.task.TaskVariables object
// context → a com.ibm.dbb.task.BuildContext object
// log → an SLF4j logger

String sourceFilePath = config.get("FILE_PATH")
String environment    = context.get("BUILD_ENV")
String buildGroup     = context.get("BUILD_GROUP")
String simulationEnv  = config.get("simulationEnv") ?: ''

File sourceFile = new File(sourceFilePath)
if (!sourceFile.exists()) {
    println "Source file does not exist: ${sourceFilePath}"
    System.exit(1)
}

// Read env var DBB_CONF
String dbbConf = System.getenv("DBB_CONF")
if (dbbConf == null) {
    println "Environment variable DBB_CONF is not set."
    System.exit(1)
}
String dbbBuild = System.getenv("DBB_BUILD")
if (dbbBuild == null) {
    println "Environment variable DBB_BUILD is not set."
    System.exit(1)
}
String dbbHome = System.getenv("DBB_HOME")
if (dbbHome == null) {
    println "Environment variable DBB_HOME is not set."
    System.exit(1)
}

// Read PuliziaCassaforte property file from current directory
Properties cfgProps = new Properties()
try {
    cfgProps.load(new FileInputStream("PuliziaCassaforte.properties"))
} catch (IOException e) {
    println "Could not read PuliziaCassaforte.properties: ${e.message}"
    System.exit(1)
}

// if buildMapClientType is not set in properties, default to 'db2'
if (!cfgProps.containsKey('buildMapClientType')) {
    cfgProps.setProperty('buildMapClientType', 'dbb')
}
// if fileOpsType is not set in properties, default to 'zos'
if (!cfgProps.containsKey('fileOpsType')) {
    cfgProps.setProperty('fileOpsType', 'zos')
}

def gcl = new GroovyClassLoader(this.class.classLoader)
gcl.parseClass(new File("${dbbBuild}/groovy/FullPuliziaCassaforte.groovy"))
def clazz = gcl.loadClass('com.intesasanpaolo.bes.pc.PuliziaCassaforteImpl')
def puliziaCassaforteImpl = clazz.getDeclaredConstructor().newInstance()

int errors = puliziaCassaforteImpl.doPuliziaPostBuild(sourceFilePath, environment, buildGroup, cfgProps)
println "PuliziaCassaforte completed with ${errors} errors."
return errors
```

Five changes from the original: (1) `config.get("file_path")`/`context.get("build_env")`/`context.get("build_group")` → uppercase keys `FILE_PATH`/`BUILD_ENV`/`BUILD_GROUP`; (2) `gcl.parseClass("${DBB_BUILD}/groovy/cassaforte/fatSourceFile")` (undefined binding, bogus path) → `gcl.parseClass(new File("${dbbBuild}/groovy/FullPuliziaCassaforte.groovy"))`; (3) `doPuliziaPostBuild(sourceFile, ...)` (File — type mismatch against the real `String sourceToProcess` parameter) → `doPuliziaPostBuild(sourceFilePath, ...)`; (4) dropped the trailing `if (errors > 0) System.exit(1)` (would kill the whole in-process DBB build on a step failure, and gave no `Integer` return on success) → `return errors`.

- [ ] **Step 4: Re-run and confirm both scenarios pass**

```bash
./gradlew :mac:test --tests "PuliziaPostBuildSpec" --console=plain -i 2>&1 | tail -60
```
Expected: `BUILD SUCCESSFUL`, both feature methods pass.

- [ ] **Step 5: Commit**

```bash
git add mac/src/test/groovy/PuliziaPostBuildSpec.groovy front-end/src/main/groovy/PuliziaPostBuild.groovy
git commit -m "$(cat <<'EOF'
Fix PuliziaPostBuild.groovy interface wiring, add Spock coverage

PuliziaPostBuild.groovy had four bugs blocking it from ever running as
a DBB type:task step: wrong config/context variable keys, a
GroovyClassLoader.parseClass target that referenced an undefined
binding and a nonexistent path, a File passed where
doPuliziaPostBuild expects a String, and no Integer return on the
success path (paired with a System.exit that would have killed the
whole in-process DBB build on step failure). PuliziaPostBuildSpec
drives the fixed script in-process via GroovyShell + fake
TaskVariables/BuildContext doubles, covering the ST-deletes-from-ATO
and ATO-has-no-predecessor scenarios.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Full `mac` regression check

**Files:** none (verification only)

**Why:** Confirm nothing else in the `mac` module (existing shell-script Exec tasks, `WrapperCliSpec`) regressed from the `test.workingDir`/environment changes in Task 4.

- [ ] **Step 1: Run the full mac test suite**

```bash
./gradlew :mac:test --console=plain 2>&1 | tail -80
```
Expected: `BUILD SUCCESSFUL` (or, if `testPuliziaCassaforte`/`WrapperCliSpec` were already failing before this plan — confirmed pre-existing per the design doc's "Purpose" section — the same pre-existing failures only, with no new ones introduced by this plan's changes).

- [ ] **Step 2: Report results**

No commit — this task only verifies Task 1–5's changes didn't regress the rest of the module. If new failures appear, stop and diagnose before considering this plan complete.
