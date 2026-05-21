# Multi-Module Gradle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the single-project Gradle build into a standard two-submodule multi-project layout (`library` + `front-end`).

**Architecture:** Root project becomes a pure orchestrator (`settings.gradle` + thin `build.gradle`). `library` module owns all compilation — main, zos, and test source sets plus the `zosJar` task. `front-end` module is a logical container for USS entry-point scripts with `base` plugin only.

**Tech Stack:** Gradle (Groovy DSL), Groovy 4, Spock 2

---

## File Map

| Action | Path |
|---|---|
| Move | `src/` → `library/src/` |
| Move | `libs/` → `library/libs/` |
| Move | `scripts/` → `front-end/scripts/` |
| Create | `library/build.gradle` |
| Create | `front-end/build.gradle` |
| Modify | `settings.gradle` |
| Replace | `build.gradle` (root) |

---

### Task 1: Create `library/` directory and move sources

**Files:**
- Move: `src/` → `library/src/`
- Move: `libs/` → `library/libs/`

- [ ] **Step 1: Create library directory and move sources with git**

```bash
mkdir library
git mv src library/
git mv libs library/
```

- [ ] **Step 2: Verify moves**

```bash
ls library/
```

Expected output:
```
libs
src
```

```bash
ls library/src/
```

Expected output:
```
main  test  zos
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: move src/ and libs/ into library/ module"
```

---

### Task 2: Create `library/build.gradle`

**Files:**
- Create: `library/build.gradle`

- [ ] **Step 1: Write `library/build.gradle`**

Create file `library/build.gradle` with this exact content:

```groovy
// Choose Local build or z/OS/CI full build
// To build for local testing:
//  ./gradlew :library:clean :library:build
// To build for z/OS, ensure IBM jars are in libs/ and run with -PzosBuild:
//   ./gradlew :library:clean :library:build -PzosBuild

plugins {
    id 'groovy'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

sourceSets {
    main {
        groovy {
            srcDirs = ['src/main/groovy']
        }
    }
    // ZosFileOpsUSS: compiled separately with IBM jars; packaged into pulizia-cassaforte-zos.jar.
    // Never folded into main — keeps pulizia-cassaforte.jar free of IBM deps.
    zos {
        groovy {
            srcDirs = ['src/zos/groovy']
            compileClasspath += sourceSets.main.output + configurations.compileClasspath + fileTree('libs') { include '*.jar' }
        }
    }
    test {
        groovy {
            srcDirs = ['src/test/groovy']
        }
    }
}

dependencies {
    implementation 'org.apache.groovy:groovy-all:4.0.25'
    testImplementation 'org.spockframework:spock-core:2.3-groovy-4.0'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.10.2'
}

test {
    useJUnitPlatform()
    outputs.upToDateWhen { false }
}

// Packages ZosFileOpsUSS into pulizia-cassaforte-zos.jar for USS deployment.
// IBM jars must be present in libs/. Activated automatically by -PzosBuild.
tasks.register('zosJar', Jar) {
    dependsOn compileZosGroovy
    from sourceSets.zos.output
    archiveBaseName = 'pulizia-cassaforte-zos'
}

// -PzosBuild: verify IBM jars exist, then wire zosJar into the standard build lifecycle.
if (project.hasProperty('zosBuild')) {
    def ibmJars = fileTree('libs') { include '*.jar' }
    if (ibmJars.isEmpty()) {
        throw new GradleException('-PzosBuild requires IBM jars in libs/. See CLAUDE.md.')
    }
    tasks.named('build') { dependsOn 'zosJar' }
}
```

- [ ] **Step 2: Commit**

```bash
git add library/build.gradle
git commit -m "refactor: add library/build.gradle"
```

---

### Task 3: Create `front-end/` directory and move scripts

**Files:**
- Move: `scripts/` → `front-end/scripts/`

- [ ] **Step 1: Create front-end directory and move scripts with git**

```bash
mkdir front-end
git mv scripts front-end/
```

- [ ] **Step 2: Verify move**

```bash
ls front-end/
```

Expected output:
```
scripts
```

```bash
ls front-end/scripts/
```

Expected output (order may vary):
```
GetBuildMap.groovy         PuliziaPostBuild.groovy
GetBuildMapFields.groovy   PuliziaPostBuildLocal.groovy
GetBuildMapFieldsNoArgs.groovy  QueryBuildMap.groovy
PuliziaCassaforte.groovy   QueryBuildMapOnPuliziaCassaforteJar.groovy
PuliziaCassaforteLocal.groovy  add_table_borders.py
build-data                 lib
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: move scripts/ into front-end/ module"
```

---

### Task 4: Create `front-end/build.gradle`

**Files:**
- Create: `front-end/build.gradle`

- [ ] **Step 1: Write `front-end/build.gradle`**

Create file `front-end/build.gradle` with this exact content:

```groovy
plugins {
    id 'base'
}

// Scripts in scripts/ are USS entry points, not compiled.
// Runtime dependency on :library — run with:
//   groovyz -cp ${DBB_BUILD}/groovy/pulizia-cassaforte/lib/pulizia-cassaforte.jar <script>.groovy
```

- [ ] **Step 2: Commit**

```bash
git add front-end/build.gradle
git commit -m "refactor: add front-end/build.gradle"
```

---

### Task 5: Update root build files

**Files:**
- Modify: `settings.gradle`
- Replace: `build.gradle` (root)

- [ ] **Step 1: Update `settings.gradle`**

Replace the entire content of `settings.gradle` with:

```groovy
rootProject.name = 'pulizia-cassaforte'
include 'library', 'front-end'
```

- [ ] **Step 2: Replace root `build.gradle`**

Replace the entire content of `build.gradle` (root) with:

```groovy
allprojects {
    repositories {
        mavenCentral()
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add settings.gradle build.gradle
git commit -m "refactor: update root build files for multi-project layout"
```

---

### Task 6: Verify build

- [ ] **Step 1: Run library tests**

```bash
./gradlew :library:test
```

Expected: `BUILD SUCCESSFUL` with all Spock specs passing (same count as before the refactor).

- [ ] **Step 2: Run full build from root**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. Both `:library:build` and `:front-end:build` complete.

- [ ] **Step 3: Verify JAR output path**

```bash
ls library/build/libs/
```

Expected:
```
pulizia-cassaforte.jar
```

- [ ] **Step 4: Verify project structure recognized by Gradle**

```bash
./gradlew projects
```

Expected output includes:
```
Root project 'pulizia-cassaforte'
+--- Project ':front-end'
\--- Project ':library'
```

- [ ] **Step 5: Commit if anything was missed**

If no uncommitted changes, skip. Otherwise:

```bash
git add -A
git commit -m "refactor: finalize multi-module Gradle layout"
```
