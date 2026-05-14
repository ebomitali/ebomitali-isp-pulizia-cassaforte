# Groovy z/OS File Operations — Architettura con Abstraction Layer

## Obiettivo

Sviluppare in locale la logica di gestione file z/OS (cancellazione, copia, spostamento di dataset/membri PDS)
senza dipendere dalle JAR IBM specifiche per il mainframe (`jzos.jar`, `dbb-toolkit.jar`),
mantenendo la possibilità di migrare su z/OS USS senza modificare il codice di business.

---

## Principio architetturale

Un **thin abstraction layer** separa la logica di business dalle API di piattaforma.
La logica rimane identica su entrambi gli ambienti; cambia solo l'implementazione iniettata.

```
┌─────────────────────────────────────────────────────┐
│                  LOGICA DI BUSINESS                 │
│   CassaforteDelete / CassafortePreviousEnvClean     │
│          (nessuna dipendenza da piattaforma)         │
└──────────────────────┬──────────────────────────────┘
                       │ dipende da (trait)
                       ▼
              ┌────────────────┐
              │   ZosFileOps   │  ← trait Groovy (interfaccia)
              └───────┬────────┘
            ┌─────────┴──────────┐
            ▼                    ▼
   ┌─────────────────┐  ┌──────────────────────┐
   │  LocalFileOps   │  │   ZosFileOpsUSS       │
   │  (java.nio)     │  │  (ZFile / BPXWDYN)   │
   │  locale, no IBM │  │  solo su mainframe    │
   └─────────────────┘  └──────────────────────┘
```

---

## Struttura del progetto

```
scripts/
├── lib/
│   ├── ZosFileOps.groovy           # Trait (interfaccia)
│   ├── LocalFileOps.groovy         # Implementazione locale — java.nio.file
│   └── ZosFileOpsUSS.groovy        # Implementazione USS — ZFile/BPXWDYN
│                                   # (compilata solo su mainframe)
├── tasks/
│   ├── CassaforteDeleteLogic.groovy        # Logica pura — nessuna dipendenza IBM
│   ├── CassaforteDelete.groovy             # Wrapper DBB TaskScript (z/OS)
│   └── CassafortePreviousEnvClean.groovy   # Wrapper DBB TaskScript (z/OS)
├── run_local.groovy                # Entry point sviluppo locale
└── run_uss.groovy                  # Entry point z/OS USS (non-DBB)
```

---

## Componenti

### ZosFileOps — trait (interfaccia)

Definisce il contratto condiviso tra le due implementazioni.
Non importa nulla di IBM; usa solo tipi Groovy/Java standard.

| Metodo | Descrizione |
|---|---|
| `exists(path)` | Controlla se dataset/file esiste |
| `delete(path)` | Cancella membro PDS o file USS |
| `copy(src, dst)` | Copia membro PDS o file USS |
| `move(src, dst)` | Sposta/rinomina dataset o file |
| `list(container)` | Lista membri PDS o file di directory |

Convenzione per il parametro `path`:

| Formato | Significato |
|---|---|
| `//DATASET.NAME(MEMBER)` | Membro PDS qualificato |
| `//DATASET.NAME` | Dataset o PDS (root) |
| `/u/app/file.txt` | File USS (path assoluto) |

---

### LocalFileOps — implementazione locale

Traduce il naming z/OS in path del filesystem locale per simulare la struttura dei dataset.

**Mapping naming:**

```
//ISP.CICS.LOADLIB(MIOPGM)
  → /tmp/zos-sim/ISP.CICS.LOADLIB/MIOPGM

//ISP.CICS.LOADLIB
  → /tmp/zos-sim/ISP.CICS.LOADLIB/
```

Dipendenze: solo `java.nio.file.*` — nessuna JAR IBM richiesta.

---

### ZosFileOpsUSS — implementazione mainframe

Usata esclusivamente su z/OS USS, eseguita con `groovyz`.
Implementa le stesse operazioni tramite API IBM:

| Operazione | API utilizzata |
|---|---|
| `exists` | `ZFile.dsExists()` |
| `delete` membro PDS | `ZFile` + BPXWDYN `free` |
| `copy` membro PDS | `ZFile` streams |
| `list` PDS | `ZFile.listMembers()` |
| `delete/move` USS | `java.io.File` (path USS standard) |

> La classe non viene caricata dalla JVM in locale anche se presente nel classpath,
> perché l'istanziazione avviene solo nell'entry point z/OS.

---

### CassaforteDeleteLogic — logica di business

Riceve `ZosFileOps` tramite costruttore (dependency injection).
Non importa nulla di IBM né di DBB.
Può essere testata in locale con `LocalFileOps` e validata con `groovy` standard.

```groovy
class CassaforteDeleteLogic {
    ZosFileOps ops
    Map<String, String> config

    int execute(String marker) { /* logica C / S */ }
}
```

---

### CassaforteDelete.groovy — wrapper DBB TaskScript

Adattatore tra il runtime DBB e la logica pura.
Responsabilità:

1. Dichiarare `@BaseScript TaskScript` (obbligatorio per DBB)
2. Istanziare `ZosFileOpsUSS` (sempre z/OS in questo contesto)
3. Leggere le variabili da `config` (TaskVariables) e `context` (BuildContext)
4. Istanziare ed eseguire `CassaforteDeleteLogic`
5. Restituire `Integer` come RC

```groovy
@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript

def ops    = new ZosFileOpsUSS()
def config = [
    targetLoadlib : config.getStringVariable('targetLoadlib'),
    fileName      : config.getStringVariable('FILE_NAME'),
]
def logic = new CassaforteDeleteLogic(ops, config)
return logic.execute(config.getStringVariable('cassaforteMarker'))
```

---

## Pattern di iniezione per ambiente

### Sviluppo locale (Pattern 1 — due entry point)

Il pattern raccomandato per questo progetto: entry point distinti per i due ambienti.
Nessuna logica di selezione nel codice business.

```
run_local.groovy                    run_uss.groovy
     │                                   │
     ▼                                   ▼
LocalFileOps('/tmp/zos-sim')       ZosFileOpsUSS()
     │                                   │
     └──────────── CassaforteDeleteLogic ┘
```

**Esecuzione locale:**
```bash
groovy -cp lib:tasks run_local.groovy
```

**Esecuzione USS (non-DBB):**
```bash
groovyz -cp /u/app/scripts/lib:/u/app/scripts/tasks run_uss.groovy
```

---

### Integrazione DBB (Pattern 3 — wrapper TaskScript)

In produzione DBB il wrapper `CassaforteDelete.groovy` è il punto di ingresso.
L'implementazione USS è sempre quella corretta — non serve logica di selezione.

```
DBB Language task
      │
      ▼
CassaforteDelete.groovy  (TaskScript wrapper)
      │  istanzia
      ▼
ZosFileOpsUSS            (sempre z/OS nel contesto DBB)
      │  iniettata in
      ▼
CassaforteDeleteLogic    (logica pura, invariata rispetto al test locale)
```

---

### Factory con system property (Pattern 2 — opzionale)

Da usare solo se si vuole un unico script entry point che seleziona l'implementazione a runtime.
Nel contesto di questo progetto **non è necessario**.

```bash
# Locale
groovy -cp lib:tasks -Denv=local run.groovy

# z/OS USS
groovyz -cp lib:tasks -Denv=zos run.groovy
```

```groovy
// FileOpsFactory.groovy
static ZosFileOps create() {
    String env = System.getProperty('env', 'local')
    if (env == 'zos')
        return Class.forName('ZosFileOpsUSS').newInstance() as ZosFileOps
    return new LocalFileOps('/tmp/zos-sim')
}
```

> `Class.forName()` posticipa il caricamento di `ZosFileOpsUSS` a runtime,
> evitando che la JVM locale tenti di risolvere le dipendenze IBM.

---

## Flusso di sviluppo raccomandato

```
1. LOCALE
   Scrivi CassaforteDeleteLogic usando ZosFileOps (trait)
   Testa con LocalFileOps e groovy standard
        │
        ▼
2. USS (validazione API)
   Sostituisci LocalFileOps con ZosFileOpsUSS
   Esegui con groovyz — verifica comportamento dataset reali
        │
        ▼
3. DBB INTEGRATION
   Crea wrapper CassaforteDelete.groovy (@BaseScript TaskScript)
   Aggiungi come type: task nel YAML della Language pipeline
   CassaforteDeleteLogic rimane invariata
```

---

## Regole di dipendenza

| Componente | Dipende da | Non dipende da |
|---|---|---|
| `ZosFileOps` (trait) | Groovy/Java stdlib | IBM JAR, DBB |
| `LocalFileOps` | `java.nio.file` | IBM JAR, DBB |
| `ZosFileOpsUSS` | `jzos.jar`, BPXWDYN | DBB |
| `CassaforteDeleteLogic` | `ZosFileOps` (trait) | IBM JAR, DBB |
| `CassaforteDelete` (wrapper) | DBB TaskScript, `ZosFileOpsUSS` | — |

La logica di business (`CassaforteDeleteLogic`) è l'unico componente che non cambia
mai tra locale e produzione — questa è la garanzia dell'architettura.

---

## Note operative

- I file `.groovy` su USS devono essere codificati **IBM-1047** (`chtag -tc IBM-1047 file.groovy`)
- Il wrapper DBB deve restituire `Integer` — se restituisce altro tipo DBB emette `BGZZB0043W` con RC default 0
- `groovyz` (non `groovy`) è obbligatorio su USS per caricare il DBB classpath automaticamente
- La directory `/tmp/zos-sim/` simula il filesystem z/OS: ogni sotto-directory è un dataset, ogni file è un membro PDS
- Per aggiungere test: istanziare `LocalFileOps` in uno script Groovy separato — nessun framework di test richiesto
