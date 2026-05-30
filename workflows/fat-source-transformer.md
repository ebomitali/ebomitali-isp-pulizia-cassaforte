# fat-source: replicate `loadScript` locally

## Context

`RunPC.groovy` (`library/src/test/sh/RunPC.groovy`) already declares `@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript` for USS use, but then bypasses `loadScript()` entirely — uses `GroovyClassLoader` + `loadClass` directly. Inconsistent.

On USS, `groovyz` puts the real `com.ibm.dbb.groovy.ScriptLoader` on the classpath automatically. Locally, there is no such class, so `@BaseScript ScriptLoader` cannot compile.

Goal: provide a local mock `ScriptLoader` in `fat-source`, prepend `@BaseScript` to the generated fat blob, and append `createPuliziaCassaforteImpl()` so that runner scripts can use the same `loadScript()` API locally as on USS.

## Step 1 — New file: `fat-source/src/main/groovy/com/ibm/dbb/groovy/ScriptLoader.groovy`

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
- This file is in `src/main/groovy/` but **not** in the temp-dir fed to `create-source-blob.sh` → never merged into the fat blob.

## Step 2 — Modify `fat-source/build.gradle` `generateFatSource.doLast`

After the `exec { }` block that runs `create-source-blob.sh`, add:

```groovy
def fatFile    = file("${projectDir}/src/main/groovy/PuliziaCassaforte.groovy")
def helperFile = project(':library').file('src/main/groovy/ScriptLoaderHelper.groovy')

// Prepend @BaseScript transform
fatFile.text = "@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript\n\n" + fatFile.text

// Append factory method (mirrors what full-fat-source gets from its zos sources)
fatFile << "\n" + helperFile.text
```

Result: generated blob becomes a `ScriptLoader` subclass and exposes `createPuliziaCassaforteImpl()`.

## Intended usage after this change

Runner script (local, fat-source jar on classpath):

```groovy
@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript

def loaded = loadScript(new File("path/to/FullPuliziaCassaforte.groovy"))
def impl   = loaded.createPuliziaCassaforteImpl()
int errors = impl.run(fileList, environment, buildGroup, cfgProps)
```

Same API as USS where IBM's real `ScriptLoader` is used via `groovyz`.

## Verification

```bash
# Compile — should succeed without IBM jars
./gradlew :fat-source:compileGroovy

# Tests — no regression
./gradlew :fat-source:test

# Smoke-check generated blob has both markers
head -1 fat-source/src/main/groovy/FullPuliziaCassaforte.groovy   # @BaseScript line
grep createPuliziaCassaforteImpl fat-source/src/main/groovy/FullPuliziaCassaforte.groovy
```
