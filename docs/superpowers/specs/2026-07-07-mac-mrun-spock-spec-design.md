# mac: Spock spec for mrunpc.sh / RunPuliziaCassaforte.groovy — design

## Purpose

`mac/src/main/script/mrunpc.sh` is the USS launcher: `groovy -cp "$GROOVY_CLASSPATH" RunPuliziaCassaforte.groovy "$LISTA" "$ENV" "$BUILD_GROUP"`. It is the shell/Groovy interface boundary between Jenkins (or a human on USS) and `PuliziaCassaforteImpl`. Today that boundary is only exercised by ad-hoc shell scripts (`mac/src/test/sh/mac/mrunpct2.sh/7/8/9.sh`), of which only `mrunpct2.sh` is wired into `mac/build.gradle` (`testPuliziaCassaforte`); `mrunpct7/8/9.sh` are unwired and their target, the `ScriptLoader`-mock double (`mac/src/test/sh/RunPuliziaCassaforte.groovy`), is broken (`com.ibm.dbb.groovy.ScriptLoader` referenced but not defined in `stubs/`).

This adds a Groovy/Spock spec that constructs the same environment mrunpc.sh expects, runs it as a real subprocess, and asserts on file-system outcome — as **additional** coverage alongside (not replacing) the existing shell-script tests.

## Scope decisions (from brainstorming)

- Add alongside existing shell tests; do not retire `testPuliziaCassaforte`/`testPuliziaPostBuild` Exec tasks.
- Cover all 4 `PuliziaCassaforte` shell scenarios (`mrunpct2/7/8/9`), not `PuliziaPostBuild`/`mrunppb.sh`.
- Leave `mac/src/test/groovy/CliRunner.groovy` and `WrapperCliSpec.groovy` untouched (unrelated placeholder scaffolding) — add new files instead.
- Target the real `front-end/src/main/groovy/RunPuliziaCassaforte.groovy` (not the broken `ScriptLoader`-mock double), fixing its one real bug rather than routing around it.

## Bug fix: `front-end/src/main/groovy/RunPuliziaCassaforte.groovy`

Current (broken):
```groovy
def gcl = new GroovyClassLoader(this.class.classLoader)
gcl.parseClass("${DBB_BUILD}/groovy/cassaforte/fatSourceFile")
def clazz = gcl.loadClass('com.intesasanpaolo.bes.pc.PuliziaCassaforteImpl')
def puliziaCassaforteImpl = clazz.getDeclaredConstructor().newInstance()
int errors = puliziaCassaforte.doPuliziaCassaforte(sourcesListFile, environment, buildGroup, cfgProps)
```
Two bugs: `"${DBB_BUILD}/groovy/cassaforte/fatSourceFile"` is a string passed where `parseClass` expects a `File` (or source text) and interpolates an undefined binding `DBB_BUILD` (the local var is lowercase `dbbBuild`) pointing at a path (`groovy/cassaforte/fatSourceFile`) that doesn't correspond to any real deployed file; separately `puliziaCassaforte` (undefined) is used instead of `puliziaCassaforteImpl` on the last line (pre-existing uncommitted fix already present in working tree per `git diff`, keep it).

On USS, DBB compiles/caches Groovy source directly (no jar packaging step) and auto-discovers/deploys sources under `$DBB_BUILD/groovy/` (per `docs/deploy-strategies.md`). `FullPuliziaCassaforte.groovy` is deployed at `$DBB_BUILD/groovy/FullPuliziaCassaforte.groovy`. Fix:

```groovy
def gcl = new GroovyClassLoader(this.class.classLoader)
gcl.parseClass(new File("${dbbBuild}/groovy/FullPuliziaCassaforte.groovy"))
def clazz = gcl.loadClass('com.intesasanpaolo.bes.pc.PuliziaCassaforteImpl')
def puliziaCassaforteImpl = clazz.getDeclaredConstructor().newInstance()
int errors = puliziaCassaforteImpl.doPuliziaCassaforte(sourcesListFile, environment, buildGroup, cfgProps)
println "PuliziaCassaforte completed with ${errors} errors."
```
(`dbbBuild` is the existing `System.getenv("DBB_BUILD")` local var, already null-checked above in the script.) `doPuliziaCassaforte(File, String, String, Properties)` already exists with this exact signature on `PuliziaCassaforteImpl` in `fat-source/src/main/groovy/FullPuliziaCassaforte.groovy` — no other API mismatch.

Note: `RunPuliziaCassaforte.groovy` never calls `System.exit()` based on the returned `errors` count — it always exits 0 unless an exception/explicit early-exit fires. This is pre-existing behavior, left as-is (not in scope); it means the new spec must assert on **file-system state**, not just process exit code, exactly like `mrunpct7/8/9.sh` already do.

## Test environment layout (per scenario, under a Spock `@TempDir`)

```
workDir/                              (CWD when mrunpc.sh runs — colocated with mrunpc.sh)
  RunPuliziaCassaforte.groovy         (fixed front-end version, copied)
  PuliziaCassaforte.properties
  simplelogger.properties
  lista.csv

workDir/dbb-build/                    (simulated $DBB_BUILD; env DBB_BUILD=workDir/dbb-build)
  groovy/FullPuliziaCassaforte.groovy (copied from fat-source/src/main/groovy)
  build-data/rules.csv
  build-data/stagemap.csv

workDir/zos-sim/                      (simulated PDS root; uxBasedir property)
  <dataset-dir>/<member>              (touch + optional content, per scenario)
```

`buildMapPath` (JSON build-map fixture used by `JsonBuildMapClient`) stays pointed at the existing `mac/src/test/resources/fixtures/buildmap.json` via absolute path — it's a `JsonBuildMapClient` test double, not a `$DBB_BUILD` deployment convention, so it is not relocated.

`PuliziaCassaforte.properties` content (per scenario):
```
fileOpsType=macos
buildMapClientType=json
buildMapPath=<abs path to fixtures/buildmap.json>
uxBasedir=<workDir>/zos-sim
rulesPath=<workDir>/dbb-build/build-data/rules.csv
stageMapPath=<workDir>/dbb-build/build-data/stagemap.csv
```

`simplelogger.properties`: same content mrunpct2.sh/7/8/9.sh already write (debug level, show log name/thread/date-time).

## New files

### `mac/src/test/groovy/PuliziaCassaforteFixture.groovy` (helper class)

Ports the bash `write_config`/`write_simplelogger_config`/`write_rules`/`list_file`/dataset-seeding functions into Groovy, parameterized per scenario:

```groovy
class PuliziaCassaforteFixture {
    final File workDir
    final File dbbBuildDir   // workDir/dbb-build
    final File zosSimDir     // workDir/zos-sim

    PuliziaCassaforteFixture(File workDir)

    void writeConfig(String buildMapPath)
    void writeSimpleLoggerConfig()
    void writeRules(String rulesCsvContent)
    void writeStageMap(File sourceStageMapFixture)      // copies existing fixture as-is
    File writeLista(List<List<String>> actionSourcePairs)  // [[action, sourcePath], ...] -> lista.csv
    File dataset(String dsName)                          // mkdirs workDir/zos-sim/<dsName>, returns File
    void member(File datasetDir, String memberName, String content = null)  // touch (+ optional content)
    void deployFatSource(File fatSourceFile)              // copy into dbb-build/groovy/FullPuliziaCassaforte.groovy
    void deployRunner(File fixedRunnerFile)                // copy into workDir/RunPuliziaCassaforte.groovy
}
```

### `mac/src/test/groovy/ShRunner.groovy`

New class (does not touch `CliRunner.groovy`). Shells a script via `sh <script> <args...>` in a given working directory with a given environment map; reuses the existing top-level `CliResult` class from `CliRunner.groovy` (same default package, no import needed since both are compiled together in `src/test/groovy`).

```groovy
class ShRunner {
    static CliResult run(File script, File workDir, Map<String, String> env,
                          List<String> args, long timeoutSeconds = 60)
}
```
Implementation mirrors `CliRunner.run`: `ProcessBuilder('sh', script.absolutePath, *args).directory(workDir)`, environment from the supplied map, `consumeProcessOutput`, timeout + `destroyForcibly`.

### `mac/src/test/groovy/PuliziaCassaforteMrunSpec.groovy`

Spock spec, `@TempDir Path workDir` per iteration. One `@Unroll`-style feature method with a `where:` block driving 4 scenarios (ported verbatim from `mrunpct2.sh`/`mrunpct7.sh`/`mrunpct8.sh`/`mrunpct9.sh`, mechanism swapped to Spock/Groovy):

| # | name | env | buildGroup | action | source(s) | rules.csv | dataset(s) & members | expected outcome |
|---|------|-----|-----------|--------|-----------|-----------|----------------------|-------------------|
| 1 | ato-c-delete | ATO | ATO | C | `ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLCA7/YO810BDD.SJCLCA7`, `.../SJCLINP/YO8AMBDD.SJCLINP`, `.../SJCLITT/YO84XS1.SJCLITT` | `SJCL*   ;LTM00.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.SJCL;NO` | `U0G9700.D9PX1A.PE000.@@@@.JCL` with empty members `YO810BDD`, `YO8AMBDD`, `YO84XS1` | all 3 members deleted from the dataset dir |
| 2 | st-s-sfilamento-sjcl | ST | ST-MAIN | S | `ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLCA7/YO810BDD.SJCLCA7` | `SJCL*   ;LTM00.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.SJCL;NO` | ST: `LTM00.D9PXAD.PE000.@@@@.@@@@@@@@.@@.SJCL` member `YO810BDD`=`st-content`; PR: `LTM00.D9PXAE.PE000.@@@@.@@@@@@@@.@@.SJCL` member `YO810BDD`=`pr-content`; TOCOLB: `LTM00.D9PXAD.PE000.TO@@.COLB@@@@.@@.SJCL` (empty dir) | ST member deleted; PR member untouched (`pr-content`); TOCOLB member created with content `pr-content` |
| 3 | pr-c-delete-jncs | PR | PROD-JOBZ | C | `edux0-jobz/$HXL007.STWSNCS` | `STWSNCS   ;LTM00.D9P${C1STAGEP}.PE000.@@@@.@@@@@@@@.@@.JNCS;NO` | PR: `LTM00.D9PXPE.PE000.@@@@.@@@@@@@@.@@.JNCS` member `$HXL007` | member deleted from PR dataset dir |
| 4 | st-s-sfilamento-jncs | ST | ST-JOBZ | S | `edux0-jobz/$HXL007.STWSNCS` | `STWSNCS   ;LTM00.D9P${C1STAGEP}.PE000.@@@@.@@@@@@@@.@@.JNCS;NO` | ST: `LTM00.D9PXAD.PE000.@@@@.@@@@@@@@.@@.JNCS` member `$HXL007`=`st-content`; PR: `LTM00.D9PXPE.PE000.@@@@.@@@@@@@@.@@.JNCS` member `$HXL007`=`pr-content`; TOCOLB: `LTM00.D9PXAD.PE000.TO@@.COLB@@@@.@@.JNCS` (empty dir) | ST member deleted; PR member untouched (`pr-content`); TOCOLB member created with content `pr-content` |

`stagemap.csv` fixture (existing `mac/src/test/resources/fixtures/stagemap.csv`) is reused unmodified for all 4 scenarios — it already contains the `01|ATO`, `01|ST`, `01|PR` etc. keys the rules reference via `${C1STAGE}`/`${C1STAGEP}`.

Per iteration:
1. `PuliziaCassaforteFixture` builds the layout above (dataset dirs/members, `rules.csv`, `stagemap.csv` copy, `PuliziaCassaforte.properties`, `simplelogger.properties`, `lista.csv`, deploys fixed `RunPuliziaCassaforte.groovy` + `FullPuliziaCassaforte.groovy`).
2. `ShRunner.run(mrunShFile, workDir, env, [listaPath, env, buildGroup])` where `env` map = `DBB_BUILD` → simulated dir, `DBB_CONF`/`DBB_HOME` → dummy non-null values (only presence-checked), `GROOVY_CLASSPATH` → `"<stubsJar>:<shLibDir>/*:<workDir>"` (system properties read from Gradle, see below).
3. Assert `result.exitCode == 0` and the per-scenario file-system post-conditions from the table.

### `mac/build.gradle`

Add to `test { }`:
- `dependsOn tasks.named('copyShLibs')` (already defined) so `build/sh-lib/slf4j-simple-*.jar` exists.
- `systemProperty 'stubsJar', project(':stubs').layout.buildDirectory.file('libs/stubs.jar').get().asFile.absolutePath`
- `systemProperty 'shLibDir', layout.buildDirectory.dir('sh-lib').get().asFile.absolutePath`
- `systemProperty 'mrunScript', file('src/main/script/mrunpc.sh').absolutePath`
- `systemProperty 'fatSourceFile', project(':fat-source').file('src/main/groovy/FullPuliziaCassaforte.groovy').absolutePath`
- `systemProperty 'frontEndRunnerFile', project(':front-end').file('src/main/groovy/RunPuliziaCassaforte.groovy').absolutePath`
- `dependsOn project(':stubs').tasks.named('jar')` so `stubsJar` exists before the test runs.

These mirror exactly what `testPuliziaCassaforte`'s `doFirst` block already computes for the Exec-task path — just exposed as JVM system properties for the new JUnit-run spec instead of shell env vars.

## Out of scope

- `PuliziaPostBuild`/`mrunppb.sh` coverage (explicitly excluded per scope decision — see `2026-07-07-mac-puliziapostbuild-spec-design.md` for that spec).
- Retiring or modifying `mrunpct2/7/8/9.sh`, `testPuliziaCassaforte`/`testPuliziaPostBuild` Exec tasks, `CliRunner.groovy`, `WrapperCliSpec.groovy`.
- Fixing the already-known-broken `ScriptLoader`-mock double (`mac/src/test/sh/RunPuliziaCassaforte.groovy`, missing `com.ibm.dbb.groovy.ScriptLoader` stub) — noted as a pre-existing issue, not touched here.
- `RunPuliziaCassaforte.groovy` never calling `System.exit()` on nonzero `errors` — left as-is; the spec compensates via file-system assertions.
