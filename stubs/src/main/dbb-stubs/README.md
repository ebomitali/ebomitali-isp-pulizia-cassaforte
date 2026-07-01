# DBB `com.ibm.dbb.groovy.TaskScript` compile-only stubs

Purpose: let DBB Groovy task scripts (anything using
`@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript`)
be **compiled, type-checked, and edited in an IDE off-host** — i.e.
outside z/OS USS, where the real `dbb.jar` (IBM-licensed, only shipped
with the DBB toolkit install on the mainframe) is not available.

This is the same `src/stubs/groovy/` pattern used elsewhere in the
BES C3 migration (Strategy D / off-host compilation for
`CassaforteHelper` and friends): real DBB/JZOS classes are represented
here by minimal-surface stub classes with matching package/class/method
signatures but no implementation (every method just throws
`UnsupportedOperationException`). They must **never** be packaged into
a build artifact or executed — `compileOnly` scope only.

## What's stubbed here

```
src/stubs/groovy/com/ibm/dbb/groovy/AbstractLoader.groovy   # base class TaskScript extends
src/stubs/groovy/com/ibm/dbb/groovy/TaskScript.groovy        # the class you asked for
src/stubs/groovy/com/ibm/dbb/task/BuildContext.groovy        # type of `context`
src/stubs/groovy/com/ibm/dbb/task/TaskVariables.groovy       # type of `config`
src/stubs/groovy/com/ibm/dbb/task/TaskConstants.groovy       # FILE_PATH / COMMAND_LINE only
```

Every method beyond the two or three directly evidenced in the DBB 3.0.3
tutorials (`getStringVariable`, `setVariable`, `getCommandLine`,
`getIntVariable` for step RCs) is explicitly commented as
**"typical / verify against real dbb.jar"** — don't treat those as
gospel; confirm on host with:

```sh
javap -classpath $DBB_HOME/lib/dbb.jar com.ibm.dbb.task.TaskVariables
javap -classpath $DBB_HOME/lib/dbb.jar com.ibm.dbb.task.BuildContext
javap -classpath $DBB_HOME/lib/dbb.jar com.ibm.dbb.groovy.TaskScript
```

and tighten the stubs accordingly before relying on any inferred method.

## Gradle wiring (compileOnly)

```groovy
sourceSets {
    stubs {
        groovy { srcDirs = ['src/stubs/groovy'] }
    }
    main {
        groovy { srcDirs += sourceSets.stubs.groovy.srcDirs }
    }
}

dependencies {
    // Real SLF4j API — not IBM-proprietary, safe to pull from Maven Central
    // and matches what TaskScript.log actually is.
    compileOnly 'org.slf4j:slf4j-api:1.7.36'
    compileOnly 'commons-cli:commons-cli:1.5.0'

    // The stubs themselves only need to be on the compile classpath;
    // on z/OS the REAL dbb.jar takes their place at runtime and must
    // come FIRST on the classpath so it wins over these stubs.
}
```

If you'd rather not fight sourceSets, the simplest approach is: compile
the stub `.groovy` files into a small `dbb-stubs.jar` once, and add that
jar as a `compileOnly` dependency wherever you edit/compile DBB Groovy
tasks off-host:

```sh
groovyc -cp "$(find ~/.m2 -name 'slf4j-api*.jar' -o -name 'commons-cli*.jar' | tr '\n' ':')" \
        -d build/classes src/stubs/groovy/com/ibm/dbb/**/*.groovy
jar cf dbb-stubs.jar -C build/classes .
```

Then compile a real task script against it:

```sh
groovyc -cp "dbb-stubs.jar:slf4j-api-1.7.36.jar:commons-cli-1.5.0.jar" \
        -d build/out examples/CLILogEncoding.groovy
```

## On host (USS)

**Never** put `dbb-stubs.jar` on the runtime classpath there — the real
`$DBB_HOME/lib/dbb.jar` (and its dependent slf4j/commons-cli jars, already
on the DBB-provided classpath) fully replaces it. The stubs exist purely
to let you write and compile-check `.groovy` task scripts (and any
Java/Groovy classes that reference `TaskScript`, `BuildContext`,
`TaskVariables`, `TaskConstants`) in your IDE/CI before pushing to Git
and running/testing for real on z/OS.

## Extending

Follow the existing "Inject traits/interfaces, not concretions" principle
from Strategy D: if a Groovy task needs another DBB class
(`com.ibm.dbb.build.BuildException`, `com.ibm.dbb.utils.GitUtilities`,
`com.ibm.dbb.build.ScriptMappings`, `com.ibm.dbb.task.AbstractTask`,
...), add a matching minimal stub under `src/stubs/groovy/com/ibm/dbb/...`
rather than pulling in any real DBB jar — that keeps the whole tree
license-clean and buildable without network/USS access.
