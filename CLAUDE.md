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
# Local dev (no IBM deps — uses LocalFileOps via Spock tests):
./gradlew test

# Local smoke (Gradle-built jar, uses groovy not groovyz):
./gradlew jar
groovy -cp build/libs/pulizia-cassaforte.jar scripts/PuliziaCassaforte.groovy <file-lista> <environment> <build-group>

# On USS (jars deployed to ${DBB_BUILD}/groovy/pulizia-cassaforte/lib/):
groovyz -cp ${DBB_BUILD}/groovy/pulizia-cassaforte/lib/pulizia-cassaforte.jar:${DBB_BUILD}/groovy/pulizia-cassaforte/lib/pulizia-cassaforte-zos.jar PuliziaCassaforte.groovy <file-lista> <environment> <build-group>
```

Input file format: one line per object, `<action>;<full-source-path>

### `PuliziaPostBuild.groovy` (DBB task, `type: task`)
Called by DBB **during** build, after successful compile step. Handles one scenario:
- Deletes member from the **predecessor environment's** cassaforte library to prevent stale objects in concatenated libraries.
- Runs only in ST and PR (not ATI/ATO).

## Architecture

See `docs/groovy-zos-file-ops-architecture.md` for the full design. Summary:

```
src/main/groovy/          — compiled into pulizia-cassaforte.jar (no IBM deps)
  DeleteCassaforteLogic  ←  ZosFileOps (trait)
                                 ├── LocalFileOps     (local testing, java.nio)
                                 └── ZosFileOpsUSS    (on USS via pulizia-cassaforte-zos.jar)
src/zos/groovy/
  ZosFileOpsUSS            (USS-only; compiled with IBM jars → pulizia-cassaforte-zos.jar)

USS deployment: ${DBB_BUILD}/groovy/pulizia-cassaforte/lib/
  pulizia-cassaforte.jar       ← ./gradlew jar
  pulizia-cassaforte-zos.jar   ← ./gradlew zosJar (requires IBM jars in libs/)

scripts/
  PuliziaCassaforte.groovy  (USS entry point, uses groovyz)
  PuliziaPostBuild.groovy   (DBB task entry point)
```

- Business logic lives in `CassaforteDeleteLogic` — zero IBM/DBB imports.
- DBB wrappers (`@BaseScript TaskScript`) are thin adapters that read `TaskVariables`/`BuildContext` and call the logic.
- Local dev: `./gradlew test` (Spock specs in src/test/groovy/ use LocalFileOps — no IBM deps)
- USS entry: `groovyz -cp ${DBB_BUILD}/groovy/pulizia-cassaforte/lib/pulizia-cassaforte.jar:${DBB_BUILD}/groovy/pulizia-cassaforte/lib/pulizia-cassaforte-zos.jar PuliziaCassaforte.groovy`

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

```bash
# Run tests (local — no IBM deps required)
./gradlew test

# Build common jar (output: build/libs/pulizia-cassaforte.jar)
./gradlew jar

# Build USS-only jar (requires IBM jars in libs/)
./gradlew zosJar
# Output: build/libs/pulizia-cassaforte-zos.jar
# Deploy both jars to ${DBB_BUILD}/groovy/pulizia-cassaforte/lib/ on USS
```

## Key implementation notes

- Groovy files on USS must be tagged `IBM-1047`: `chtag -tc IBM-1047 file.groovy`
- DBB wrapper must return `Integer` — any other return type causes `BGZZB0043W` with RC 0.
- Use `groovyz` (not `groovy`) on USS to auto-load the DBB classpath.
- `/tmp/zos-sim/` simulates z/OS dataset structure locally: each subdirectory is a dataset, each file is a PDS member.
- `ZosFileOpsUSS` must not be loaded by the local JVM even if on the classpath — instantiate it only in the USS entry point.
