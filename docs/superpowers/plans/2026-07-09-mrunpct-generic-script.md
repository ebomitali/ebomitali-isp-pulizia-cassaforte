# mrunpct Generic Script + Config Files Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the four near-duplicate shell scripts in `mac/src/test/sh/mac/` (`mrunpct2.sh`, `mrunpct7.sh`, `mrunpct8.sh`, `mrunpct9.sh`) with one generic runner (`mrunpct.sh`) driven by per-scenario config files, so adding a new scenario means writing a config file, not copy-pasting ~120 lines of shell.

**Architecture:** All four scripts already share the same skeleton (write rules.csv/PuliziaCassaforte.properties/simplelogger.properties → seed a simulated z/OS PDS tree under a temp dir → copy `FullPuliziaCassaforte.groovy`/`RunPuliziaCassaforte.groovy` alongside the script → run `groovy RunPuliziaCassaforte.groovy lista.csv $ENV $BUILD_GROUP` → assert on the resulting files) and differ only in *data* (env, build group, source list, rules, seeded datasets, expected outcomes). Extract that data into four POSIX-sh config files (`conf/pct2.conf`, `conf/pct7.conf`, `conf/pct8.conf`, `conf/pct9.conf`) that `mrunpct.sh` sources, and drive dataset seeding / assertions generically from newline-delimited records instead of hand-written per-script blocks.

**Tech Stack:** POSIX `sh` (no bashisms — matches the existing scripts' `#!/bin/sh` shebang and array-free style), Groovy 4 (`groovy` CLI, not `groovyz`), the `stubs`/`fat-source`/`front-end` Gradle modules already in this repo.

## Global Constraints

- POSIX `sh` only — no arrays, no `[[ ]]`, no `local` (the existing scripts already follow this; don't introduce bashisms).
- Config files are sourced (`. "$CONFIG_FILE"`), not parsed — they are plain shell variable assignments.
- Values containing `$` (e.g. `$HXL007`) or `${...}` (e.g. `${C1STAGE}`) that must NOT be shell-expanded are single-quoted in config files — this matches how the original scripts already escape them (e.g. `SLIST="edux0-jobz/\$HXL007.STWSNCS"`).
- Preserve each scenario's existing test data and pass/fail behavior exactly — this is a structural refactor, not a rewrite of what's being tested. Do not "fix" scenario data even where it looks inconsistent with current fixtures (see Task 5 note on `pct2.conf`).
- Gradle wiring (making `testPuliziaCassaforte` or a new task invoke `mrunpct.sh`) is explicitly out of scope for this plan — these scripts remain manually-run local dev tools, exactly as they are today.

---

### Task 1: Fix `RunPuliziaCassaforte.groovy`'s broken `parseClass` target

**Files:**
- Modify: `front-end/src/main/groovy/RunPuliziaCassaforte.groovy`

**Interfaces:**
- Consumes: nothing new — same CLI contract (`args[0]`=lista path, `args[1]`=environment, `args[2]`=buildGroup), same `DBB_CONF`/`DBB_BUILD`/`DBB_HOME` env vars it already reads via `System.getenv(...)`.
- Produces: a working `groovy RunPuliziaCassaforte.groovy <lista> <env> <buildGroup>` invocation, given `DBB_BUILD` points at a directory containing `groovy/FullPuliziaCassaforte.groovy`. All later tasks depend on this working.

**Why:** None of `mrunpct2.sh`/`mrunpct7.sh`/`mrunpct8.sh`/`mrunpct9.sh` can currently pass, because `RunPuliziaCassaforte.groovy` line 67 does `gcl.parseClass("${DBB_BUILD}/groovy/cassaforte/fatSourceFile")` — a `String` passed where `parseClass` expects a `File` (or source text), interpolating an undefined binding `DBB_BUILD` (the local var is lowercase `dbbBuild`), pointing at a path that doesn't correspond to any real deployed file. This is the exact same bug class already fixed today in `front-end/src/main/groovy/PuliziaPostBuild.groovy` (see `git log` on that file for the identical fix pattern) — same fix here, applied to the sibling script.

- [ ] **Step 1: Read the current file to confirm the exact text before editing**

```bash
cat front-end/src/main/groovy/RunPuliziaCassaforte.groovy
```

Confirm line 67 (or nearby) reads exactly:
```groovy
def gcl = new GroovyClassLoader(this.class.classLoader)
gcl.parseClass("${DBB_BUILD}/groovy/cassaforte/fatSourceFile")
def clazz = gcl.loadClass('com.intesasanpaolo.bes.pc.PuliziaCassaforteImpl')
def puliziaCassaforteImpl = clazz.getDeclaredConstructor().newInstance()

int errors = puliziaCassaforteImpl.doPuliziaCassaforte(sourcesListFile, environment, buildGroup, cfgProps)
println "PuliziaCassaforte completed with ${errors} errors."
```
If it doesn't match, STOP and report — the file has changed since this plan was written.

- [ ] **Step 2: Fix the `parseClass` line**

Replace:
```groovy
gcl.parseClass("${DBB_BUILD}/groovy/cassaforte/fatSourceFile")
```
With:
```groovy
gcl.parseClass(new File("${dbbBuild}/groovy/FullPuliziaCassaforte.groovy"))
```
(`dbbBuild` is the existing local var from `String dbbBuild = System.getenv("DBB_BUILD")`, already null-checked earlier in the file — do not rename it, just use it.)

- [ ] **Step 3: Verify with a manual smoke test**

```bash
PROJECT_ROOT="$(pwd)"
SMOKE_DIR="$(mktemp -d)"
mkdir -p "$SMOKE_DIR/groovy"
cp "$PROJECT_ROOT/fat-source/src/main/groovy/FullPuliziaCassaforte.groovy" "$SMOKE_DIR/groovy/FullPuliziaCassaforte.groovy"
cp "$PROJECT_ROOT/front-end/src/main/groovy/RunPuliziaCassaforte.groovy" "$SMOKE_DIR/RunPuliziaCassaforte.groovy"

cat > "$SMOKE_DIR/PuliziaCassaforte.properties" <<EOF
fileOpsType=macos
buildMapClientType=json
buildMapPath=$PROJECT_ROOT/mac/src/test/resources/fixtures/buildmap.json
uxBasedir=$SMOKE_DIR/zos-sim
rulesPath=$SMOKE_DIR/rules.csv
stageMapPath=$PROJECT_ROOT/mac/src/test/resources/fixtures/stagemap.csv
EOF
printf 'SJCL*   ;LTM00.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.SJCL;NO' > "$SMOKE_DIR/rules.csv"
: > "$SMOKE_DIR/lista.csv"

STUBS_DIR="$SMOKE_DIR/stubs"
mkdir -p "$STUBS_DIR"
find "$PROJECT_ROOT/stubs/src/main/java" -name "*.java" | xargs javac -d "$STUBS_DIR"

export DBB_BUILD="$SMOKE_DIR"
export DBB_CONF="$SMOKE_DIR"
export DBB_HOME="$SMOKE_DIR"

cd "$SMOKE_DIR"
groovy -cp "$STUBS_DIR:$SMOKE_DIR" RunPuliziaCassaforte.groovy lista.csv ATO ATO
```
Expected: `PuliziaCassaforte completed with 0 errors.` printed, no stack trace, no `MissingPropertyException`/`GroovyRuntimeException`. (Empty `lista.csv` means zero sources processed — this smoke test only proves the classloading/wiring path works, not full scenario behavior; that's covered by Tasks 2-5.)

Clean up:
```bash
rm -rf "$SMOKE_DIR"
```

- [ ] **Step 4: Commit**

```bash
git add front-end/src/main/groovy/RunPuliziaCassaforte.groovy
git commit -m "$(cat <<'EOF'
Fix RunPuliziaCassaforte.groovy's broken parseClass target

Same bug already fixed in PuliziaPostBuild.groovy: parseClass was
called with a String literal interpolating an undefined DBB_BUILD
binding (should have been the lowercase local var dbbBuild) pointing
at a nonexistent path. Fix: parseClass(new File("${dbbBuild}/groovy/
FullPuliziaCassaforte.groovy")), matching where DBB deploys sources
per docs/deploy-strategies.md.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Generic runner + first config (`pct7`)

**Files:**
- Create: `mac/src/test/sh/mac/mrunpct.sh`
- Create: `mac/src/test/sh/mac/conf/pct7.conf`

**Interfaces:**
- Consumes: `front-end/src/main/groovy/RunPuliziaCassaforte.groovy` (fixed in Task 1), `fat-source/src/main/groovy/FullPuliziaCassaforte.groovy`, `stubs/build/libs/stubs.jar` (prebuilt via `./gradlew :stubs:jar` — the java-only `javac`-compiled-on-the-fly approach the original scripts used doesn't cover `com.ibm.dbb.task.BuildContext`, which is a Groovy stub under `stubs/src/main/groovy/`, not `stubs/src/main/java/` — Task 1's implementer hit exactly this gap), `mac/src/test/resources/fixtures/{buildmap.json,stagemap.csv}`.
- Produces: `mrunpct.sh <config-file>` — the CLI contract every later config file (Tasks 3-5) is written against. Config file variables it reads: `ENV`, `BUILD_GROUP`, `LISTA_ENTRIES` (newline-separated `action;sourcePath` records), `RULES_CONTENT` (raw rules.csv text), `DATASETS` (newline-separated `dsName;member;content` records, `member`/`content` may be empty to create an empty dataset dir), `ASSERTIONS` (newline-separated `dsName;member;expectExists(0|1);expectedContent` records, `expectedContent` empty = skip content check, may be entirely empty to skip assertions).

**Why:** This is the payoff task — the generic script plus the most feature-complete config (`pct7`: three seeded datasets, four assertions, `S` sfilamento action) proves the abstraction covers everything the original scripts did before the simpler configs (Tasks 3-5) are written against it.

- [ ] **Step 1: Create the config directory and `conf/pct7.conf`**

```bash
mkdir -p mac/src/test/sh/mac/conf
```

Write `mac/src/test/sh/mac/conf/pct7.conf`:
```sh
# Scenario: ST env, sfilamento (S) of a single SJCL* source.
# Ported from the original mrunpct7.sh — see git history for that file if you need
# to compare byte-for-byte.

ENV="ST"
BUILD_GROUP="ST-MAIN"

LISTA_ENTRIES='S;ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLCA7/YO810BDD.SJCLCA7'

RULES_CONTENT='SJCL*   ;LTM00.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.SJCL;NO'

# dsName;member;content  (member/content empty => dataset dir created but left empty)
DATASETS='
LTM00.D9PXAD.PE000.@@@@.@@@@@@@@.@@.SJCL;YO810BDD;st-content
LTM00.D9PXAE.PE000.@@@@.@@@@@@@@.@@.SJCL;YO810BDD;pr-content
LTM00.D9PXAD.PE000.TO@@.COLB@@@@.@@.SJCL;;
'

# dsName;member;expectExists(0|1);expectedContent  (expectedContent empty => skip content check)
ASSERTIONS='
LTM00.D9PXAD.PE000.@@@@.@@@@@@@@.@@.SJCL;YO810BDD;0;
LTM00.D9PXAE.PE000.@@@@.@@@@@@@@.@@.SJCL;YO810BDD;1;pr-content
LTM00.D9PXAD.PE000.TO@@.COLB@@@@.@@.SJCL;YO810BDD;1;pr-content
'
```

- [ ] **Step 2: Create `mac/src/test/sh/mac/mrunpct.sh`**

```sh
#!/bin/sh
# Generic runner for the mrunpct* local (Mac) FullPuliziaCassaforte/RunPuliziaCassaforte
# test scenarios. Scenario-specific data lives in conf/<name>.conf.
# Usage: mrunpct.sh <config-file>

set -e

CONFIG_FILE="${1:?Usage: mrunpct.sh <config-file>}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SUBPROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../../.." && pwd)"

# shellcheck disable=SC1090
. "$CONFIG_FILE"

: "${ENV:?ENV must be set in $CONFIG_FILE}"
: "${BUILD_GROUP:?BUILD_GROUP must be set in $CONFIG_FILE}"
: "${LISTA_ENTRIES:?LISTA_ENTRIES must be set in $CONFIG_FILE}"
: "${RULES_CONTENT:?RULES_CONTENT must be set in $CONFIG_FILE}"

TEMP_DIR="${TMPDIR:-/tmp/}run-puliziacassaforte.$$"
mkdir -p "$TEMP_DIR"

cleanup() {
    rm -f "$SCRIPT_DIR/RunPuliziaCassaforte.groovy"
    rm -f "$SCRIPT_DIR/PuliziaCassaforte.properties"
    rm -f "$SCRIPT_DIR/simplelogger.properties"
    rm -f "$SCRIPT_DIR/lista.csv"
    rm -f "$SCRIPT_DIR/rules.csv"
    rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

resource_file() {
    echo "$SUBPROJECT_ROOT/src/test/resources/fixtures/$1"
}

write_rules() {
    printf '%s\n' "$RULES_CONTENT" > "$SCRIPT_DIR/rules.csv"
}

write_config() {
    _cfg="$SCRIPT_DIR/PuliziaCassaforte.properties"
    printf 'fileOpsType=%s\n'        "macos"                              >  "$_cfg"
    printf 'buildMapClientType=%s\n' "json"                               >> "$_cfg"
    printf 'buildMapPath=%s\n'       "$(resource_file 'buildmap.json')"   >> "$_cfg"
    printf 'uxBasedir=%s\n'          "$TEMP_DIR"                          >> "$_cfg"
    printf 'rulesPath=%s\n'          "$SCRIPT_DIR/rules.csv"              >> "$_cfg"
    printf 'stageMapPath=%s\n'       "$(resource_file 'stagemap.csv')"    >> "$_cfg"
}

write_simplelogger_config() {
    _slf4j_cfg="$SCRIPT_DIR/simplelogger.properties"
    printf 'org.slf4j.simpleLogger.defaultLogLevel=%s\n' "debug"                  >  "$_slf4j_cfg"
    printf 'org.slf4j.simpleLogger.showLogName=%s\n' "true"                       >> "$_slf4j_cfg"
    printf 'org.slf4j.simpleLogger.showThreadName=%s\n' "true"                    >> "$_slf4j_cfg"
    printf 'org.slf4j.simpleLogger.showDateTime=%s\n' "true"                      >> "$_slf4j_cfg"
    printf 'org.slf4j.simpleLogger.dateTimeFormat=%s\n' "yyyy-MM-dd HH:mm:ss:SSS" >> "$_slf4j_cfg"
    printf 'org.slf4j.simpleLogger.logFile=%s\n' "System.out"                     >> "$_slf4j_cfg"
}

write_lista() {
    _lista="$SCRIPT_DIR/lista.csv"
    rm -f "$_lista"
    while IFS=';' read -r action srcpath; do
        [ -z "$action" ] && continue
        printf '%s,%s\n' "$action" "$srcpath" >> "$_lista"
    done <<EOF
$LISTA_ENTRIES
EOF
    echo "$_lista"
}

seed_datasets() {
    [ -z "$DATASETS" ] && return 0
    while IFS=';' read -r dsname member content; do
        [ -z "$dsname" ] && continue
        _dsdir="$TEMP_DIR/$dsname"
        mkdir -p "$_dsdir"
        if [ -n "$member" ]; then
            printf '%s' "$content" > "$_dsdir/$member"
        fi
    done <<EOF
$DATASETS
EOF
}

check_assertions() {
    [ -z "$ASSERTIONS" ] && return 0
    _fail_marker="$TEMP_DIR/.assertion_failed"
    rm -f "$_fail_marker"
    while IFS=';' read -r dsname member expect_exists expect_content; do
        [ -z "$dsname" ] && continue
        _path="$TEMP_DIR/$dsname/$member"
        if [ "$expect_exists" = "1" ]; then
            if [ -f "$_path" ]; then
                echo "Verified: $_path exists"
            else
                echo "Test failed: expected file $_path to exist, but it does not"
                touch "$_fail_marker"
            fi
            if [ -n "$expect_content" ]; then
                _actual="$(cat "$_path" 2>/dev/null || true)"
                if [ "$_actual" = "$expect_content" ]; then
                    echo "Verified: $_path content is correct ($expect_content)"
                else
                    echo "Test failed: expected content '$expect_content' in $_path, but found '$_actual'"
                    touch "$_fail_marker"
                fi
            fi
        else
            if [ -f "$_path" ]; then
                echo "Test failed: expected file $_path to be deleted, but it exists"
                touch "$_fail_marker"
            else
                echo "Verified: $_path was deleted"
            fi
        fi
    done <<EOF
$ASSERTIONS
EOF
    [ ! -f "$_fail_marker" ]
}

write_rules
write_config
write_simplelogger_config
lista=$(write_lista)
seed_datasets

echo "Files to be processed:"
cat "$lista"

STUBS_JAR="$PROJECT_ROOT/stubs/build/libs/stubs.jar"
if [ ! -f "$STUBS_JAR" ]; then
    echo "ERROR: $STUBS_JAR not found. Run './gradlew :stubs:jar' once first." >&2
    exit 1
fi
SH_LIB="$SUBPROJECT_ROOT/build/sh-lib"

cp "$PROJECT_ROOT/front-end/src/main/groovy/RunPuliziaCassaforte.groovy" "$SCRIPT_DIR/RunPuliziaCassaforte.groovy"

# RunPuliziaCassaforte.groovy resolves FullPuliziaCassaforte.groovy via
# ${DBB_BUILD}/groovy/FullPuliziaCassaforte.groovy (GroovyClassLoader.parseClass), not the
# classpath — deploy it there, not alongside the runner script.
mkdir -p "$TEMP_DIR/groovy"
cp "$PROJECT_ROOT/fat-source/src/main/groovy/FullPuliziaCassaforte.groovy" "$TEMP_DIR/groovy/FullPuliziaCassaforte.groovy"

result=0
cd "$SCRIPT_DIR"
export DBB_BUILD="$TEMP_DIR"
export DBB_CONF="$TEMP_DIR"
export DBB_HOME="$TEMP_DIR"
groovy -cp "$STUBS_JAR:$SH_LIB/*:$SCRIPT_DIR" RunPuliziaCassaforte.groovy "$lista" "$ENV" "$BUILD_GROUP" || result=$?

if ! check_assertions; then
    result=1
fi

if [ "$result" -eq 0 ]; then
    echo "Test passed: no errors"
else
    echo "Test failed: errors detected (exit code: $result)"
    exit 1
fi
```

- [ ] **Step 3: Make it executable**

```bash
chmod +x mac/src/test/sh/mac/mrunpct.sh
```

- [ ] **Step 4: Run it and verify it passes**

Prerequisites: `mac/build/sh-lib/slf4j-simple-*.jar` must exist (the slf4j provider the script's classpath expects — if missing, run `./gradlew :mac:copyShLibs` once) and `stubs/build/libs/stubs.jar` must exist (if missing, run `./gradlew :stubs:jar` once — `mrunpct.sh` checks for this itself and exits with a clear error if it's absent).

```bash
mac/src/test/sh/mac/mrunpct.sh mac/src/test/sh/mac/conf/pct7.conf
```
Expected output includes, in order:
- `Files to be processed:` followed by `S,ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLCA7/YO810BDD.SJCLCA7`
- `Verified: .../LTM00.D9PXAD.PE000.@@@@.@@@@@@@@.@@.SJCL/YO810BDD was deleted`
- `Verified: .../LTM00.D9PXAE.PE000.@@@@.@@@@@@@@.@@.SJCL/YO810BDD exists`
- `Verified: .../LTM00.D9PXAE.PE000.@@@@.@@@@@@@@.@@.SJCL/YO810BDD content is correct (pr-content)`
- `Verified: .../LTM00.D9PXAD.PE000.TO@@.COLB@@@@.@@.SJCL/YO810BDD exists`
- `Verified: .../LTM00.D9PXAD.PE000.TO@@.COLB@@@@.@@.SJCL/YO810BDD content is correct (pr-content)`
- `Test passed: no errors`

Exit code must be `0` (check with `echo $?` immediately after).

If it fails, compare against the original `mrunpct7.sh`'s behavior (do not delete `mrunpct7.sh` yet — it's removed in Task 6, keep it around for comparison during Tasks 2-5) to isolate whether the generic script or the config data is wrong.

- [ ] **Step 5: Commit**

```bash
git add mac/src/test/sh/mac/mrunpct.sh mac/src/test/sh/mac/conf/pct7.conf
git commit -m "$(cat <<'EOF'
Add generic mrunpct.sh runner + pct7 config

First step of replacing the four near-duplicate mrunpct{2,7,8,9}.sh
scripts: a single runner driven by newline-delimited config records
(LISTA_ENTRIES/DATASETS/ASSERTIONS), proven against the most
feature-complete original scenario (ST sfilamento, 3 seeded datasets,
4 assertions). mrunpct7.sh itself is untouched for now — removed in a
later task once all four configs are verified.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: `pct8` config (simple `C`-flag delete)

**Files:**
- Create: `mac/src/test/sh/mac/conf/pct8.conf`

**Interfaces:**
- Consumes: `mrunpct.sh` from Task 2 (unchanged).
- Produces: nothing new for later tasks — this is a leaf config.

**Why:** Proves the generic script handles the simplest case correctly: single dataset, single assertion, `C` (plain delete) action instead of `S` (sfilamento).

- [ ] **Step 1: Write `mac/src/test/sh/mac/conf/pct8.conf`**

```sh
# Scenario: PR env, C flag, delete-only from PR (no sfilamento).
# Ported from the original mrunpct8.sh.

ENV="PR"
BUILD_GROUP="PROD-JOBZ"

LISTA_ENTRIES='C;edux0-jobz/$HXL007.STWSNCS'

RULES_CONTENT='STWSNCS   ;LTM00.D9P${C1STAGEP}.PE000.@@@@.@@@@@@@@.@@.JNCS;NO'

DATASETS='
LTM00.D9PXPE.PE000.@@@@.@@@@@@@@.@@.JNCS;$HXL007;pr-content
'

ASSERTIONS='
LTM00.D9PXPE.PE000.@@@@.@@@@@@@@.@@.JNCS;$HXL007;0;
'
```

- [ ] **Step 2: Run it and verify it passes**

```bash
mac/src/test/sh/mac/mrunpct.sh mac/src/test/sh/mac/conf/pct8.conf
```
Expected output includes:
- `Files to be processed:` followed by `C,edux0-jobz/$HXL007.STWSNCS`
- `Verified: .../LTM00.D9PXPE.PE000.@@@@.@@@@@@@@.@@.JNCS/$HXL007 was deleted`
- `Test passed: no errors`

Exit code `0`.

- [ ] **Step 3: Commit**

```bash
git add mac/src/test/sh/mac/conf/pct8.conf
git commit -m "$(cat <<'EOF'
Add pct8 config: PR env, plain C-flag delete

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: `pct9` config (sfilamento with `$`-prefixed member name)

**Files:**
- Create: `mac/src/test/sh/mac/conf/pct9.conf`

**Interfaces:**
- Consumes: `mrunpct.sh` from Task 2 (unchanged).
- Produces: nothing new for later tasks — this is a leaf config.

**Why:** Same sfilamento shape as `pct7` but with a JOBZ-type source (`STWSNCS` extension, `$`-prefixed member name) — proves the config format correctly round-trips a literal `$` through `LISTA_ENTRIES`/`DATASETS`/`ASSERTIONS` without the shell trying to expand it as a variable reference (this is why those values are single-quoted in the config file).

- [ ] **Step 1: Write `mac/src/test/sh/mac/conf/pct9.conf`**

```sh
# Scenario: ST env, sfilamento (S) of a JOBZ-type source with a $-prefixed member name.
# Ported from the original mrunpct9.sh.

ENV="ST"
BUILD_GROUP="ST-JOBZ"

LISTA_ENTRIES='S;edux0-jobz/$HXL007.STWSNCS'

RULES_CONTENT='STWSNCS   ;LTM00.D9P${C1STAGEP}.PE000.@@@@.@@@@@@@@.@@.JNCS;NO'

DATASETS='
LTM00.D9PXAD.PE000.@@@@.@@@@@@@@.@@.JNCS;$HXL007;st-content
LTM00.D9PXPE.PE000.@@@@.@@@@@@@@.@@.JNCS;$HXL007;pr-content
LTM00.D9PXAD.PE000.TO@@.COLB@@@@.@@.JNCS;;
'

ASSERTIONS='
LTM00.D9PXAD.PE000.@@@@.@@@@@@@@.@@.JNCS;$HXL007;0;
LTM00.D9PXPE.PE000.@@@@.@@@@@@@@.@@.JNCS;$HXL007;1;pr-content
LTM00.D9PXAD.PE000.TO@@.COLB@@@@.@@.JNCS;$HXL007;1;pr-content
'
```

- [ ] **Step 2: Run it and verify it passes**

```bash
mac/src/test/sh/mac/mrunpct.sh mac/src/test/sh/mac/conf/pct9.conf
```
Expected output includes:
- `Files to be processed:` followed by `S,edux0-jobz/$HXL007.STWSNCS`
- `Verified: .../LTM00.D9PXAD.PE000.@@@@.@@@@@@@@.@@.JNCS/$HXL007 was deleted`
- `Verified: .../LTM00.D9PXPE.PE000.@@@@.@@@@@@@@.@@.JNCS/$HXL007 exists`
- `Verified: .../LTM00.D9PXPE.PE000.@@@@.@@@@@@@@.@@.JNCS/$HXL007 content is correct (pr-content)`
- `Verified: .../LTM00.D9PXAD.PE000.TO@@.COLB@@@@.@@.JNCS/$HXL007 exists`
- `Verified: .../LTM00.D9PXAD.PE000.TO@@.COLB@@@@.@@.JNCS/$HXL007 content is correct (pr-content)`
- `Test passed: no errors`

Exit code `0`. Pay particular attention to the `$HXL007` in the output — if the shell expanded it as a variable, you'd see an empty string instead of the literal text `$HXL007` in paths; if you see that, the single-quoting in the config file was lost somewhere (check `write_lista`/`seed_datasets`/`check_assertions` in `mrunpct.sh` use `<<EOF ... EOF` heredocs, not double-quoted `echo`, which would re-expand).

- [ ] **Step 3: Commit**

```bash
git add mac/src/test/sh/mac/conf/pct9.conf
git commit -m "$(cat <<'EOF'
Add pct9 config: ST env, sfilamento of a JOBZ-type source

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: `pct2` config (multi-source `C`-flag batch)

**Files:**
- Create: `mac/src/test/sh/mac/conf/pct2.conf`

**Interfaces:**
- Consumes: `mrunpct.sh` from Task 2 (unchanged).
- Produces: nothing new for later tasks — this is a leaf config.

**Why:** Proves the config format handles a source list with more than one entry, and a dataset with more than one seeded member (three sources, three members in one dataset dir) — the shape `mrunpct2.sh` originally tested, which none of `pct7`/`pct8`/`pct9` cover.

**Note on scenario data:** The original `mrunpct2.sh` used dataset dir `U0G9700.D9PX1A.PE000.@@@@.JCL` (`X1A`) for env `ATO`. The current `stagemap.csv` fixture maps `"01|ATO"` to `X2A`, not `X1A` (`X1A` is `ATI1`/`ATI2`). This mismatch already existed in the original script and was never caught because `mrunpct2.sh` had no per-file assertions (only an exit-code check) — `PuliziaCassaforteImpl` doesn't error when a rule resolves to a library with no matching file, it just performs zero deletions silently. Per the Global Constraints, this plan preserves that existing behavior/data as-is rather than "fixing" it — do not add assertions or correct the dataset name; that would change what's being tested, which is out of scope here. If you want the data corrected, that's a separate, follow-up change.

- [ ] **Step 1: Write `mac/src/test/sh/mac/conf/pct2.conf`**

```sh
# Scenario: ATO env, C flag, three sources in one batch.
# Ported from the original mrunpct2.sh, data preserved as-is (see plan notes on the
# X1A/X2A stagemap mismatch — not fixed here, out of scope for this refactor).

ENV="ATO"
BUILD_GROUP="ATO"

LISTA_ENTRIES='
C;ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLCA7/YO810BDD.SJCLCA7
C;ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YO8AMBDD.SJCLINP
C;ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLITT/YO84XS1.SJCLITT
'

RULES_CONTENT='SJCL*   ;LTM00.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.SJCL;NO'

DATASETS='
U0G9700.D9PX1A.PE000.@@@@.JCL;YO810BDD;
U0G9700.D9PX1A.PE000.@@@@.JCL;YO8AMBDD;
U0G9700.D9PX1A.PE000.@@@@.JCL;YO84XS1;
'

ASSERTIONS=''
```

- [ ] **Step 2: Run it and verify it passes**

```bash
mac/src/test/sh/mac/mrunpct.sh mac/src/test/sh/mac/conf/pct2.conf
```
Expected output includes:
- `Files to be processed:` followed by all three `C,...` lines
- `Test passed: no errors`

Exit code `0`. (No `Verified:`/dataset-content assertions expected — `ASSERTIONS` is empty for this scenario, matching the original script.)

- [ ] **Step 3: Commit**

```bash
git add mac/src/test/sh/mac/conf/pct2.conf
git commit -m "$(cat <<'EOF'
Add pct2 config: ATO env, three-source C-flag batch

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Remove the superseded scripts, final regression pass

**Files:**
- Delete: `mac/src/test/sh/mac/mrunpct2.sh`
- Delete: `mac/src/test/sh/mac/mrunpct7.sh`
- Delete: `mac/src/test/sh/mac/mrunpct8.sh`
- Delete: `mac/src/test/sh/mac/mrunpct9.sh`

**Interfaces:**
- Consumes: `mrunpct.sh` + all four `conf/*.conf` files (Tasks 2-5), already independently verified.
- Produces: nothing — this is the final cleanup task.

**Why:** All four scenarios are now covered by `mrunpct.sh` + config, verified individually in Tasks 2-5. The original scripts are now dead weight — keeping both versions around invites drift (someone edits one and forgets the other).

- [ ] **Step 1: Re-run all four configs in one pass, back to back, to confirm no cross-contamination**

```bash
for conf in mac/src/test/sh/mac/conf/*.conf; do
    echo "=== $conf ==="
    mac/src/test/sh/mac/mrunpct.sh "$conf"
    echo "=== $conf: exit $? ==="
done
```
Expected: all four print `Test passed: no errors` and `=== ...: exit 0 ===`. Since each run uses its own `$$`-suffixed temp dir and cleans up via `trap cleanup EXIT`, there's no shared state between runs — if one run's leftover state affects another, that's a real bug in `mrunpct.sh`'s cleanup and must be fixed before proceeding (do not just delete the old scripts and hope).

- [ ] **Step 2: Remove the superseded scripts**

```bash
git rm mac/src/test/sh/mac/mrunpct2.sh mac/src/test/sh/mac/mrunpct7.sh mac/src/test/sh/mac/mrunpct8.sh mac/src/test/sh/mac/mrunpct9.sh
```

- [ ] **Step 3: Grep the repo for any remaining reference to the removed filenames**

```bash
grep -rn "mrunpct2\.sh\|mrunpct7\.sh\|mrunpct8\.sh\|mrunpct9\.sh" --include="*.gradle" --include="*.sh" --include="*.md" . 2>/dev/null
```
Expected: no output (these four scripts were never wired into `mac/build.gradle` — confirmed unwired at the start of this plan — so no build file should reference them). If something does reference them, STOP and report rather than silently breaking it.

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
Remove mrunpct{2,7,8,9}.sh, superseded by mrunpct.sh + conf/

All four scenarios verified equivalent under the generic runner in
prior commits. Adding a new scenario going forward means writing a
conf/*.conf file, not copy-pasting a ~120-line shell script.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```
