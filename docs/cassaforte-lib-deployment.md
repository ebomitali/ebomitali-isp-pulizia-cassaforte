# Deployment della libreria cassaforte in DBB

## Obiettivo

Impacchettare l'implementazione della pulizia cassaforte (`lib/` e `tasks/`) come libreria riutilizzabile, in modo che gli script di entry point (`PuliziaAmbienti.groovy`, `RemoveCassaforte.groovy`) rimangano thin wrapper che richiamano solo le interfacce di business logic.

---

## Opzione A — Sorgenti su classpath

Groovy compila i file `.groovy` on-demand al primo caricamento e li mette in cache. Zero step di build aggiuntivi.

### Struttura di deployment su USS

```
/u/app/cassaforte/
├── lib/
│   ├── ZosFileOps.groovy
│   ├── LocalFileOps.groovy       # solo per test locali
│   └── ZosFileOpsUSS.groovy
├── tasks/
│   ├── PatternMatcher.groovy
│   ├── DeletionRule.groovy
│   ├── DeletionRulesLoader.groovy
│   ├── LibraryNameResolver.groovy
│   ├── EnvironmentChain.groovy
│   ├── BuildMapClient.groovy
│   ├── LocalBuildMapClient.groovy
│   ├── DeleteCassaforteLogic.groovy
│   ├── SfilamentoLogic.groovy
│   └── PrevEnvCleanLogic.groovy
├── build-data/
│   └── rules.csv
├── PuliziaCassaforte.groovy
└── PuliziaPostBuild.groovy
```

### Configurazione DBB task (Language YAML)

```yaml
tasks:
  - name: CleanPrevEnv
    type: task
    script: /u/app/cassaforte/PuliziaPostBuild.groovy
    classpath:
      - /u/app/cassaforte/lib
      - /u/app/cassaforte/tasks
```

### Invocazione standalone (Jenkins)

```bash
groovyz -cp /u/app/cassaforte/lib:/u/app/cassaforte/tasks \
        /u/app/cassaforte/PuliziaCassaforte.groovy \
        <file-lista> <build-group> <environment>
```

### Script thin wrapper — `PuliziaPostBuild.groovy`

```groovy
@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript

def member      = config.getStringVariable('MEMBER')
def fileExt     = config.getStringVariable('FILE_EXT')
def environment = config.getStringVariable('CLI_BUILDENV')
def buildGroup  = config.getStringVariable('CLI_BUILDGROUP') ?: config.getStringVariable('BUILDGROUP')
def system      = config.getStringVariable('C1SYSTEM') ?: buildGroup?.tokenize('_')?.first()?.toUpperCase() ?: ''

def sourcePath  = context.getBuildFile()

def rulesPath   = new File(context.getWorkingDirectory(), 'build-data/rules.csv').absolutePath
def ops         = new ZosFileOpsUSS()
def rules       = new DeletionRulesLoader().load(rulesPath)

// TODO: replace with DBB build-result map client when available
def bmFile      = new File(context.getWorkingDirectory(), 'build-data/buildmap.json')
def buildMap    = bmFile.exists()
    ? new LocalBuildMapClient(bmFile.absolutePath)
    : [getGeneratedObjects: { sp, bg -> [] }] as BuildMapClient

def deleteLogic = new DeleteCassaforteLogic(ops: ops, rules: rules, buildMap: buildMap)
def prevClean   = new PrevEnvCleanLogic(deleteLogic: deleteLogic)

def count = prevClean.execute(sourcePath, fileExt, environment, system, buildGroup)

println "PuliziaPostBuild: env=${environment} predecessor=${new EnvironmentChain().getPredecessor(environment)} deleted=${count}"

return 0   // DBB requires Integer return — any other type triggers BGZZB0043W warning with RC 0
```

---

## Opzione B — JAR pre-compilato

Compila una volta sola, distribuisce il JAR come artefatto versionato tra gli ambienti.

### Build del JAR (su USS)

```bash
# Compila lib/ e tasks/ in una directory di classi
groovyc -cp $DBB_HOME/lib/dbb-toolkit.jar \
        -d /u/app/cassaforte/classes \
        /u/app/cassaforte/lib/*.groovy \
        /u/app/cassaforte/tasks/*.groovy

# Impacchetta in JAR
jar cf /u/app/cassaforte/cassaforte-lib-1.0.jar \
    -C /u/app/cassaforte/classes .
```

### Configurazione DBB task

```yaml
tasks:
  - name: CleanPrevEnv
    type: task
    script: /u/app/cassaforte/PuliziaPostBuild.groovy
    classpath:
      - /u/app/cassaforte/cassaforte-lib-1.0.jar
```

### Invocazione standalone

```bash
groovyz -cp /u/app/cassaforte/cassaforte-lib-1.0.jar \
        /u/app/cassaforte/PuliziaCassaforte.groovy \
        <file-lista> <build-group> <environment>
```

---

## Confronto

| | Opzione A — Sorgenti | Opzione B — JAR |
|--|--|--|
| Deployment | Copia directory su USS | Compila + distribuisce JAR |
| Modifica rapida | Edit diretto su USS, subito attivo | Ricompila + redeploy JAR |
| Overhead primo run | Groovy compila e cacha on-demand | Nessuno (già compilato) |
| Versioning | Implicito (data file) | Esplicito (nome JAR, es. `cassaforte-lib-1.2.jar`) |
| Debugging su USS | Leggibile direttamente | Richiede i sorgenti separati |
| Gestione ambienti | Sincronizzazione manuale dei file | Un JAR per versione, promuovibile |

---

## Raccomandazione

**Durante lo sviluppo e il collaudo**: usare **Opzione A** — sorgenti su classpath. Nessun step di build aggiuntivo, modifiche immediate senza redeploy.

**In produzione / ambienti stabili**: migrare a **Opzione B** — JAR versionato. Garantisce che tutti gli ambienti (ST, PR) usino esattamente la stessa versione della libreria, indipendentemente da modifiche ai file su USS.

---

## Note operative

- I file `.groovy` su USS devono essere taggati IBM-1047: `chtag -tc IBM-1047 *.groovy`
- Per l'Opzione A, aggiungere il tag anche alle directory `lib/` e `tasks/` dopo ogni upload
- Con l'Opzione B, il JAR non richiede tagging (è binario)
- `groovyc` su USS richiede che `$DBB_HOME/lib/dbb-toolkit.jar` sia nel classpath per risolvere `TaskScript`; la libreria cassaforte stessa non dipende da DBB (solo `ZosFileOpsUSS.groovy` e `PuliziaAmbienti.groovy` hanno dipendenze IBM)
