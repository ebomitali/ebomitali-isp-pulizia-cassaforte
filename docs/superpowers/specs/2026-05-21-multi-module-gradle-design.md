# Multi-module Gradle Design

Date: 2026-05-21

## Goal

Convert the single-project Gradle build into a standard two-module multi-project layout.

## Modules

| Module | Purpose |
|---|---|
| `library` | Compiles Groovy sources into `pulizia-cassaforte.jar` and `pulizia-cassaforte-zos.jar` |
| `front-end` | Logical container for USS entry-point scripts; no compilation |

## Directory Structure

```
pulizia-cassaforte/          (root — orchestration only)
  settings.gradle
  build.gradle
  gradle/, gradlew, gradlew.bat
  docs/, example/, helper/, CLAUDE.md, …

  library/
    build.gradle
    src/
      main/groovy/
      zos/groovy/
      test/groovy/
      test/resources/
    libs/                    (IBM jars for zosBuild)

  front-end/
    build.gradle
    scripts/
      PuliziaCassaforte.groovy
      PuliziaPostBuild.groovy
      PuliziaCassaforteLocal.groovy
      PuliziaPostBuildLocal.groovy
      GetBuildMap.groovy
      GetBuildMapFields.groovy
      GetBuildMapFieldsNoArgs.groovy
      QueryBuildMap.groovy
      QueryBuildMapOnPuliziaCassaforteJar.groovy
      add_table_borders.py
      build-data/
        rules.csv
        stage-map.csv
      lib/
```

## File Moves

| From (root) | To |
|---|---|
| `src/` | `library/src/` |
| `libs/` | `library/libs/` |
| `scripts/` | `front-end/scripts/` |

Everything else stays at root.

## Build Files

### `settings.gradle` (root)

```groovy
rootProject.name = 'pulizia-cassaforte'
include 'library', 'front-end'
```

### `build.gradle` (root)

```groovy
allprojects {
    repositories {
        mavenCentral()
    }
}
```

### `library/build.gradle`

Current root `build.gradle` content verbatim, with the `repositories` block removed (inherited from root).

```groovy
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
        groovy { srcDirs = ['src/main/groovy'] }
    }
    zos {
        groovy {
            srcDirs = ['src/zos/groovy']
            compileClasspath += sourceSets.main.output + configurations.compileClasspath + fileTree('libs') { include '*.jar' }
        }
    }
    test {
        groovy { srcDirs = ['src/test/groovy'] }
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

tasks.register('zosJar', Jar) {
    dependsOn compileZosGroovy
    from sourceSets.zos.output
    archiveBaseName = 'pulizia-cassaforte-zos'
}

if (project.hasProperty('zosBuild')) {
    def ibmJars = fileTree('libs') { include '*.jar' }
    if (ibmJars.isEmpty()) {
        throw new GradleException('-PzosBuild requires IBM jars in libs/. See CLAUDE.md.')
    }
    tasks.named('build') { dependsOn 'zosJar' }
}
```

### `front-end/build.gradle`

```groovy
plugins {
    id 'base'
}

// Scripts in scripts/ are USS entry points, not compiled.
// Runtime dependency on :library — run with:
//   groovyz -cp ${DBB_BUILD}/groovy/pulizia-cassaforte/lib/pulizia-cassaforte.jar <script>.groovy
```

## Gradle Task Mapping

| Command | Before | After |
|---|---|---|
| Compile + test | `./gradlew test` | `./gradlew :library:test` or `./gradlew test` (root delegates) |
| Build JAR | `./gradlew jar` | `./gradlew :library:jar` |
| Build zos JAR | `./gradlew zosJar` | `./gradlew :library:zosJar` |
| Full build | `./gradlew build` | `./gradlew build` (root delegates) |

Root-level `./gradlew test` and `./gradlew build` still work — Gradle propagates to all subprojects.  
`front-end` uses `base` plugin only, so it has no `test` or `compileGroovy` task.

## Out of Scope

- No changes to business logic or source files.
- No new Gradle tasks in `front-end` (purely logical container).
- No changes to USS deployment commands.
