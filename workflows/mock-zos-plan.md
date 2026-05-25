# Plan: Add --mock-zos flag

## Context

`PuliziaCassaforteImpl` has two execution paths:
- **DB2 path** (lines 24-34): connects to live DBB metadata store, hardcodes `ZosFileOpsFactory.createOnZos()` — requires USS environment
- **bmFile path** (lines 36-50): reads JSON build map, already uses `ZosFileOpsFactory.mockZos()` → `LocalFileOps`

The flag `--mock-zos true` lets developers run the DB2 path locally (or in CI) without a real z/OS environment, forcing `LocalFileOps` instead of `ZosFileOpsUSS`.

## Changes

### 1. `library/src/main/groovy/PuliziaCassaforteImpl.groovy`

Add a `boolean mockZos = false` field after `stageMapPath`:

```groovy
boolean mockZos = false
```

Modify line 33 in the DB2 run method — replace the hardcoded `ZosFileOpsFactory.createOnZos()`:

```groovy
// before
execute(listFile, environment, buildGroup, buildMap, ZosFileOpsFactory.createOnZos(), hlq)

// after
def ops = mockZos ? ZosFileOpsFactory.mockZos() : ZosFileOpsFactory.createOnZos()
execute(listFile, environment, buildGroup, buildMap, ops, hlq)
```

No new overloads needed. The `mockZos` field is set by the caller before invoking `run`.

### 2. `front-end/scripts/groovy/PuliziaCassaforte.groovy`

Add `boolean mockZos = false` variable in the CLI section alongside the other options.

Add parsing block in the `while` loop (same style as existing flags):

```groovy
} else if (args[i] == '--mock-zos') {
    if (i + 1 >= args.size()) { System.err.println "ERROR: --mock-zos requires an argument (true|false)"; System.exit(1) }
    mockZos = args[++i].toBoolean()
}
```

Update usage string to include `[--mock-zos true|false]`.

When constructing the impl, set the field before calling `run`:

```groovy
def impl = new PuliziaCassaforteImpl()
impl.mockZos = mockZos
errors = impl.run(...)   // both the DB2 branch and the bmFile branch
```

## Files modified

- `library/src/main/groovy/PuliziaCassaforteImpl.groovy`
- `front-end/scripts/groovy/PuliziaCassaforte.groovy`

## Verification

```bash
# 1. Unit tests still pass (no IBM deps)
./gradlew test

# 2. Smoke: DB2 path with --mock-zos true routes to LocalFileOps
#    (fails fast at buildmap lookup, but confirms no ClassNotFoundException for ZosFileOpsUSS)
groovy -cp build/libs/pulizia-cassaforte.jar scripts/PuliziaCassaforte.groovy \
  --mock-zos true --dbid fakeuser --dbpf /dev/null \
  build-data/lista.txt ST GRP1
```
