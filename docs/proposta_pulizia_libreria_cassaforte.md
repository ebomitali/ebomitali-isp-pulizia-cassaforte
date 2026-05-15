# Allineamento librerie di cassaforte - Analisi di dettaglio

## Contesto e principi

*   Le **librerie di cassaforte** alimentano **solo il processo di promote/deploy**, non il runtime.
*   L’allineamento serve a evitare che **oggetti obsoleti** (LOAD, COPY, JCL, ecc.) vengano visti tramite **librerie concatenate**
*   Le operazioni si applicano in base a:
    *   **environment**
    *   **processor group**, **type**
    *   **path completo del sorgente**
    *   **build map**

## Architettura logica

### Componenti individuati

| Livello                           | Componente                           | Responsabilità                             |
| --------------------------------- | ------------------------------------ | ------------------------------------------ |
| Coordinamento                     | Pipeline Jenkins                     | Prep lista oggetti + orchestrazione script |
| Operazioni di delete / sfilamento | `PuliziaCassaforte.groovy` (groovyz) | Cancellazione / ripristino oggetti         |
| Pulizia post-build                | `PuliziaPostBuild.groovy` (task DBB) | Delete in ambiente precedente dopo build   |
| Dati                              | Build Map DBB                        | Risoluzione oggetti generati               |
| Configurazione                    | Regole sulle librerie                | Lista librerie su cui operare la rimozione |

## Script PuliziaCassaforte

Script invocato dalla pipeline Jenkins prima dell’invocazione del domando di build e implementa i casi relativi a:

- rimozione di un sorgente
- cambio del processor group e type di un sorgente (per la parte di cancellazione, il cambio effettivo avviene nella build)
- sfilamento

### Utilizzo

groovyz PuliziaCassaforte.groovy \<file-lista\> \<environment\>

* file-lista: \<AZIONE\>,\<PATH_COMPLETO\>, una per riga dove AZIONE:
    *   `C` = cancellazione
    *   `S` = sfilamento
* environment: ATI, ATO, SAD, PA

**Funzioni**
Usa le seguenti funzioni
* DELETE_CASSAFORTE
* SFILAMENTO


## Script PuliziaPostBuild

Script invocato da un task di tipo `task` alla fine di una build sugli ambienti SAD e PR e implementa il caso relativo a:

- pulizia ambiente precedente

### Utilizzo

* step con type:task
* usa `MEMBER`, nome del membro, `FILE_EXT` estensione del file, `CLI_BUILDENV` (valori ottenibili accedendo al config negli script groovy lanciati da dbb)

**Funzioni**
Usa le seguenti funzioni
* Recupero ambiente precedente
* DELETE_CASSAFORTE

## Algoritmi elementari (specializzati)

### Algoritmo: Cancellazione base (`DELETE_CASSAFORTE`)

**Scopo**  
Rimuovere un oggetto dalla libreria cassaforte di un dato ambiente.

**Input**

*   path completo dell’oggetto da cancellare
*   environment, valori ATI,ATO,TEST,PROD (esplicito o ricavato dal repository name?)

**Passi**
La cancellazione prevede due opzioni a seconda del tipo di oggetto da cancellare:

- Oggetti standard, cancellazione tramite scansione lista librerie
- Oggetti speciali, cancellazione tramite interrogazione build-map

La cancellazione è basata sulle regole di cancellazione. La regola è composta da tre campi:

- pattern per il match sul tipo dell’oggetto, con “%” match su singolo carattere, “*” match su 0 o più caratteri
- libreria su cui applicare la regola
- flag che indica l’utilizzo della build map per la risoluzione degli oggetti da cancellare

La cancellazione avviene con il seguente algoritmo:

1. Prendi il file relativo al sorgente da cancellare.
2. Ricava l’ambiente, il layer operativo e il system dal nome del repository, primo elemento del path del sorgente. Il system costituisce un parametro per il nome di alcune librerie.
3. L’ambiente e il layer operativo permettono di ricevere il valore di stage (variabile STAGE) da utilizzare e che costituisce un parametro per il nome di alcune librerie.
4. Individua la regola in base al match su tipo di file. Possono essere attivate più regole, elaborate in sequenza.
5. Nel caso di flag NO, viene rimosso, se esistente, il membro con il medesimo nome del sorgente (senza tipo) dalla libreria indicata dalla regola (❓ la libreria nella build map e nella regola devono coincidere?).
6. Nel caso di flag BUILD MAP, viene interrogata la build map per l’ambiente indicato nei parametri. La build map fornisce l’elenco degli oggetti candidati alla cancellazione. Se esistenti, vengono rimossi gli oggetti presenti nella build map che abbiano il medesimo nome e libreria. La libreria nella build map e nella regola devono coincidere.
7. In entrambi i casi, se l’oggetto da cancellare non viene trovato non si genera errore.

Esempio di regole:
```
%CPYCOB*;LTM00.D9P${C1STAGE}.PE000.LING.COB@@@@@.@@.COPY;NO
%CPYD2  ;LTM00.D9P${C1STAGE.PE000.LING.COB@@@@@.@@.COPY;NO
SZFSSWG ;LTM00.D9P${C1STAGE.PE000.LING.MAP@@@@@.@@.COPY;BUILD MAP
SZFSSWG ;LTM00.D9P${C1STAGE.PE000.@@@@.@@@@@@@@.@@.ZARA;NO
%CB2%R  ;LTM00.D9P${C1STAGE.PE000.@@@@.@@@@@@@@.@@.NCAL;NO
%CB2%R  ;LTM00.D9P${C1STAGE.PE000.MAIN.@@@@@@@@.@@.NCAL;NO
%CB2%   ;LTM00.D9P${C1STAGE.PE000.SYST.${C1SYSTEM}@@@@@@@.BT.LOAD;NO
```

Per `SZFSSWG` sono attuabili due regole, una che include l’utilizzo della BUILD MAP per ricevere il nome degli oggetti da cancellare nella libreria indicata, e una in cui la rimozione è svolta utilizzando il nome stesso del file.

### Algoritmo: Cancellazione con librerie concatenate (`DELETE_PREV_ENV_AFTER_BUILD`)

**Scopo**  
Evitare che oggetti di ambienti precedenti sovrascrivano quelli appena compilati.

**Dove**
*   Solo in ambienti **successivi ad ATO** (SAD, PR)
*   ATO e ATI non prevedono pulizia all'indietro
*   La catena è [ATI ->] ATO -> SAD -> PA per cui
    *   SAD -> ATO
    *   PR -> SAD
    *   EM non ha predecessori e non genera cancellazioni

**Passi**

1.  Language sul file corrente completato con successo
2.  Individuare l’**ambiente predecessore** e determinare il build group corrispondente
3.  Cancellazione base (DELETE_CASSAFORTE) dell’elemento dall’ambiente predecessore

**Mapping predecessore**
La cancellazione del predecessore avviene solo per SAD e PA. Questa la tabella degli ambienti:

| Corrente   | Precedente |
| ---------- | ---------- |
|    SAD     |    ATO     |
|    PA      |    SAD     |

### Algoritmo: Sfilamento (`SFILAMENTO`)

**Scopo**  
Allineare le librerie quando un sorgente viene “ritirato” da un ambiente.

**Dove**
*   Solo in ambienti SAD, PA (❓ o in ATO, SAD)

**Passi**
1. Cancellazione base (DELETE_CASSAFORTE) dell’elemento
2. Se il type è **SJCL\*** (JCL):
    -  Individuare gli **ambienti superiori**
    -  Cercare la **prima occorrenza disponibile**
    -  Copiare l’oggetto da quell’ambiente nella libreria TOCOLB

**Nota**

*   Lo sfilamento segue la cancellazione ed effettua un **ripristino condizionale** in base al tipo.
*   Nessun ripristino per gli ambienti generati
*   La libreria TOCOLB è ottenuta dalla libreria cassaforte utilizzata per la cancellazione tramite la modifica del quarto componente da “@@@@“ a “TO@@“ e del quinto componente da "@@@@@@@@" a “COLB@@@@“.

### Algoritmo composto: Cambio Processor Group / Type

**Passi**

1.  Cancellazione base (DELETE_CASSAFORTE) sul vecchio path con flag C
2.  Build del nuovo path

## Coordinatore ad alto livello (pipeline)

Schema operativo:

1. Jenkins prepara file-lista con path e flag C, S
2. Jenkins → groovyz PuliziaCassaforte.groovy \<lista\> \<environment\>
   - Esegue C e S
3. Jenkins → dbb build --environment \<env\>
   - Compile
   - Deploy in cassaforte
4. DBB → PuliziaAmbienti.groovy
   - Cancella oggetti in env predecessore

## Note

### Ambienti

*   Elenco
    *	ATI
    *   ATO
    *   SAD (system test)
    *   PA (produzione)
    *   EM (emergenza)

*   Catena Ambienti:
    *  [ATI] —> ATO -> SAD -> PA
    *  EM in mappa a PR

| Ambiente | `DELETE_CASSAFORTE` | `DELETE_PREV_ENV_AFTER_BUILD` | `SFILAMENTO` |
| -------- | :-----------------: | :---------------------------: | :----------: |
| ATI      |                     |                               |              |
| ATO      | ✓                   |                               |              |
| SAD      | ✓                   | ✓ (predecessore: ATO)         | ✓            |
| PA       | ✓                   | ✓ (predecessore: SAD)         |              |
| EM       |                     |                               |              |


### Formato delle regole di cancellazione

*   Formato dei dati
    * file in build-data
    * \<pattern\>;\<libreria parametrica\>;\<flag build map\>

Esempio:
```csv
%CPYD2  ;LTM00.D9P${C1STAGE.PE000.LING.COB@@@@@.@@.COPY;NO
SZFSSWG ;LTM00.D9P${C1STAGE.PE000.LING.MAP@@@@@.@@.COPY;BUILD MAP
SZFSSWG ;LTM00.D9P${C1STAGE.PE000.@@@@.@@@@@@@@.@@.ZARA;NO
```

### Tipologie

*   ❓  Oltre a JCL (estensione SJCL*) quali altri type richiedono **ripristino** nello sfilamento?
    *   Caso S su file non SJCL*, ignorato o errore


