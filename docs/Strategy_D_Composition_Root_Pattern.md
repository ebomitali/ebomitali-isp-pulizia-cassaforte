# Strategy D + Composition-Root / Injected-Service Pattern

**One-line summary:** Make DBB Groovy helpers depend on *abstractions* instead of z/OS
classes, wire the real implementation only at the top of the task script, and Strategy D
(local off-host compilation) falls out for free — the same code compiles locally against
stubs and runs unchanged on USS.

---

## The core principle

These two ideas are the same idea seen from two angles:

- **Composition root + injected service** is *how* you write the code: a class never
  reaches for its dependencies, it receives them; the only place that constructs and wires
  them is the task-script body.
- **Strategy D** is *what that buys you*: because helpers depend on a trait/interface and
  never on `com.ibm.jzos.*` or the licensed DBB API directly, they compile off-host with a
  thin stub bundle and no IBM JARs.

The hinge that connects them: **inject abstractions, not concretions.** A helper that takes
a `FileOpsTrait` can be given a `ZosFileOpsUSS` on the host or a `LocalFileOps` off-host
without changing a line of the helper.

---

## Strategy D — local-first off-host compilation

Goal: compile and statically check Groovy on a workstation/CI agent **without** the licensed
IBM DBB JARs and without JZOS, then run the *identical* source on z/OS USS under `groovyz`.

### Two-layer service split

| Layer | Source set | Depends on | Always compiles locally? |
|-------|-----------|------------|--------------------------|
| `FileOpsTrait` (abstraction) | shared | nothing IBM | ✅ |
| `LocalFileOps` (impl) | `src/main/groovy` | pure Java/Groovy | ✅ |
| `ZosFileOpsUSS` (impl) | `src/zos/groovy` | `com.ibm.jzos.ZFile` | ❌ (needs IBM JARs) |

Helpers (Cassaforte scripts, resolvers) depend **only** on `FileOpsTrait`. They live with
the always-compilable layer.

### Stub bundle

A minimal set of compile-only stubs stands in for the DBB API so the off-host compiler is
satisfied. Under `src/stubs/groovy/`:

- `AbstractLoader`
- `TaskScript`
- `ScriptLoader`
- `TaskConstants`
- `BuildContext`
- `TaskVariables`

These are wired into the Gradle build with a **`compileOnly`** configuration so they never
leak onto the runtime classpath (on USS the real DBB JARs provide these). The stubs only
need to satisfy the *signatures* you actually call — keep them minimal.

### Deferred z/OS class loading

The factory must not statically reference `ZosFileOpsUSS` (that would drag JZOS into local
compilation). Resolve it by name at runtime, and fall back to `LocalFileOps` when the class
isn't present (i.e. off-host):

```groovy
class FileOpsFactory {
    // loadClass (not Class.forName) keeps CodeNarc's ClassForName rule quiet and
    // defers z/OS class resolution until runtime on USS.
    static FileOpsTrait create(context, log) {
        try {
            def cls = Thread.currentThread().contextClassLoader
                          .loadClass('isp.dbb.ops.ZosFileOpsUSS')
            return cls.getDeclaredConstructor(Object, Object).newInstance(context, log)
        } catch (ClassNotFoundException ignored) {
            return new LocalFileOps()   // off-host / local test path
        }
    }
}
```

---

## Composition root — the task-script body

Only the script body has the DBB binding (`context`, `log`, `config`), because that body is
compiled into `run()` of the `TaskScript` subclass. A class declared in the same file is a
**separate peer class** with no binding. Therefore the script body is the *only* legitimate
place to construct and wire dependencies — it is the composition root.

```groovy
@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript

// --- composition root: the only place with the binding ---
def fileOps = FileOpsFactory.create(context, log)        // Zos impl on USS, Local off-host
def cleaner = new CassaforteCleaner(
        context : context,
        log     : log,
        fileOps : fileOps)

cleaner.clean()
return 0
```

Rules of thumb for the root:

- Construct everything here; push dependencies **down** through constructors.
- Never let a deep helper reach back up to the binding — one injection point at the top.
- A tiny factory method in the body is enough when several helpers share dependencies; no
  DI container.

---

## Injected service — the helper

The helper is binding-agnostic. It receives `context`, `log`, and the `FileOpsTrait`; it has
no idea whether file ops hit real datasets or an in-memory fake.

```groovy
class CassaforteCleaner {
    def context              // BuildContext  (stub locally, real on USS)
    def log                  // org.slf4j.Logger from the binding
    FileOpsTrait fileOps     // injected abstraction

    void clean() {
        String hlq = context.getVariable('HLQ')
        String dsn = "${hlq}.CASSAFORTE.MEMBER"
        if (fileOps.exists(dsn)) {
            log.info("Deleting {}", dsn)
            fileOps.delete(dsn)
        }
    }
}
```

The two implementations behind `FileOpsTrait`:

```groovy
trait FileOpsTrait {
    abstract boolean exists(String dsn)
    abstract void    delete(String dsn)
}

// src/main — always compiles, used off-host and in tests
class LocalFileOps implements FileOpsTrait {
    Set<String> present = []
    boolean exists(String dsn) { dsn in present }
    void    delete(String dsn) { present.remove(dsn) }
}

// src/zos — compiled only with IBM JARs, used on USS
class ZosFileOpsUSS implements FileOpsTrait {
    ZosFileOpsUSS(context, log) { /* ... */ }
    boolean exists(String dsn) { com.ibm.jzos.ZFile.dsExists("//'${dsn}'") }
    void    delete(String dsn) { com.ibm.jzos.ZFile.remove("//'${dsn}'") }
}
```

---

## How it all fits

```
                 task-script body  (composition root, has the binding)
                          │  builds + injects
            ┌─────────────┼──────────────┐
            ▼                             ▼
     FileOpsFactory.create()       CassaforteCleaner(context, log, fileOps)
            │ loadClass(), fallback              │ depends only on FileOpsTrait
            ▼                                    ▼
   ZosFileOpsUSS (USS)  /  LocalFileOps (off-host)   — interchangeable
```

- **On USS:** factory loads `ZosFileOpsUSS`; real JZOS calls run.
- **Off-host / CI / unit test:** factory falls back to `LocalFileOps`; the same helper code
  compiles against stubs and runs against an in-memory fake.

---

## Guardrails / gotchas

- A class in a `.groovy` script file is a **peer class**, not an inner class of the script;
  it does **not** see the binding. `@BaseScript` only changes the parent of the auto-generated
  script class, never the peer class. Inject `context`/`log` explicitly or you get
  `MissingPropertyException`.
- `@Slf4j` injects its own `private static final org.slf4j.Logger log` (category = the class
  FQN). DBB's binding `log` is also an SLF4J logger on the same Simple Logger backend, so both
  write to the same place via the same `simplelogger.properties`. You can't repoint the
  `static final` field at the binding — either accept the shared backend, set
  `@Slf4j(category = '…')` to match the script's logger name, or drop `@Slf4j` and inject the
  binding instead.
- Keep stubs **`compileOnly`** so they never reach the runtime classpath on USS.
- Never statically reference `ZosFileOpsUSS` from the always-compilable layer — resolve it by
  name in the factory.
- No DI container. For a handful of DBB tasks, the manual composition root is the
  purpose-fit solution; a container is over-engineering here.
- If a helper has both `@Slf4j` (field `log`) and an injected binding, rename one
  (`@Slf4j(value = 'LOG')` or inject as `dbbLog`) to avoid the name clash.
