---
name: zbuilder-groovy-coder
description: >
  IBM zBuilder DBB Groovy script expert for development of script used by type:task steps or to be run by groovyz interpreter called from CLI.
  TRIGGER on: mentions of DBB groovy script, groovyz script, task script, type:task,
  BuildContext, TaskVariables, condition variables or requests to write/fix/review any .groovy file used in a DBB build pipeline.
---

# IBM zBuilder DBB Groovy Script Expert

You are now an expert in writing and debugging Groovy scripts for IBM Dependency Based Build (DBB) `type: task` steps or scripts run by the `groovyz` interpreter from the CLI.
Follow all instructions in this skill for the duration of the task.

## Your Workflow

1. **Read existing groovy scripts** first — use Glob/Read to find `*.groovy` under `groovy/` in the build directory
2. **Read the calling YAML step** — understand what variables the step expects, what condition flags it sets, and where it sits in the pipeline
3. **Identify the script type** (see **Script Categories** below) and apply the matching template
4. **Draft the script** following the patterns below, then run the **Validation Checklist**
5. **Align** with existing scripts in the repo: naming conventions, log levels, assertion style, return codes

---

## Mandatory Script Header

Every task script **must** start with this exact line — without it the script will not load as a DBB task:

```groovy
@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript
```

Then add only the imports you actually use:

```groovy
import com.ibm.dbb.task.BuildContext    // for context object
import com.ibm.dbb.task.TaskVariables   // for config object
import com.ibm.dbb.task.TaskConstants   // for CLI option parsing
import com.ibm.dbb.repository.*         // for repository APIs
import com.ibm.dbb.build.*              // for build APIs
```

---

## The Two Runtime Objects

Every task script has two implicit objects available (no declaration needed):

| Object | Type | Scope | Purpose |
|--------|------|-------|---------|
| `context` | `BuildContext` | Global (whole build) | CLI params, cross-language shared state |
| `config` | `TaskVariables` | Language/step local | Language-specific variables, step results |

```groovy
// Read a variable
String myVar = context.getVariable('CLI_SOMETHING')   // context (global)
String myVar = config.getVariable('SOME_STEP_VAR')    // config (language-local)

// Write a variable
context.setVariable('MY_GLOBAL', value)
config.setVariable('MY_LOCAL', value)

// Read CLI options
def cli = context.getCommandLine(TaskConstants.COMMAND_LINE)
if (cli.hasOption("myOption")) {
    String val = cli.getOptionValue("myOption")
}
```

**Rule:** `CLI_*` variables live in `context`. Language-step variables live in `config`. When in doubt about where a variable lives, check how it is written in the pipeline's setup scripts.

---

## Logging API

Use the implicit `log` object — never `println` or `System.out`:

```groovy
log.info("Starting {}", scriptName)           // milestones, variable resolutions
log.debug("Variable {}: [{}]", name, value)   // detailed trace
log.warn("Key '{}' not found, defaulting to '{}'", key, defaultValue)
log.error("Required variable '{}' is null", varName)
```

SLF4J-style `{}` placeholders are preferred over string concatenation.

---

## Return Codes

| Code | Meaning |
|------|---------|
| `0` | Success |
| `4` | Warning / partial failure (not found, default used) |
| `8` | Error — required variable missing or logic failure |

Always use a bare `return <int>` at the bottom. The `type: task` YAML step exposes the return code as `${<StepName>}` for use in `condition:` expressions on subsequent steps.

---

## Script Categories

### 1. Setup / Variable Mapping Scripts

**Purpose:** Run early in the pipeline to populate derived or aliased variables for downstream steps.

**Pattern:**
```groovy
@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript

import com.ibm.dbb.task.BuildContext
import com.ibm.dbb.task.TaskVariables

class MySetupImpl {
    private final BuildContext context
    private final TaskVariables config

    MySetupImpl(BuildContext context, TaskVariables config) {
        this.context = context
        this.config = config
    }

    void loadVars() {
        log.info("loadVars: Loading derived variables")

        // Map source → target variable
        String sourceValue = config.getVariable('SOURCE_VAR')
        if (sourceValue != null) {
            config.setVariable('TARGET_VAR', sourceValue)
            log.debug("Mapped SOURCE_VAR -> TARGET_VAR = {}", sourceValue)
        }

        // Substring derivation (Endevor-style)
        String fileExt = config.getVariable('FILE_EXT')
        config.setVariable('C1TY_SUB5_3', fileExt.drop(4).take(3))  // chars 5-7
        config.setVariable('C1TY_SUB1_8', fileExt.take(8))           // first 8

        // Padded string (simulates fixed-length Endevor fields)
        config.setVariable('MEMBER_SUB1_8', config.getVariable('MEMBER').take(8).padRight(8))
    }
}

// ========= MAIN SCRIPT EXECUTION ==========
MySetupImpl impl = new MySetupImpl(context, config)
impl.loadVars()
return 0
```

**Endevor variable name mapping conventions used in this project:**

| DBB Variable | Endevor Alias | Scope |
|---|---|---|
| `CLI_USER` | `C1USER` | context |
| `CLI_COMMENT` | `C1COMMENT` | context |
| `FILE_EXT` | `C1TYPE`, `C1ELTYPE`, `C1TY` | config |
| `MEMBER` | `C1ELEMENT` | config |
| `PATH_SSA` | `C1SUBSYS` | config |
| `PATH_SA` | `C1SYSTEM` | config |
| `PATH_AMBIENTE` | `C1ENVMNT` | config |
| `STAGE` | `C1STAGE`, `C1ST` | config |
| `STAGE_ID` | `C1STGID` | config |

**Substring variable naming:** `<VAR>_SUB<start>_<length>` — e.g. `C1TY_SUB5_3` = `C1TY` starting at position 5 (0-indexed: drop(4)), keep 3 chars.

---

### 2. Condition Scripts

**Purpose:** Calculate boolean flags (`if*` variables) stored as strings `"true"` / `"false"` so that YAML step `condition:` expressions can gate step execution.

**YAML usage:**
```yaml
- step: MySyntaxCheck
  type: mvs
  condition: "${ifSynt} == 'true'"
```

**Pattern:**
```groovy
@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript

log.info("Calculating condition variables for <ProcessorName>")

try {
    // --- Read inputs ---
    // Context (global): CLI_* variables
    String CLI_COMMENT = context.getVariable('CLI_COMMENT')
    // Config (language-local): step-specific resolved variables
    String LRSTGSYNT   = config.getVariable('LRSTGSYNT')?.trim()?.toUpperCase()
    String LRTYPSYNT   = config.getVariable('LRTYPSYNT')?.trim()?.toUpperCase()
    String C1ELTYPE    = config.getVariable('C1ELTYPE')?.trim()?.toUpperCase()
    String C1STGID     = config.getVariable('C1STGID')?.trim()?.toUpperCase()

    log.debug("Inputs — LRSTGSYNT:[{}] LRTYPSYNT:[{}] C1ELTYPE:[{}] C1STGID:[{}] CLI_COMMENT:[{}]",
              LRSTGSYNT, LRTYPSYNT, C1ELTYPE, C1STGID, CLI_COMMENT)

    // --- Condition 1: ifSynt ---
    // Original Endevor: IF (&STGSYNT EQ 'Y' OR 'E') AND &TYPSYNT EQ 'Y'
    assert LRSTGSYNT != null && LRTYPSYNT != null : "Required variable for ifSynt is null"
    Boolean ifSynt = (LRSTGSYNT == 'Y' || LRSTGSYNT == 'E') && LRTYPSYNT == 'Y'
    config.setVariable('ifSynt', ifSynt.toString())
    log.debug("ifSynt = {}", ifSynt)

    // --- Condition 2: ifLite ---
    // Store language-step-only flags in config; cross-step flags in context
    Boolean ifLite = (LRSTGSYNT == 'L' || LRSTGSYNT == 'E') &&
                     C1ELTYPE == 'SJCLPRC' &&
                     CLI_COMMENT != '$BYPASS:' + config.getVariable('MEMBER') + ' '
    config.setVariable('ifLite', ifLite.toString())
    log.debug("ifLite = {}", ifLite)

} catch (Exception e) {
    log.error("Error calculating condition variables", e)
    throw e
}
return 0
```

**Key rules for condition scripts:**
- Always `.trim().toUpperCase()` string variables from config before comparison
- Simulate Endevor `substring(1,n)` with `.take(n)` (1-indexed → 0-indexed `take`)
- Simulate Endevor `substring(pos,len)` with `.drop(pos-1).take(len)`
- Use `assert` to validate required variables exist — DBB will report the assertion message
- Store flags as `"true"`/`"false"` strings (not booleans) — YAML conditions compare strings
- Prefer `config.setVariable()` for language-local flags; use `context.setVariable()` only if the flag must be visible across language processors
- Include the original Endevor condition as a comment for traceability

---

### 3. Symbol Table Resolution Scripts

**Purpose:** Look up environment-specific or stage-specific values from YAML-defined map variables.

**YAML symbol table definition:**
```yaml
variables:
  - name: LRTYPSYNT_TBL
    value:
      CA7: "Y"
      DB2: "Y"
      INP: "Y"
      PRC: "Y"
      DEL: "Y"
```

**Pattern:**
```groovy
@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript

/**
 * Resolves a variable from a symbol table defined in YAML.
 * @param varName  Base variable name (table must be named <varName>_TBL in config/context)
 * @param key      The lookup key
 * @param defaultValue  Used if key not found; null means failure is an error
 */
boolean resolveSymbolTable(String varName, String key, String defaultValue = null) {
    String tableName = "${varName}_TBL"
    def symbolTable = config.getVariable(tableName) ?: context.getVariable(tableName)

    if (symbolTable == null) {
        log.error("Symbol table '{}' not found", tableName)
        if (defaultValue != null) { config.setVariable(varName, defaultValue) }
        return false
    }
    if (!(symbolTable instanceof Map)) {
        log.error("Symbol table '{}' is not a Map", tableName)
        return false
    }

    String value = symbolTable[key]
    if (value == null) {
        log.warn("Key '{}' not found in '{}', using default '{}'", key, tableName, defaultValue)
        if (defaultValue != null) { config.setVariable(varName, defaultValue) }
        return false
    }

    config.setVariable(varName, value)
    log.info("Resolved {}: '{}' -> '{}'", varName, key, value)
    return true
}

// ========= MAIN SCRIPT EXECUTION ==========
log.info("ResolveSymbolTable: Starting")
try {
    String stage    = config.getVariable('STAGE')
    String stageId  = config.getVariable('STAGE_ID')
    String typCode  = config.getVariable('C1TY_SUB5_3')   // 3-char type code

    resolveSymbolTable("LRTYPSYNT", typCode, "X")
    resolveSymbolTable("LRSTGSYNT", stage,   "X")

    log.info("ResolveSymbolTable: Completed")
} catch (Exception e) {
    log.error("ResolveSymbolTable: Error", e)
    return 8
}
return 0
```

---

### 4. CLI Parameter Extraction Scripts

**Purpose:** Parse `--option` arguments from the zBuilder CLI and store them as `CLI_*` context variables.

**Pattern:**
```groovy
@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript

import com.ibm.dbb.task.TaskConstants

def cli = context.getCommandLine(TaskConstants.COMMAND_LINE)

if (cli.hasOption("myParam")) {
    String value = cli.getOptionValue("myParam")
    log.debug("Pulled '{}' from '--myParam'", value)
    context.setVariable("CLI_MYPARAM", value)
} else {
    // Always provide defaults for optional parameters
    log.warn("No '--myParam' provided. Defaulting to 'DEFAULT'.")
    context.setVariable("CLI_MYPARAM", "DEFAULT")
}

return 0
```

---

### 5. Exit Code Scripts

**Purpose:** Propagate or transform a step return code for use in YAML `condition:` or `maxRC:` checks.

**Pattern:**
```groovy
@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript

int exitCode = 0
log.info("Calculating exit code")
try {
    // Read numeric RC from a previous step stored in config
    int jclcheck = config.getVariable('JCLCHECK') ?: 0
    if (jclcheck <= 7) {
        exitCode = jclcheck
    }
    // If jclcheck > 7, exitCode stays 0 (treat as non-critical)
} catch (Exception e) {
    log.error("Error calculating exit code", e)
    throw e
}
return exitCode
```

---

## Variable Naming Conventions

| Prefix/Pattern | Meaning | Scope |
|---|---|---|
| `CLI_*` | Command-line parameters | context |
| `PATH_*` | Path segments extracted from FILE | config |
| `STAGE`, `STAGE_ID` | Resolved environment/stage | config |
| `LR*` | Endevor symbol table values (Italian: £ prefix → LR) | config |
| `C1*` | Endevor element/member attributes | config/context |
| `if*` | Boolean condition flags (stored as strings) | config or context |
| `*_TBL` | Symbol table (Map variable in YAML) | config or context |
| `*_SUB<start>_<len>` | Substring derived variable | config |

---

## Path Parsing Pattern

Source file paths follow this structure:
```
<SSA>_<SA>_<LO>_<AT>[_DESC]/genere/linguaggio/<ambiente>/<processorgroup>/<MEMBER>.<TYPE>
```

Extract components as:
```groovy
String filePath = context.getVariable('FILE')   // relative to WORKSPACE
String[] segments = filePath.split('/')

// Application directory (first segment): SSA_SA_LO_AT[_DESC]
String appDir = segments[0]
String[] appParts = appDir.toLowerCase().split('_')
String SSA = appParts[0].toUpperCase()     // e.g. YO
String SA  = appParts[1].toUpperCase()     // e.g. Y
String LO  = appParts[2]                   // e.g. 01

// Processor group is second-to-last path segment
String processorGroup = segments[-2]
```

---

## Error Handling Standard

```groovy
try {
    // ... main logic ...
} catch (Exception e) {
    log.error("ScriptName: Error occurred", e)
    throw e      // re-throw for condition scripts (signals hard failure)
    // OR: return 8    // for scripts that should propagate RC softly
}
return 0
```

- Condition scripts should re-throw: an unexpected condition calculation failure is a hard error
- Resolution/setup scripts may return 8 to allow pipeline error handling

---

## How Task Scripts Fit in the YAML Pipeline

```yaml
# In a language YAML file (e.g. PROCESSOR.yaml):
steps:
  - step: ResolvePathAndStage    # type: task — setup, runs first
    type: task
    script: groovy/ResolvePathAndStage.groovy

  - step: AugmentVars            # type: task — variable mapping
    type: task
    script: groovy/AugmentedStepVars.groovy

  - step: ResolveSymbolTables    # type: task — symbol table resolution
    type: task
    script: groovy/ResolveSymbolTableFromYaml.groovy

  - step: CalcConditions         # type: task — condition flag calculation
    type: task
    script: groovy/processor/CalcConditions.groovy

  - step: JclCheck               # type: mvs — conditional on ifSynt flag
    type: mvs
    pgm: IEYEJCL
    condition: "${ifSynt} == 'true'"
    ...

  - step: GJCLExit               # type: task — exit code propagation
    type: task
    script: groovy/processor/RcExit.groovy
```

---

## Validation Checklist

Before presenting any groovy script:

- [ ] First line is exactly `@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript`
- [ ] Only imports that are actually used are included
- [ ] `context` used for `CLI_*` and global cross-step variables; `config` for language-local variables
- [ ] Condition flag variables stored as `.toString()` of Boolean, never bare `true`/`false`
- [ ] `assert` used to validate required variables before use (with a descriptive message)
- [ ] All string variables from config/context normalized with `.trim()?.toUpperCase()` before comparison
- [ ] Substring operations use `.take(n)`, `.drop(n).take(m)` (not Java `.substring()` except for bounds-checked cases)
- [ ] Padded strings use `.padRight(n)` to simulate Endevor fixed-length fields
- [ ] Logging uses `log.info/debug/warn/error` with `{}` placeholders, never `println`
- [ ] Return code is a bare `return <int>` (0, 4, or 8)
- [ ] Script wrapped in `try/catch` — re-throw for condition scripts, return 8 for setup scripts
- [ ] Symbol table lookup: always check `config` first, then `context`; validate `instanceof Map`
- [ ] Endevor condition comments included for all translated condition blocks
- [ ] Variable names follow project conventions (`CLI_*`, `C1*`, `LR*`, `if*`, `*_TBL`, `*_SUB*_*`)

---

## Tips

- When translating Endevor conditions, include the original Endevor `IF` block as a comment
- `C1TY_SUB5_3` is the 3-char file type code used as the primary symbol table lookup key
- `STAGE` (full stage string) and `STAGE_ID` (single char, e.g. 'B', 'E') are the stage lookup keys
- `LRCODTIPOSI` is the organization type code ('01', '03', '10', '12') derived from a stage table
- For composite keys: concatenate directly — e.g. `"${codtiposi}${stageId}"` as lookup key
- When in doubt whether to use `context` or `config` for a new flag, check how the consuming YAML step reads it
