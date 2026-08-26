# pulizia-temporanei

Groovy module for managing temporanei (temporary) z/OS datasets. Deletes stale objects to keep library concatenations clean.

## Overview

**pulizia-temporanei** deletes temporanei datasets based on DSN patterns and deletion rules. Runs:
- **During build** (via DBB task) — after compile, cleans predecessor environment's library
- **Pre-build** (USS script) — via action list file, handles deletion scenarios

## Testing

### Unit Tests (local, no IBM deps)

```bash
./gradlew :pulizia-temporanei:test
```

Spock specs in `src/test/groovy/` use `MacosDatasetService` to simulate z/OS directory structure in `/tmp/zos-sim/`.

### Integration Tests (subprocess)

```bash
./gradlew :pulizia-temporanei:integrationTest
```

Tests mimic **production Jenkins call**: build generates fat source, deploys to working directory, launches via shell script subprocess.

**Key test scenarios:**
- Wildcard `*` matches zero or more chars
- Wildcard `%` matches exactly one char
- Exact DSN pattern
- Missing config fails gracefully
- Missing DSN pattern fails with error

## Fat Source Architecture

**pulizia-temporanei** uses a **single merged Groovy file** (fat source) per ISP requirement:
- No external jar dependencies in production
- All classes compiled into one file
- Deployed once at build time, reused by DBB tasks

### Build Process

```bash
./gradlew :pulizia-temporanei:jar                 # Compile src/main/groovy → pulizia-temporanei.jar
./gradlew :pulizia-temporanei:generateFullFatSource  # Merge classes → build/fat-source/FullPuliziaTemporanei.groovy
```

### Fat Source Contents

Generated `build/fat-source/FullPuliziaTemporanei.groovy` merges:
- `DatasetService` (trait)
- `MacosDatasetService`, `UssDatasetService`, `JzosDatasetService` (implementations)
- `DeleteTemporaneiLogic`
- `PatternMatcher`
- `PuliziaTemporaneiConfig`
- `PuliziaTemporaneiImpl` (main entry)

**Excludes:** `fe/RunPuliziaTemporanei.groovy` (front-end entry point)

## Integration Test Flow

Integration tests execute the full production path:

1. **Build phase** — `./gradlew :pulizia-temporanei:integrationTest`
   - Generate fat source (`generateFullFatSource` task)
   - Build jar with implementation (`jar` task)
   - Set system properties for subprocess

2. **Deploy phase** (per test)
   - Copy fat source to `workDir/FullPuliziaTemporanei.groovy`
   - Copy front-end entry point to `workDir/RunPuliziaTemporanei.groovy`
   - Copy shell script to `workDir/run-pulizia-temporanei.sh`
   - Deploy SLF4J jars to `workDir/lib/`
   - Deploy logback config to `workDir/logback.xml`

3. **Test phase**
   - Launch subprocess via `run-pulizia-temporanei.sh`
   - Subprocess loads fat source via `GroovyClassLoader`
   - Subprocess uses SLF4J simple provider for logging (INFO level)

### Shell Script Entry Point

`src/integrationTest/resources/run-pulizia-temporanei.sh`:

```bash
#!/bin/bash
groovy -Dorg.slf4j.simpleLogger.defaultLogLevel=info -cp "lib/*" RunPuliziaTemporanei.groovy "$@"
```

- Runs in subprocess working directory (simulates USS environment)
- Uses glob `lib/*` to pick up SLF4J jars (version-agnostic)
- Passes arguments to RunPuliziaTemporanei.groovy

### Production Deployment

On USS (DBB_BUILD):

```
${DBB_BUILD}/groovy/pulizia-temporanei/
├── lib/
│   ├── pulizia-temporanei.jar              ← ./gradlew jar
│   ├── pulizia-temporanei-zos.jar          ← ./gradlew zosJar (requires IBM jars)
│   └── slf4j-*.jar                         ← copied by deployment
├── scripts/
│   ├── PuliziaCassaforte.groovy            ← USS entry point (groovyz)
│   ├── PuliziaPostBuild.groovy             ← DBB task entry point
│   └── run-pulizia-temporanei.sh           ← shell wrapper
└── fat-source/
    ├── FullPuliziaTemporanei.groovy        ← merged sources
    └── RunPuliziaTemporanei.groovy         ← front-end
```

Entry point loads fat source via `GroovyClassLoader`:

```groovy
def gcl = new GroovyClassLoader(this.class.classLoader)
gcl.parseClass(new File('FullPuliziaTemporanei.groovy'))
def impl = gcl.loadClass('com.intesasanpaolo.bes.pt.PuliziaTemporaneiImpl')
```

## Configuration

### PuliziaTemporanei.properties

```properties
fileOpsType=zos                        # macos (local test), uss (USS production)
uxBasedir=/path/to/datasets            # z/OS dataset root for testing
```

### Logging

SLF4J simple provider, INFO level by default:
- Console output to stdout/stderr
- Configurable via `-Dorg.slf4j.simpleLogger.defaultLogLevel=debug`

## ISP Requirements (Fat Source)

**Why fat source instead of managed dependencies?**
- ISP builds operate in isolated USS environment with limited external access
- All code + dependencies merged into single file at build time
- No runtime jar resolution — no network calls, no Maven Central hits
- Deployed once, reused across all DBB tasks
- Simpler license/compliance tracking (all code merged, visible in one file)

## Development

### Local Testing (No Fat Source)

Direct in-process testing (no subprocess):

```bash
./gradlew :pulizia-temporanei:test
```

Classes loaded directly; MacosDatasetService simulates z/OS filesystem.

### Integration Testing (Full Stack)

```bash
./gradlew :pulizia-temporanei:integrationTest --rerun-tasks
```

Generates fat source, deploys to temp directory, launches via subprocess. Tests the exact production flow without z/OS.

## References

- [ISP Build Architecture](../../docs/groovyz-quircks.md)
- [z/OS File Operations](../../docs/groovy-zos-file-ops-architecture.md)
