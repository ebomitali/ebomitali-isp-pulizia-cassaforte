# Rules CSV Macro Resolution — Design

Date: 2026-05-21

## Goal

Generalize `LibraryNameResolver` to resolve any `${VARNAME}` macro in `rules.csv` library templates using a per-file variable map, and wire up correct per-file derivation of `C1STAGE`, `C1SYSTEM`, and `HLQ`.

---

## Variables

| Macro | Origin | Per-file? |
|---|---|---|
| `${C1SYSTEM}` | Source path segment token[1] | Yes |
| `${C1STAGE}` | `stage-map.csv` lookup: `PATH_LO\|BUILD_ENV` | Yes (PATH_LO from path) |
| `${HLQ}` | CLI parameter `--hlq` | No (same for all files in a run) |

`PATH_LO` is an intermediate value (not a template macro): token[2] from the source path segment, used only to look up C1STAGE.

---

## Path Extraction Algorithm

Source path format: `…/<BUILD_ENV>/<segment>/src/…`

Example: `/repo/cloned/ATO/yo_y_01_ato_r1/src/COBOL/BATCH/S2NN/AS14000.SCB2B`

Algorithm in `PathVariableExtractor`:
1. Split sourcePath by `/`
2. Find the path component whose split-by-`_` tokens satisfy: `tokens.size() >= 5 && tokens[2] ==~ /\d+/`
3. `C1SYSTEM = tokens[1]` → `y`
4. `PATH_LO  = tokens[2]` → `01`
5. Look up `"${PATH_LO}|${buildEnv}"` in stageMap → `C1STAGE` (e.g. `X2A`)
6. Return `[C1STAGE: c1stage, C1SYSTEM: c1system, HLQ: hlq ?: '']`

---

## New Classes

### `StageMapLoader`

```
Map<String, String> load(String csvPath)
```

Reads `stage-map.csv` (semicolon-delimited, key `"01|ATO"`, value `"X2A"`).
Returns map keyed by `PATH_LO|BUILD_ENV` → C1STAGE string.
Throws `IllegalArgumentException` on malformed rows.

### `PathVariableExtractor`

```
Map<String,String> extract(String sourcePath, String buildEnv, Map<String,String> stageMap, String hlq)
```

- Parses source path segment as described above.
- Looks up `PATH_LO|buildEnv` in stageMap; throws `IllegalArgumentException("No stage-map entry for '${PATH_LO}|${buildEnv}'")`  if missing.
- Returns `[C1STAGE: …, C1SYSTEM: …, HLQ: hlq ?: '']`.

---

## Changed Classes

### `LibraryNameResolver`

**Before:** `resolve(String template, String stage, String system)`
**After:**  `resolve(String template, Map<String,String> vars)`

- Iterates all `${VARNAME}` occurrences in template; replaces each from `vars`.
- Throws `IllegalStateException("Unresolved macro: \${VARNAME}")` if any `${…}` remains after substitution.
- `toTocolbLibrary(String resolvedLibrary)` — unchanged.

### `DeleteCassaforteLogic`

**execute() before:** `(sourcePath, fileType, String stage, String system, String buildGroup)`
**execute() after:**  `(sourcePath, fileType, Map<String,String> vars, String buildGroup)`

Passes `vars` directly to `resolver.resolve(rule.libraryTemplate, vars)`.

### `SfilamentoLogic`

**New injected fields:** `PathVariableExtractor extractor`, `Map<String,String> stageMap`, `String hlq`

**execute() before:** `(sourcePath, fileType, String environment, String system, String buildGroup)`
**execute() after:**  `(sourcePath, fileType, String environment, String buildGroup)`

Inside execute():
- `currentVars = extractor.extract(sourcePath, environment, stageMap, hlq)`
- `deleteLogic.execute(sourcePath, fileType, currentVars, buildGroup)` (delete current env)
- For each `superEnv` in `envChain.getSuperiors(environment)`:
  - `superVars = extractor.extract(sourcePath, superEnv, stageMap, hlq)`
  - `resolver.resolve(rule.libraryTemplate, superVars)` for the restore source library
- `resolver.resolve(rule.libraryTemplate, currentVars)` for the local TOCOLB derivation

### `PrevEnvCleanLogic`

**New injected fields:** `PathVariableExtractor extractor`, `Map<String,String> stageMap`, `String hlq`

**execute() before:** `(sourcePath, fileType, String environment, String system, String buildGroup)`
**execute() after:**  `(sourcePath, fileType, String environment, String buildGroup)`

Inside execute():
- `prevEnv  = envChain.getPredecessor(environment)`
- `prevVars = extractor.extract(sourcePath, prevEnv, stageMap, hlq)`
- `deleteLogic.execute(sourcePath, fileType, prevVars, buildGroup)`

### `PuliziaCassaforteImpl`

- `run(…)` overloads gain `String hlq` parameter (default `''` for existing overloads).
- `private execute(…)` instantiates `StageMapLoader`, `PathVariableExtractor`.
- Injects `extractor`, `stageMap`, `hlq` into `SfilamentoLogic` and `PrevEnvCleanLogic`.
- Per-file loop: builds `vars = extractor.extract(sourcePath, environment, stageMap, hlq)` for each line, passes `vars` to `deleteLogic.execute()`.
- Removes `extractSystem(buildGroup)` TODO method (C1SYSTEM now from extractor).

### `PuliziaCassaforte.groovy` (front-end script)

Adds optional CLI argument: `--hlq <value>` (defaults to `''`).
Passes `hlq` to `PuliziaCassaforteImpl.run(…, hlq)`.

---

## Error Handling

| Condition | Behaviour |
|---|---|
| Path segment not found / wrong format | `IllegalArgumentException` from `PathVariableExtractor` |
| `PATH_LO\|BUILD_ENV` not in stage-map | `IllegalArgumentException` from `PathVariableExtractor` |
| `${VARNAME}` in template not in vars map | `IllegalStateException` from `LibraryNameResolver` |
| HLQ not passed (`null` or `''`) | `HLQ` added to map as `''`; `${HLQ}` resolves to empty string — no error |

---

## Test Plan

### `StageMapLoaderSpec` (new)

- Load valid stage-map.csv → map contains `"01|ATO" → "X2A"`, `"03|ST" → "YAD"`
- Missing key returns null (caller handles)
- Malformed row throws `IllegalArgumentException`

### `PathVariableExtractorSpec` (new)

- Path `/repo/cloned/ATO/yo_y_01_ato_r1/src/COBOL/BATCH/S2NN/AS14000.SCB2B`, env `ATO`, no HLQ
  → `[C1SYSTEM:'y', C1STAGE:'X2A', HLQ:'']`
- Same path, `hlq='U0G9700'` → `HLQ:'U0G9700'`
- Unknown `PATH_LO|BUILD_ENV` key → throws `IllegalArgumentException`

### `LibraryNameResolverSpec` (update)

- Replace existing `resolve(template, stage, system)` calls with `resolve(template, [C1STAGE:…, C1SYSTEM:…, HLQ:…])`
- Add: template `${HLQ}.D9P${C1STAGE}.SYST.${C1SYSTEM}` + full map → correct string
- Add: template with `${UNKNOWN_VAR}` not in map → throws `IllegalStateException`
- Add: `HLQ` as `''` in map → `${HLQ}` replaces to empty string, no error

### `DeleteCassaforteLogicSpec` (update)

- Replace `execute(…, stage, system, buildGroup)` with `execute(…, [C1STAGE:stage, C1SYSTEM:system, HLQ:''], buildGroup)` in all existing cases.

### `SfilamentoLogicSpec` (update)

- Inject mock `PathVariableExtractor` + stageMap + hlq fields.
- Update `execute()` calls to new signature.

### `PrevEnvCleanLogicSpec` (update)

- Inject mock `PathVariableExtractor` + stageMap + hlq.
- Update `execute()` calls to new signature.

### `PuliziaCassaforteImplSpec` (add test)

End-to-end HLQ test:
- Source path: `/repo/cloned/ATO/yo_y_01_ato_r1/src/mapasm/batch/TESTMEM.SZFSSWG`
- `environment = 'ATO'`, `hlq = 'U0G9700'`
- Fixture `rules.csv`: add rule `SZFSSWG ;${HLQ}.D9P${C1STAGE}.PE000.@@@@.@@@@@@@@.@@.ZARA;NO`
- Pre-create member at `U0G9700.D9PX2A.PE000.@@@@.@@@@@@@@.@@.ZARA/TESTMEM` in `LocalFileOps`
- `impl.run(lista, 'ATO', buildGroup, bmFile, ops, 'U0G9700')` → returns `0` and member deleted

---

## Out of Scope

- Changes to `PuliziaPostBuild.groovy` / `PuliziaPostBuildLocal.groovy` entry scripts (separate task).
- Removal of `EnvironmentChain.STAGE_BY_ENV` (still used for chain logic, not library resolution).
- Handling of malformed `${C1STAGE` (missing `}`) in production `rules.csv` — separate cleanup task.
