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

The script reads `PuliziaCassaforte.properties` from the **current working directory**, then loads and delegates to `FullPuliziaCassaforte.groovy`.

The environment variable `DBB_CONF` must be set before invocation.

## Configuration

Running the script requires a `PuliziaCassaforte.properties` file in the current directory. The shell scripts in this directory are examples that write that file before invoking `groovyz`.

| Property | Required | Description |
|---|---|---|
| `fileOpsType` | no (default: `zos`) | `local` for USS filesystem ops, `zos` for real z/OS PDS ops via ZFile |
| `buildMapClientType` | no (default: `db2`) | `json` to read a local JSON fixture, `db2` to query the live DBB metadata store |
| `rulesPath` | yes | Path to deletion rules CSV file |
| `stageMapPath` | yes | Path to stage-to-suffix mapping CSV file |
| `uxBasedir` | no | USS base directory for PDS path resolution |
| `buildMapPath` | when `buildMapClientType=json` | Path to pre-captured JSON build map file |
| `userId` | when `buildMapClientType=db2` | DB2 user ID for metadata store connection |
| `pwFilePath` | when `buildMapClientType=db2` | Path to DB2 password file |
| `db2ConfigPath` | when `buildMapClientType=db2` | Path to `db2Connection.conf` |

## Shell invocation examples

### `runpct2z.sh` — z/OS PDS ops, JSON build map

Allocates a temporary PDS, writes `PuliziaCassaforte.properties` with `fileOpsType=zos` and `buildMapClientType=json`, builds the list file, and runs `RunPuliziaCassaforte.groovy`. Verifies that only members matching a deletion rule are removed.

```sh
./runpct2z.sh
```

### `runpct4z.sh` — z/OS PDS ops, DB2 build map

Same flow but with `buildMapClientType=db2`. Requires `DBB_CONF` set and DB2 credentials configured.

```sh
./runpct4z.sh
```

## Core components

| File | Role |
|---|---|
| `RunPuliziaCassaforte.groovy` | CLI frontend — parses args, loads config, delegates to `FullPuliziaCassaforte.groovy` |
| `FullPuliziaCassaforte.groovy` | Fat source — contains all deletion logic, build map clients, rule matching, and `PuliziaCassaforteImpl` |
| `resources/rulest2.csv` | Sample deletion rules for `runpct2z.sh` (JSON build map scenario) |
| `resources/rulest4.csv` | Sample deletion rules for `runpct4z.sh` (DB2 build map scenario) |
| `resources/stagemap.csv` | Maps `buildGroup\|environment` pairs to PDS suffix tokens |

## Deletion rules CSV format

```
<pattern>;<hlq-template>;<useBuildMap>
```

- `<pattern>` — glob or prefix matched against the source file's directory/type segment (e.g. `SJCLINP`, `SJCLC*`)
- `<hlq-template>` — PDS HLQ template, with `@@@@` replaced by the stage suffix looked up from `stagemap.csv`
- `<useBuildMap>` — `YES` to resolve the member name via the build map; `NO` to use the source name directly
