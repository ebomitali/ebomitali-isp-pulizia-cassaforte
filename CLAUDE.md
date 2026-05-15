# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project purpose

Design and implementation of two IBM z/OS Groovy scripts that manage **cassaforte libraries** — staging libraries used by the promote/deploy process (not the runtime) in the Intesa San Paolo ISP build pipeline.

Architecture documents in `docs/`. Implementation in `scripts/`.

## Scripts

### `PuliziaCassaforte.groovy` (standalone, `groovyz`)
Called by Jenkins **before** DBB build. Handles three scenarios via an action list file:
- `C` — delete member from cassaforte library for the given environment
- `S` — delete + conditional restore from upstream environment (JCL types only: `SJCL*`)

```bash
groovyz -cp lib:tasks PuliziaCassaforte.groovy <file-lista> <environment> <build-group>
```

Input file format: one line per object, `<action>;<full-source-path>

### `PuliziaPostBuild.groovy` (DBB task, `type: task`)
Called by DBB **during** build, after successful compile step. Handles one scenario:
- Deletes member from the **predecessor environment's** cassaforte library to prevent stale objects in concatenated libraries.
- Runs only in ST and PR (not ATI/ATO).

## Architecture

See `docs/groovy-zos-file-ops-architecture.md` for the full design. Summary:

```
CassaforteDeleteLogic  ←  ZosFileOps (trait)
                               ├── LocalFileOps     (local dev, java.nio)
                               └── ZosFileOpsUSS    (mainframe, ZFile/BPXWDYN)
```

- Business logic lives in `CassaforteDeleteLogic` — zero IBM/DBB imports.
- DBB wrappers (`@BaseScript TaskScript`) are thin adapters that read `TaskVariables`/`BuildContext` and call the logic.
- Local dev: `groovy -cp lib:tasks run_local.groovy`
- USS (non-DBB): `groovyz -cp lib:tasks run_uss.groovy`

## Environment chain

```
ATI → ATO → ST → PR
EM  (no predecessor, cancellation only)
```

`DELETE_PREV_ENV_AFTER_BUILD` applies only to ST and PR.  
`SFILAMENTO` applies only to ST (SAD in some docs).

## Deletion rules format

Rules file (CSV): `<type-pattern>;<parametric-library>;<flag>`

- Pattern: `%` matches one char, `*` matches zero or more.
- Flag `NO` — delete member by source name.
- Flag `BUILD MAP` — query DBB build map to resolve generated object names.

Library names use `${C1STAGE}` and `${C1SYSTEM}` as substitution parameters.

## Tooling

### Convert markdown → docx

```bash
./generate_doc.sh docs/some-file.md
# uses reference.docx by default; override with --ref-doc custom.docx
```

Requires `pandoc` installed (`brew install pandoc`).

## Key implementation notes

- Groovy files on USS must be tagged `IBM-1047`: `chtag -tc IBM-1047 file.groovy`
- DBB wrapper must return `Integer` — any other return type causes `BGZZB0043W` with RC 0.
- Use `groovyz` (not `groovy`) on USS to auto-load the DBB classpath.
- `/tmp/zos-sim/` simulates z/OS dataset structure locally: each subdirectory is a dataset, each file is a PDS member.
- `ZosFileOpsUSS` must not be loaded by the local JVM even if on the classpath — instantiate it only in the USS entry point.
