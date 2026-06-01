# full-fat-source: replicate `loadScript` locally

## Context

`RunPuliziaCassaforte.groovy` (`full-fat-source/src/test/sh/uss/RunPuliziaCassaforte.groovy`)  declares `@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript` specific for USS use

On USS, `groovyz` puts the real `com.ibm.dbb.groovy.ScriptLoader` on the classpath automatically. Locally, there is no such class, so `@BaseScript ScriptLoader` cannot compile nor can execute

Goal: provide a local mock `ScriptLoader` in `full-fat-source/src/mock/java` directory so that runner `full-fat-source/src/test/sh/RunPuliziaCassaforte.groovy`  can use the same `loadScript()` API locally as on USS.

## Step 1 — New file: `fat-source/src/mock/groovy/com/ibm/dbb/groovy/ScriptLoader.groovy`

Local mock. Must extend `Script` (required by `@BaseScript`). Provides `loadScript(File)`:

```groovy
package com.ibm.dbb.groovy

abstract class ScriptLoader extends Script {
    Script loadScript(File file) {
        def gcl = new GroovyClassLoader(this.class.classLoader)
        Class clazz = gcl.parseClass(file)
        Script script = (Script) clazz.getDeclaredConstructor().newInstance()
        script.binding = new Binding()
        script.run()
        return script
    }
}
```

Notes:
- Parent classloader = calling script's classloader → `ScriptLoader` itself is resolvable when the fat blob references it via `@BaseScript`.
- `script.run()` executes the fat blob top-level code (initialises all inner classes into the GCL).
- Returned `script` exposes `createPuliziaCassaforteImpl()` as a top-level method.
- Groovy joint-compiles both files in the same sourceSet via stub generation → `ScriptLoader` resolvable at AST-transform time.
- This mock generated file goes in `src/test/groovy/` but **not** in the temp-dir fed to `create-source-blob.sh` → never merged into the fat blob.

## Intended usage after this change

On USS gradle is not available so revert to manual launch of a shell script, i.e. `full-fat-source/src/test/sh/mrunpct2.sh`.
The groovy command inside the script should see the mock class in 
that invoke the groovy wrapper `full-fat-source/src/test/sh/RunPuliziaCassaforte.groovy`
The wrapper uses the transform to run a simulated local environment, should complete successfully
Same API as USS where IBM's real `ScriptLoader` is used via `groovyz`.

## Verification

```bash
# Compile — should succeed without IBM jars
./gradlew :full-fat-source:compileGroovy

# Tests — no regression
./gradlew :full-fat-source:test

# Manul run
cd full-fat-source/src/test/sh && ./mrunpct2.sh
```
