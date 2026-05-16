# Strategie di Deploy — Applicazione Groovy/DBB su USS z/OS

## Contesto

L'applicazione è strutturata con:

- `src/main/groovy/` — trait + implementazione locale (sempre compilabile, no licensed jar)
- `src/zos/groovy/` — implementazione z/OS (usa `com.ibm.jzos`, licensed jar IBM)
- `src/test/groovy/` — test Spock, girano in locale con implementazione locale

L'obiettivo è portare su USS le classi compilate e/o i sorgenti z/OS in modo che `groovyz` e il DBB build engine li trovino nel classpath.

---

## Struttura del progetto

```
pulizia-cassaforte/
├── build.gradle
├── gradlew
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
├── src/
│   ├── main/groovy/
│   │   ├── FileOpsTrait.groovy          ← trait (contratto)
│   │   └── LocalFileOps.groovy          ← implementazione locale
│   ├── zos/groovy/
│   │   └── ZosFileOpsUSS.groovy         ← implementazione z/OS
│   └── test/groovy/
│       └── LocalFileOpsSpec.groovy
└── deploy/
    └── deploy-remote.sh
```

---

## Configurazione `build.gradle` comune

```groovy
plugins {
    id 'groovy'
}

sourceSets {
    main {
        groovy {
            srcDirs = ['src/main/groovy']   // zos mai incluso in locale
        }
    }
    zos {
        groovy {
            srcDirs = ['src/zos/groovy']
            compileClasspath += sourceSets.main.output
        }
    }
    test {
        groovy {
            srcDirs = ['src/test/groovy']
        }
    }
}

dependencies {
    implementation 'org.apache.groovy:groovy:4.0.x'
    testImplementation 'org.spockframework:spock-core:2.x-groovy-4.0'

    // Licensed jar — solo se -PzosBuild e jar disponibile in locale
    if (project.hasProperty('zosBuild')) {
        zosImplementation files('libs/ibm-jzos.jar')
        sourceSets.main.groovy.srcDirs += 'src/zos/groovy'
    }
}

test {
    outputs.upToDateWhen { false }
}

// Task comune: prepara directory build/deploy con gli artifact da trasferire
tasks.register('prepareDeployArtifacts') {
    dependsOn build
    group = 'deploy'

    doLast {
        def deployDir = layout.buildDirectory.dir('deploy').get().asFile
        deployDir.mkdirs()

        copy {
            from(layout.buildDirectory.dir('libs'))
            into(deployDir)
            include '*.jar'
        }

        copy {
            from('src/zos/groovy')
            into("${deployDir}/groovy")
            include '**/*.groovy'
        }

        println "Artifacts pronti in: ${deployDir}"
    }
}
```

---

## Strategia B — Compila e fa il deploy su USS (Gradle su USS)

### Prerequisiti

- Gradle installato su USS (o Gradle Wrapper disponibile nel repository)
- Sorgenti sincronizzati su USS via `scp` o `git clone`
- Le licensed jar IBM sono già disponibili nel classpath di `groovyz`

### Flusso

```
[macOS locale]                          [USS z/OS]
─────────────────────                   ──────────────────────
git push / scp sorgenti  ──────────►   ./gradlew build -PzosBuild
                                              │
                                        build/libs/app.jar
                                              │
                                        cp → $DBB_HOME/lib/ext/
                                        cp → $DBB_BUILD/groovy/
```

### Script di deploy (`deploy/deploy-uss-build.sh`)

```bash
#!/bin/bash
# Eseguito su USS dopo aver sincronizzato i sorgenti

APP_HOME=/u/dbb/pulizia-cassaforte
DBB_EXT=$DBB_HOME/lib/ext
DBB_GROOVY=$DBB_BUILD/groovy

cd $APP_HOME

# 1. Compila con sorgenti z/OS (le licensed jar sono già in classpath USS)
./gradlew build -PzosBuild

# 2. Deploy jar
cp build/libs/pulizia-cassaforte.jar $DBB_EXT/

# 3. Verifica
echo "Jar installate:"
ls -la $DBB_EXT/*.jar
```

### `gradle-wrapper.properties` per USS

```properties
# Se USS ha accesso internet:
distributionUrl=https\://services.gradle.org/distributions/gradle-8.x-bin.zip

# Se USS è isolata (scarica il zip e trasferiscilo prima):
distributionUrl=file\:///u/dbb/gradle/gradle-8.x-bin.zip
```

### Pro e contro

| Pro | Contro |
|-----|--------|
| Licensed jar natively disponibili | Gradle deve essere installato su USS |
| Compatibilità JVM z/OS garantita | Ciclo di sviluppo più lento |
| Pipeline CI/CD Jenkins naturale | USS non è ideale per sviluppo iterativo |
| Nessuna licensed jar necessaria in locale | Richiede sincronizzazione sorgenti su USS |

---

## Strategia C — Compila in locale con `-PzosBuild`, trasferisci jar su USS

### Prerequisiti

- Licensed jar IBM disponibili in locale (es. `libs/ibm-jzos.jar`)
- JVM locale compatibile con z/OS (stessa major version)

### Flusso

```
[macOS locale]                          [USS z/OS]
─────────────────────                   ──────────────────────
./gradlew build -PzosBuild  ──jar──►   $DBB_HOME/lib/ext/
  (compila anche ZosFileOpsUSS)
```

### Script di deploy (`deploy/deploy-local-build.sh`)

```bash
#!/bin/bash
# Uso: ./deploy/deploy-local-build.sh utente@zos-host

REMOTE=${1:?"Uso: $0 utente@zos-host"}

# 1. Build locale completo con sorgenti z/OS
echo ">>> Build locale con -PzosBuild..."
./gradlew build -PzosBuild

# 2. Trasferisci jar
echo ">>> Trasferimento jar..."
scp build/libs/pulizia-cassaforte.jar ${REMOTE}:'$DBB_HOME/lib/ext/'

# 3. Verifica
echo ">>> Verifica su USS..."
ssh ${REMOTE} 'ls -la $DBB_HOME/lib/ext/*.jar'

echo ">>> Deploy completato"
```

### Pro e contro

| Pro | Contro |
|-----|--------|
| Build environment controllato in locale | Licensed jar IBM necessarie in locale |
| Non serve Gradle su USS | Rischio incompatibilità JVM locale/z/OS |
| Ciclo sviluppo rapido | Jar locale potrebbe differire da USS |
| Deploy incrementale semplice | |

---

## Strategia D — Compila jar locale (senza licensed jar), trasferisci jar + sorgenti `.groovy` su USS

### Idea chiave

La jar contiene solo le classi compilabili senza licensed jar (trait + `LocalFileOps`).
I file `.groovy` z/OS vengono trasferiti come **sorgenti** e interpretati a runtime da `groovyz`,
che ha già le licensed jar nel suo classpath nativo.

### Flusso

```
[macOS locale]                          [USS z/OS]
─────────────────────                   ──────────────────────
./gradlew build           ──jar──────►  $DBB_HOME/lib/ext/
  (no -PzosBuild,
   no licensed jars)      ──.groovy──►  $DBB_BUILD/groovy/
                            (sorgenti, interpretati da groovyz a runtime)
```

### Perché funziona

`groovyz` su USS ha già nel classpath:

- Tutte le jar DBB (`$DBB_HOME/lib/*.jar`)
- Le IBM licensed jar (`ibm-jzos.jar`, ecc.)
- `$DBB_HOME/lib/ext/*.jar` — dove viene installata la jar locale

Quindi `ZosFileOpsUSS.groovy` viene interpretato direttamente da `groovyz` a runtime,
senza bisogno di compilarlo in locale.

### Task Gradle aggiuntivo in `build.gradle`

```groovy
tasks.register('deployRemote') {
    dependsOn build
    group = 'deploy'
    description = 'Assembla jar + sorgenti groovy zos per il deploy su USS'

    doLast {
        def deployDir = layout.buildDirectory.dir('deploy').get().asFile
        deployDir.mkdirs()

        // Jar con classi main (no zos)
        copy {
            from(layout.buildDirectory.dir('libs'))
            into(deployDir)
            include '*.jar'
        }

        // Sorgenti groovy zos (interpretati da groovyz a runtime su USS)
        copy {
            from('src/zos/groovy')
            into("${deployDir}/groovy")
            include '**/*.groovy'
        }

        println "Artifacts pronti in: ${deployDir}"
        println "  jar    → \$DBB_HOME/lib/ext/"
        println "  groovy → \$DBB_BUILD/groovy/"
    }
}
```

### Script di deploy (`deploy/deploy-remote.sh`)

```bash
#!/bin/bash
# Uso: ./deploy/deploy-remote.sh utente@zos-host

REMOTE=${1:?"Uso: $0 utente@zos-host"}
BUILD_DIR=build/deploy

# 1. Build locale (solo sorgenti main, nessuna licensed jar necessaria)
echo ">>> Build locale..."
./gradlew deployRemote

# 2. Trasferisci jar in DBB_HOME/lib/ext
echo ">>> Trasferimento jar..."
scp ${BUILD_DIR}/*.jar ${REMOTE}:'$DBB_HOME/lib/ext/'

# 3. Trasferisci sorgenti groovy zos in DBB_BUILD/groovy
echo ">>> Trasferimento sorgenti groovy zos..."
scp ${BUILD_DIR}/groovy/*.groovy ${REMOTE}:'$DBB_BUILD/groovy/'

# 4. Conversione encoding: USS richiede IBM-1047 per groovyz
echo ">>> Conversione encoding IBM-1047..."
ssh ${REMOTE} '
    for f in $DBB_BUILD/groovy/Zos*.groovy; do
        iconv -f UTF-8 -t IBM-1047 "$f" > "$f.tmp" && mv "$f.tmp" "$f"
        chtag -t -c IBM-1047 "$f"
        echo "  convertito: $f"
    done
'

# 5. Verifica
echo ">>> Verifica su USS..."
ssh ${REMOTE} '
    echo "Jar installate:"
    ls -la $DBB_HOME/lib/ext/*.jar
    echo "Script Groovy installati:"
    ls -T $DBB_BUILD/groovy/Zos*.groovy
'

echo ">>> Deploy completato"
```

### Attenzione all'encoding

I file `.groovy` trasferiti via `scp` arrivano in **UTF-8**.
`groovyz` su USS li richiede in **IBM-1047**. La conversione è obbligatoria:

```bash
iconv -f UTF-8 -t IBM-1047 ZosFileOpsUSS.groovy > ZosFileOpsUSS.groovy.tmp
mv ZosFileOpsUSS.groovy.tmp ZosFileOpsUSS.groovy
chtag -t -c IBM-1047 ZosFileOpsUSS.groovy
```

Verifica con:
```bash
ls -T $DBB_BUILD/groovy/ZosFileOpsUSS.groovy
# output atteso: t IBM-1047 T=on ZosFileOpsUSS.groovy
```

### Pro e contro

| Pro | Contro |
|-----|--------|
| Nessuna licensed jar necessaria in locale | Sorgenti `.groovy` interpretati (non compilati) |
| Non serve Gradle su USS | Errori di sintassi scoperti solo a runtime su USS |
| Ciclo di sviluppo rapido in locale | Conversione encoding obbligatoria post-trasferimento |
| Compatibilità JVM garantita (runtime USS) | |
| Deploy semplicissimo (jar + file testo) | |

---

## Tabella comparativa

| Criterio | B — Build su USS | C — Build locale con licensed | D — Build locale senza licensed |
|---|---|---|---|
| Licensed jar in locale | Non necessarie | **Necessarie** | Non necessarie |
| Gradle su USS | **Necessario** | Non necessario | Non necessario |
| Compatibilità JVM garantita | Sì | Rischio | Sì (runtime USS) |
| Sorgenti zos compilati | Su USS | In locale | Interpretati a runtime |
| Errori zos scoperti in | Build USS | Build locale | Runtime USS |
| Ciclo di sviluppo | Lento | Rapido | Rapido |
| Conversione encoding | Non necessaria | Non necessaria | **Obbligatoria** |
| Pipeline Jenkins | Naturale | Richiede setup | Naturale |
| **Consigliata se…** | Gradle su USS disponibile | Licensed jar in locale | Nessun requisito aggiuntivo |

---

## Destinazioni di deploy su USS

| Artifact | Destinazione | Visibilità |
|---|---|---|
| `*.jar` (classi compilate) | `$DBB_HOME/lib/ext/` | Tutti gli script `groovyz` e DBB build |
| `*.groovy` (sorgenti zos) | `$DBB_BUILD/groovy/` | Scoperta automatica da `dbb build` |
| `*.groovy` (script standalone) | Path arbitrario su USS | Da passare esplicitamente a `groovyz` |

---

*Documento generato per il progetto BES C3 — Cantiere 3 Broadcom Exit Strategy (ISP)*
