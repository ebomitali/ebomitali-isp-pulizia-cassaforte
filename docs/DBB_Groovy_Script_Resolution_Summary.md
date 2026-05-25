# DBB 3.0.3 Groovy Script Resolution & Class Reuse Summary

## Overview
This document summarizes discussion on Groovy script resolution in IBM Dependency Based Build (DBB) 3.0.3, specifically addressing how task steps can load and use classes from companion scripts.

---

## Question 1: Will DBB Resolve Scripts in the Same Directory?

### Answer: **Yes, automatically**

When using relative paths in DBB script resolution methods, DBB automatically appends the path to the **parent directory of the running script**.

#### Example
```groovy
loadScript(new File("Tools.groovy"))
```
This will attempt to load `Tools.groovy` from the same directory as the currently executing script.

---

## Question 2: Can a Task Step Load Another Script and Use Its Classes?

### Answer: **Yes, this is the intended pattern in DBB 3.0.3**

You can:
1. Define a Groovy script implementing a `type: task` step
2. Use `loadScript()` to load a companion script containing class definitions
3. Instantiate and use those classes in your task step

---

## Script Caching API (Recommended Approach)

To enable script resolution and caching, extend your Groovy scripts with the `ScriptLoader` base class:

```groovy
@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import com.ibm.dbb.build.*
```

### Available Methods

| Method | Purpose |
|--------|---------|
| `runScript(File script, String[] args=[])` | Execute another Groovy script with caching; supports both DBB ScriptLoader and generic Groovy scripts |
| `runScript(File script, Map<String,Object> argMap)` | Execute with argument Map (preferred for DBB ScriptLoader scripts); allows easy object passing between scripts |
| `loadScript(File script)` | Load script without execution; **used for loading object-oriented scripts like Tools.groovy** |
| `getScriptDir()` | Returns the parent directory of the running script as a String |

---

## Path Resolution Behavior

### Relative Paths
- Automatically appended to the current script's parent directory
- Example: `loadScript(new File("Tools.groovy"))` in `/u/build/scripts/Main.groovy` loads `/u/build/scripts/Tools.groovy`

### Absolute Paths
- Supported by both `loadScript()` and `runScript()` methods
- Example: `loadScript(new File("/u/build/lib/Tools.groovy"))`

---

## Recommended Architecture for Class Reuse

### File Structure
```
groovy/
├── MyTaskStep.groovy          (main task step implementation)
└── MyTaskLibrary.groovy       (shared classes & utilities)
```

### Library Script (MyTaskLibrary.groovy)
```groovy
// No BaseScript required for library scripts
import com.ibm.dbb.build.*

class DatasetHelper {
    String hlq
    
    DatasetHelper(String hlq) {
        this.hlq = hlq
    }
    
    String buildDatasetName(String suffix) {
        return "${hlq}.${suffix}"
    }
    
    void createDataset(String dsn, String options) {
        new CreatePDS().dataset(dsn).options(options).execute()
    }
}

class LoggingHelper {
    void logInfo(String message) {
        println("INFO: ${message}")
    }
    
    void logError(String message) {
        System.err.println("ERROR: ${message}")
    }
}
```

### Task Step Implementation (MyTaskStep.groovy)
```groovy
@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import com.ibm.dbb.build.*
import com.ibm.dbb.task.*

// Load the companion library
def tools = loadScript(new File("MyTaskLibrary.groovy"))

class MyCustomTask extends AbstractTask {
    
    MyCustomTask(BuildContext context, TaskVariables config) {
        super(context, config)
    }
    
    @Override
    Integer execute() throws BuildException {
        try {
            // Use classes from the loaded script
            def dsHelper = new DatasetHelper(config.getStringVariable("HLQ"))
            def logHelper = new LoggingHelper()
            
            logHelper.logInfo("Creating dataset for compilation")
            String dsn = dsHelper.buildDatasetName("COBOL")
            dsHelper.createDataset(dsn, "cyl space(5,1) lrecl(80) dsorg(PO)")
            
            logHelper.logInfo("Dataset created: ${dsn}")
            return 0
            
        } catch (Exception e) {
            logger.error("Task failed: ${e.message}")
            return 8
        }
    }
}

// Instantiate and execute
new MyCustomTask(context, config).execute()
```

### YAML Configuration (dbb-build.yaml)
```yaml
tasks:
  - task: MyCustomTask
    script: groovy/MyTaskStep.groovy
    variables:
      - name: HLQ
        value: "MYHLQ"
```

---

## Key Technical Details

| Aspect | Details |
|--------|---------|
| **loadScript() return type** | Returns a `GroovyObject` that wraps the loaded script's classes and methods |
| **Class instantiation** | Classes defined in loaded scripts can be instantiated directly in the task step |
| **Script caching** | Both main task script and loaded library script benefit from DBB's automatic caching mechanism |
| **Relative path resolution** | Paths resolve relative to the task script's parent directory |
| **Arguments** | `loadScript()` does NOT pass arguments; it's purely for loading class definitions |
| **Performance benefit** | Compiled script classes are cached to avoid recompilation on subsequent calls |

---

## Benefits of This Pattern

✅ **Code Reuse** — Share common classes across multiple task steps  
✅ **Clean Separation** — Separate business logic from utilities  
✅ **Performance** — Automatic caching reduces compilation overhead  
✅ **Maintainability** — Easier to test and maintain library classes independently  
✅ **Object-Oriented Design** — Leverage Groovy's OOP capabilities  

---

## Application to ISP Cantiere 3 Project

### Suggested Structure for BES C3
```
groovy/
├── CassaforteManager.groovy         (main task step)
├── CassaforteUtils.groovy           (CassaforteHelper, DatasetOps classes)
├── StageResolver.groovy             (main task step)
└── StageResolutionLib.groovy        (StageMapper, ConfigLoader classes)
```

### Use Cases
- **Cassaforte Management**: Separate vault/safe library lifecycle logic into reusable helper classes
- **Stage Resolution**: Create mapper classes for dynamic `(CLI_SITE, PATH_LO, CLI_BUILDENV)` → `(STAGE, STAGE_ID)` resolution
- **Metadata Operations**: Build reusable classes for DBB metadata store queries and updates

---

## References

- **DBB Documentation**: IBM Dependency Based Build 3.0.3 documentation
- **Key Classes**:
  - `com.ibm.dbb.groovy.ScriptLoader` — Base script class for caching
  - `com.ibm.dbb.task.AbstractTask` — Base class for task implementation
  - `com.ibm.dbb.task.BuildContext` — Inter-task communication
  - `com.ibm.dbb.task.TaskVariables` — Task configuration variables

---

## Summary

DBB 3.0.3 fully supports a modular Groovy script architecture where:
- Task steps implemented as `type: task` can use `loadScript()` to load companion scripts
- Companion scripts define reusable classes without requiring the `BaseScript` annotation
- Relative paths automatically resolve to the script's parent directory
- Script caching provides performance benefits
- This pattern enables clean, maintainable, and testable build automation code

