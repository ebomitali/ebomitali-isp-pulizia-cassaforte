# Allenamento librerie di cassaforte

Le librerie cassaforte sono delle librerie che contengono gli oggetti che alimentano il processo di promote. Il processo di promote o deploy, esterno a DBB utilizzare queste librerie per gestire l’ambiente runtime. Le librerie cassaforte non sono quindi utilizzate dal runtime ma servono per alimentare il processo di deploy che va a modificare il runtime.

Sono previsti due script:

- Script groovyz “RemoveCassaforte"
- Task DBB groovy “PuliziaAmbienti"

## Script e contesto

### Script 1 — `RemoveCassaforte.groovy` (standalone, via `groovyz`)

**Quando viene invocato:** **prima** della compilazione DBB, dalla pipeline Jenkins.

**Chi lo invoca:** la pipeline Jenkins direttamente, tramite `groovyz`.

**Sintassi di invocazione:**
> Nota: il build group somiglia all’environment ma non è l’environment per cui va fornito esplicitamente

```groovy
groovyz RemoveCassaforte.groovy <file-lista-oggetti> <build group> <environment>
```

**Cosa gestisce** — i tre scenari in cui un sorgente non viene compilato ma le sue librerie vanno comunque aggiornate:

| Scenario               | Azione sulle librerie     |
|------------------------|---------------------------|
| **Rimozione sorgente** | Cancella il membro dalla libreria cassaforte dell'ambiente indicato |
| **Cambio Processor Group / Type** | Prima cancella dalla libreria del vecchio PG, poi la compilazione successiva metterà il membro nella libreria del nuovo PG |
| **Sfilamento** | Cancella il membro dalla libreria corrente e, per certi type (es. JCL), lo ripristina copiando la versione dall'ambiente superiore nella stage chain (TOCOLB) |

>  ❓ per tutti gli ambienti o solo per alcuni

>  ❓ TOCOLB è un nome generico per definire l’ambiente superiore? Basta la catena di ambienti (vedi dopo)

Il file di input ha una riga per oggetto nel formato `<azione>,<path>` dove `<azione>` è `C` (cancellazione) o `S` (sfilamento).ù

Esempio:
```
C,/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/abcdef.cbl
S,/dbb/DEE/IBM/yn_r_01_ato_r1/src/cobol/batch/abcdef.cbl
```

---

### Script 2 — `PuliziaAmbienti.groovy` (task DBB, dentro la Language pipeline)

**Quando viene invocato:** **durante** la compilazione DBB, come `type: task` step nella Language YAML, **dopo** che lo step di compilazione è andato a buon fine.

**Chi lo invoca:** il framework DBB stesso, nell'ambito dell'iterazione per-file della Language task.

**Cosa gestisce** — un solo scenario:

| Scenario | Azione sulle librerie |
|---|---|
| **Build in ambiente successivo ad ATO/ATI** (es. ST, PR) | Dopo compilazione riuscita, cancella il membro dalla libreria cassaforte dell'**ambiente predecessore** nel ciclo di vita |

La ragione è il meccanismo delle librerie concatenate: se LOAD o COPY esistono ancora nell'ambiente precedente, verrebbero visti al posto della versione appena compilata.

---

### Schema visuale del flusso pipeline

```
Pipeline Jenkins
│
├─ 1. Prepara file-lista oggetti da cancellare/sfilare
│
├─ 2. groovyz RemoveCassaforte.groovy <lista> <env>
│        │
│        ├─ per ogni riga C,<path>  → cancella membro dalla libreria dell'ambiente
│        └─ per ogni riga S,<path>  → cancella + eventuale ripristino da TOCOLB superiore
│
├─ 3. dbb build <lista-oggetti-da-compilare> --environment <env> ...
│        │
│        └─ Per ogni file, Language pipeline DBB:
│               ├─ ResolveStage.groovy    (step 1: risolve STAGE, librerie)
│               ├─ Compile (mvs step)
│               ├─ Link    (mvs step)
│               └─ CleanPrevEnvLib.groovy (step 4: se compile OK, cancella da libreria env precedente)
│
└─ 4. Resto della pipeline (listing, CMOD, file esca, ...)
```

### Sequenza degli ambienti

Gli ambienti definiti in Intesa San Paolo sono i seguenti:

- ATI  	Build eseguita da pipeline di GENERATE per ATI1
- ATO 	Build eseguita in Application 
- ST 	Build eseguita in System Test 
- PR  	Build eseguita in Produzione
- EM  	Build eseguita in Emergenza

E definiscono le seguenti catene:

```text
ATI -> ATO -> ST -> PT
                    EM
```

> **Nota** Code & Build BES C3 del 23/3/2026

EM non ha ambienti precedenti o successivi per cui le operazioni si riducono alla cancellazione di sorgenti/oggetti generati.

## Scenari

### Rimozione sorgente
Lo script RimuoviDaCassaforte.groovy procede alla rimozione dei sorgenti (interpretati) o degli oggetti generati (compilati) dalla build del sorgente fornito.

1. Prende la lista dei sorgenti
2. Usa il path assieme al build group come chiave per interrogare la build map
3. Per ogni oggetto generato procede alla cancellazione

### Cambio Processor Group
Lo script RimuoviDaCassaforte.groovy esegue lo scenario “Rimozione Sorgente” mentre la successiva build di DBB eseguita il ripristino sotto il nuovo tipo o processor group

### Sfilamento
Lo script RimuoviDaCassaforte.groovy esegue lo scenario “Rimozione Sorgente”. Per le tipologie JCL (ovvero tipo SJCL*) vanno ripristinate gli oggetti presi dall’ambiente superiore.

### Build in ambiente successivo ad ATO/ATI
Il task è inserito nei vari language di build e viene eseguito a seguito di esecuzione con successo in coda alla build (compilazione, check etc) e successivamente al deposito del sorgente/oggetto generato nelle librerie cassaforte.
Il task viene eseguito solo in ST, PR, ovvero gli ambienti successivi ad ATO/AT1/AT2.

1. Individua l’ambiente precedente
2. Accede alla build map dell’oggetto sull’ambiente precedente (in base alla catena degli ambienti)
3. Recupera gli oggetti da cancellare (sorgenti interpretati od oggetti generati)
4. Cancella gli oggetti
5. Se il tipo è SJCL*, va nell’ambiente successivo della catena e recupera l’oggetto per copiarlo in locale. Usa la build map per trovare l’oggetto da copiare e lo piazza al posto dell’oggetto cancellato.

>❓poiché l’ambiente non è la build map, è necessario definire un mapping da ambiente a build group in modo da poter accedere ai generati dell’ambiente precedente
