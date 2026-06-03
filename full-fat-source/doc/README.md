# PuliziaCassaforte

Deletes members from IBM z/OS PDS datasets (cassaforte) based on configurable rules and a DBB build map. Given a list of source files and a target environment, it resolves which PDS members to delete by matching deletion rules and, optionally, looking up generated object names in the DBB build map.

## Entry point

`RunPuliziaCassaforte.groovy` is the frontend script, invoked via `groovyz`:

```sh
groovyz RunPuliziaCassaforte.groovy <list-file> <environment> <build-group>
```

| Argument | Description |
|---|---|
| `<list-file>` | Path to a CSV file with lines in `<action>,<source-path>` format (e.g. `C,ATO/yo_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YO8AMADD.SJCLINP`) |
| `<environment>` | Target environment string (e.g. `ATO`, `ST`, `PR`) |
| `<build-group>` | DBB build group name (e.g. `ATO`) |

The script reads `PuliziaCassaforte.properties` from the **current working directory**, then loads and use to `FullPuliziaCassaforte.groovy`.

The environment variable `DBB_CONF` must be set before invocation.

## Configuration

Running the script requires a `PuliziaCassaforte.properties` file in the current directory. The shell scripts in this directory are examples that write that file before invoking `groovyz`.

| Property | Description |
|---|---|
| `userId` | DB2 user ID for metadata store connection |
| `pwFilePath` | Path to DB2 password file |
| `db2ConfigPath` | Path to `db2Connection.conf` |
| `rules.csv` | Path to delete rules |
| `stagemap.csv` | Path to mapping from layer operativo/environment to stage |


## Shell invocation examples

### `runpct4z.sh`

Allocates a temporary PDS, writes `PuliziaCassaforte.properties`, builds the list file, and runs `RunPuliziaCassaforte.groovy`. 
Verifies that only members matching a deletion rule are removed.

```sh
./runpct4z.sh
```

## Core components

| File | Role |
|---|---|
| `RunPuliziaCassaforte.groovy` | CLI frontend — parses args, loads config, delegates to `FullPuliziaCassaforte.groovy` |
| `FullPuliziaCassaforte.groovy` | Fat source — contains all deletion logic, build map clients, rule matching, and `PuliziaCassaforteImpl` |
| `resources/rulest4.csv` | Sample deletion rules for `runpct4z.sh` (DB2 build map scenario) |
| `resources/stagemap.csv` | Maps `buildGroup\|environment` pairs to PDS suffix tokens |

