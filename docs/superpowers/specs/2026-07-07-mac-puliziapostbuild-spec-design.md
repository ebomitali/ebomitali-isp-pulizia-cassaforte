# mac: Spock spec for PuliziaPostBuild.groovy — design

## Purpose

`PuliziaPostBuild.groovy` (`front-end/src/main/groovy/`) is a DBB `type: task` step script: it runs *during* a DBB build, after a successful compile step, and deletes a member from the predecessor environment's cassaforte library (ST and PR only — see CLAUDE.md "Environment chain"). It declares `@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript`, which on real USS means the zBuilder engine injects `context` (`BuildContext`, build-scoped) and `config` (`TaskVariables`, task-scoped) into the script instance before calling its generated `run()`, and expects the script to `return` an `Integer` step return code.

This is a separate, later addition to `docs/superpowers/specs/2026-07-07-mac-mrun-spock-spec-design.md`, which explicitly scoped `PuliziaPostBuild`/`mrunppb.sh` out. That scoping stands: this spec does **not** touch `mac/src/main/script/mrunpc.sh`, `mac/src/main/script/mrunppb.sh`, `RunPuliziaCassaforte.groovy`, or the mrun-spec files from that design. `mac/src/main/script/mrunppb.sh` passes `SOURCE ENV BUILD_GROUP` as CLI args directly to `PuliziaPostBuild.groovy`, which the script never reads (it only reads `config`/`context`) — that gap is real but out of scope here per explicit decision: no new production wrapper is being added to bridge it; the test drives the script in-process instead (see below).

## Bugs found and fixed (all in `front-end/src/main/groovy/PuliziaPostBuild.groovy` unless noted)

1. **`config.get("file_path")` / `context.get("build_env")` / `context.get("build_group")`** — lowercase keys. Initially this looked like it should switch to `getStringVariable` with uppercase keys, but `library/src/main/groovy/com/intesasanpaolo/bes/pc/DbbBuildMapClient.groovy:70` already does `context.get('BUILD_GROUP') as BuildGroup` — a real, compiled-against call site, not stray legacy code. `.get(String)` with uppercase keys is the established convention here. Fix: keep `.get()`, just uppercase the keys:
   ```groovy
   String sourceFilePath = config.get("FILE_PATH")
   String environment     = context.get("BUILD_ENV")
   String buildGroup      = context.get("BUILD_GROUP")
   ```

2. **Duplicate `BuildContext` class** — `stubs/src/main/java/com/ibm/dbb/task/BuildContext.java` (`get(String)`, `getBuildFile()`, `getWorkingDirectory()` — the one `DbbBuildMapClient.groovy` actually depends on) and `stubs/src/main/groovy/com/ibm/dbb/task/BuildContext.groovy` (`getStringVariable`/`setVariable`/`getVariable`/`getIntVariable`/`getBooleanVariable`/`hasVariable`) define the same class in the same Gradle source set. Verified via `unzip -l stubs/build/libs/stubs.jar` + `javap` that only one class file survives — currently the Java one, silently shadowing the Groovy one. Since both APIs are genuinely needed (`DbbBuildMapClient` needs `.get()`, `TaskVariables`'s sibling methods are the documented DBB 3.0.3 convention for everything else), fix: merge both method sets into the single Groovy-source `BuildContext` class, delete the Java duplicate, and add a matching `get(String)` to `TaskVariables` (which had neither).

3. **`gcl.parseClass("${DBB_BUILD}/groovy/cassaforte/fatSourceFile")`** — same bug pattern as `RunPuliziaCassaforte.groovy` (string literal, undefined `DBB_BUILD` binding, nonexistent path). Fix (identical to the mrun-spec design):
   ```groovy
   gcl.parseClass(new File("${dbbBuild}/groovy/FullPuliziaCassaforte.groovy"))
   ```

4. **`puliziaCassaforteImpl.doPuliziaPostBuild(sourceFile, ...)`** passes a `File`, but the real signature (`fat-source/src/main/groovy/FullPuliziaCassaforte.groovy:1350`) is `doPuliziaPostBuild(String sourceToProcess, String environment, String buildGroup, Properties props)` — a `File` argument would throw `MissingMethodException` at runtime. Fix: pass the existing `sourceFilePath` String, not the `sourceFile` File wrapper (the File is still used for the `.exists()` pre-check, just not passed onward).

5. **Missing `Integer` return / `System.exit()` inside a task step.** The script's last statement is `if (errors > 0) System.exit(1)`, which yields no return value on the success path (`errors == 0` falls through with nothing returned) — CLAUDE.md documents this exact mistake as causing `BGZZB0043W` with a false RC 0. Separately, `type: task` steps run **in-process** inside the live DBB build JVM (unlike the standalone `PuliziaCassaforte.groovy`/`RunPuliziaCassaforte.groovy` path, which is a genuinely separate process) — calling `System.exit()` here would terminate the entire DBB build, not just this step. Fix: remove `System.exit`, end with an explicit `return errors`.

Fixed script tail:
```groovy
def gcl = new GroovyClassLoader(this.class.classLoader)
gcl.parseClass(new File("${dbbBuild}/groovy/FullPuliziaCassaforte.groovy"))
def clazz = gcl.loadClass('com.intesasanpaolo.bes.pc.PuliziaCassaforteImpl')
def puliziaCassaforteImpl = clazz.getDeclaredConstructor().newInstance()

int errors = puliziaCassaforteImpl.doPuliziaPostBuild(sourceFilePath, environment, buildGroup, cfgProps)
println "PuliziaCassaforte completed with ${errors} errors."
return errors
```

## Test shape: in-process, no subprocess

Unlike the mrun-spec design (which drives `mrunpc.sh` as a real subprocess because that's the actual USS entry point), `PuliziaPostBuild.groovy` is only ever invoked by the DBB engine in-process — there is no shell entry point to test here (see Purpose section on `mrunppb.sh`). `mac/build.gradle` already has `testImplementation project(':stubs')` and `testImplementation project(':fat-source')`, so `TaskVariables`, `BuildContext`, `TaskScript`, and `PuliziaCassaforteImpl` are all resolvable on the JVM test classpath directly — no `GROOVY_CLASSPATH`/`ProcessBuilder` plumbing needed.

### `mac/src/test/groovy/FakeTaskContext.groovy` (helper, new file)

`TaskVariables`/`BuildContext` are ordinary (non-final) Groovy classes in `stubs/`, compileOnly elsewhere but a normal `testImplementation` dependency of `mac`. Their real methods `throw new UnsupportedOperationException("stub - not for execution")` — subclass and override for the test:

```groovy
import com.ibm.dbb.task.TaskVariables
import com.ibm.dbb.task.BuildContext

class FakeTaskVariables extends TaskVariables {
    Map<String, String> vars = [:]
    @Override Object get(String name) { vars[name] }
}

class FakeBuildContext extends BuildContext {
    Map<String, String> vars = [:]
    @Override Object get(String name) { vars[name] }
}
```

### `mac/src/test/groovy/PuliziaPostBuildFixture.groovy` (helper, new file)

Builds the `$DBB_BUILD` layout (reusing the same `groovy/FullPuliziaCassaforte.groovy` + `build-data/{rules.csv,stagemap.csv}` shape as the mrun-spec fixture — this can share/reuse the mrun-spec's `PuliziaCassaforteFixture` dataset/config-writing methods once that spec exists; until then this spec's fixture duplicates the small subset it needs: `deployFatSource`, `writeRules`, `writeStageMap`, `dataset`/`member`), plus:

```groovy
class PuliziaPostBuildFixture {
    File dbbBuildDir
    Script loadPostBuild(File postBuildFile, File classLoaderBaseDir,
                          FakeTaskVariables config, FakeBuildContext context)
    // GroovyShell(this.class.classLoader).parse(postBuildFile) run with
    // classpath/CWD such that DBB_BUILD env var resolves; sets
    // .context/.config/.log on the parsed instance before returning it.
}
```

Env var `DBB_CONF`/`DBB_BUILD`/`DBB_HOME` are read via `System.getenv()` inside `PuliziaPostBuild.groovy`. Since the spec runs in-process (no subprocess), these are JVM-wide for the whole `test` task and cannot vary per iteration or per-test-method — Java offers no supported way to change `System.getenv()` after JVM start. Design accordingly: `DBB_BUILD`/`DBB_CONF`/`DBB_HOME` are set **once**, via `mac/build.gradle`'s `test { environment ... }`, pointing `DBB_BUILD` at a fixed `build/dbb-build-sim/` directory. `FullPuliziaCassaforte.groovy` doesn't vary between scenarios, so it's deployed once (`setupSpec()`). `rules.csv`/`stagemap.csv` under `build/dbb-build-sim/build-data/` are rewritten in-place at the start of each iteration (both scenarios use the same rules content here, so in practice this is also a one-time write — the fixture still exposes a per-iteration write method for scenarios that need to vary it). The PDS simulation root (`zosSimDir`) stays a fresh `@TempDir` per iteration, since `uxBasedir` is read from `PuliziaCassaforte.properties`, not an env var, and does need isolation per scenario.

### `mac/src/test/groovy/PuliziaPostBuildSpec.groovy`

Spock spec, `@TempDir Path zosSimDir` per iteration (dataset root only — `$DBB_BUILD` is the fixed `build/dbb-build-sim/` set once via Gradle `test.environment`). Two scenarios:

| # | name | env | buildGroup | source | rules.csv | dataset(s) | expected |
|---|------|-----|-----------|--------|-----------|-------------|----------|
| 1 | st-deletes-from-ato-predecessor | ST | ST | `ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLCA7/YO810BDD.SJCLCA7` | `SJCL*   ;LTM00.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.SJCL;NO` | ATO (predecessor) dataset `LTM00.D9PX2A.PE000.@@@@.@@@@@@@@.@@.SJCL` with member `YO810BDD` | `run()` returns `0`; `YO810BDD` deleted from the ATO dataset dir |
| 2 | ato-no-predecessor-cleanup-is-noop | ATO | ATO | same source | same rules | same ATO dataset, no ST predecessor dataset created | `run()` returns `0`; ATO's own `YO810BDD` member untouched (still exists) — `EnvironmentChain.requiresPrevEnvClean('ATO')` is false per CLAUDE.md ("Environment chain": only ST/PR require predecessor cleanup) |

Per iteration:
1. `PuliziaPostBuildFixture` rewrites `build/dbb-build-sim/build-data/rules.csv`, ensures `build/dbb-build-sim/groovy/FullPuliziaCassaforte.groovy` is deployed (idempotent, can be done once in `setupSpec()` instead of per-iteration since content doesn't vary).
2. Seeds `zosSimDir` with the scenario's dataset dir(s)/member(s).
3. Writes `PuliziaCassaforte.properties` (`uxBasedir=<zosSimDir>`, `rulesPath`/`stageMapPath` pointing at `build/dbb-build-sim/build-data/...`) to a location `PuliziaPostBuild.groovy` will find via `FileInputStream("PuliziaCassaforte.properties")` — since there's no subprocess/CWD control here, this requires either (a) running the `GroovyShell`-parsed script with the JVM's actual working directory temporarily changed (fragile, same class of problem as `DBB_BUILD`), or (b) — **preferred** — changing `PuliziaCassaforte.properties` loading to an absolute/configurable path is out of scope (production file, already covered by the mrun-spec design without needing this change, since that spec controls subprocess CWD directly). For this in-process spec: run each iteration's JVM-relative "current directory" concern away by writing `PuliziaCassaforte.properties` into the real process CWD equivalent Gradle already uses for the `test` task (`workingDir` of the `Test` task, configurable in `mac/build.gradle` via `test { workingDir = file("build/postbuild-test-cwd") }`), and have the fixture write/overwrite `PuliziaCassaforte.properties` there per iteration.
4. Constructs `FakeTaskVariables(vars: [FILE_PATH: source])` and `FakeBuildContext(vars: [BUILD_ENV: env, BUILD_GROUP: buildGroup])`, loads + runs the script via the fixture.
5. Asserts the returned `Integer` and the file-system post-condition from the table.

## Files touched

- `front-end/src/main/groovy/PuliziaPostBuild.groovy` — 5 fixes above.
- `stubs/src/main/groovy/com/ibm/dbb/task/BuildContext.groovy` — merged with the Java stub's methods.
- `stubs/src/main/groovy/com/ibm/dbb/task/TaskVariables.groovy` — `get(String)` added.
- `stubs/src/main/java/com/ibm/dbb/task/BuildContext.java` — deleted.
- `mac/src/test/groovy/FakeTaskContext.groovy` — new.
- `mac/src/test/groovy/PuliziaPostBuildFixture.groovy` — new.
- `mac/src/test/groovy/PuliziaPostBuildSpec.groovy` — new.
- `mac/build.gradle` — `test { workingDir = file("build/postbuild-test-cwd"); environment 'DBB_BUILD', file("build/dbb-build-sim").absolutePath; environment 'DBB_CONF', '(unused-dummy)'; environment 'DBB_HOME', '(unused-dummy)' }`, plus a `doFirst`/dependency ensuring `build/dbb-build-sim/groovy/FullPuliziaCassaforte.groovy` exists before tests run (copy from `project(':fat-source')`).

## Out of scope

- `mrunppb.sh` (both `mac/src/main/script/mrunppb.sh` and the `mac/src/test/sh/mac/mrunppb.sh` shell test) — not exercised; no production wrapper added to bridge its CLI-args-vs-config/context gap, per explicit decision.
- Everything already out of scope in the mrun-spec design (`RunPuliziaCassaforte.groovy`/`mrunpc.sh` coverage itself, retiring existing shell tests, `CliRunner`/`WrapperCliSpec`).
- `PrevEnvCleanLogic`/`DeleteCassaforteLogic` business-logic correctness beyond the two scenarios above — already covered by `fat-source`'s own unit Specs (`PrevEnvCleanLogicSpec.groovy`, `DeleteCassaforteLogicSpec.groovy`); this spec only verifies the `PuliziaPostBuild.groovy` ↔ `PuliziaCassaforteImpl` ↔ `TaskScript` interface wiring.
