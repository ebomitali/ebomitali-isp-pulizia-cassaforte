# Enabling Logging in a `groovyz` Script — `-D` and `-cp` Mechanics (DBB 3.0.3)

Summary of why SLF4J logging was silent when running a Groovy script via the DBB
`groovyz` shell script, and how the `-Dorg.slf4j.simpleLogger` flag and `-cp`
interact to fix it.

## Goal

Get `debug`-level SLF4J output (from an `@Slf4j` logger inside a class invoked by
the script) to appear on **stdout** when launching with `groovyz`.

## Root cause — it's the `groovyz` shell script, not the Groovy code

The DBB `groovyz` script parses its arguments before launching the JVM. Two pieces
of its logic drive the whole behavior:

1. `-Dorg.slf4j.simpleLogger` is matched as an **exact token** (not a prefix). When
   present, it only sets an internal flag (`SIMPLE_LOGGER_ON=true`) and later adds
   `$DBB_CONF/logging` to the classpath so a `simplelogger.properties` is read.
   It is **never forwarded to the JVM as a system property**.

2. When that exact flag is **absent**, the script force-appends
   `-Dorg.slf4j.simpleLogger.defaultLogLevel=off` to `JAVA_OPTS`. This is a real
   **system property**, and system properties **override** any
   `simplelogger.properties` file. Result: logging is hard-disabled.

Any other `-D...` argument falls through to a `-D*` catch-all and is added to
`JAVA_OPTS` — but the forced `=off` is appended *after* it, and for a repeated
`-D` key the **last value wins**.

## Why the two attempts failed

| Command passed | What the script did | Effective level | Result |
|---|---|---|---|
| `-Dorg.slf4j.simpleLogger.defaultLogLevel=debug` | Did **not** match the exact flag → went to `JAVA_OPTS` via `-D*`; flag stayed off → `=off` appended **after** it | `off` (last wins) | Silent |
| `-Dorg.slf4j.simpleLogger` | Matched exact flag → added `$DBB_CONF/logging` to classpath; no system property set | `warn` (from DBB sample file) | No `debug`/`info` |

This matched the diagnostics exactly — both
`System.getProperty("org.slf4j.simpleLogger")` and
`System.getProperty("org.slf4j.simpleLogger.defaultLogLevel")` printed `null`,
because with the bare flag the level comes from the **file on the classpath**, not
from a system property. `println` always showed because it is unaffected by log
level.

## The fix

Pass the bare flag **and** put a `debug`-level `simplelogger.properties` first on
the classpath via `-cp`:

```
groovyz -Dorg.slf4j.simpleLogger -cp . RunPuliziaCassaforte.groovy cassaforte-file-list.txt ST ST-JOBZ
```

`./simplelogger.properties`:

```properties
org.slf4j.simpleLogger.defaultLogLevel=debug
org.slf4j.simpleLogger.logFile=System.out
org.slf4j.simpleLogger.showLogName=true
```

### Why both arguments are required together

- **`-Dorg.slf4j.simpleLogger` (mandatory).** Without it the script injects
  `defaultLogLevel=off` as a system property, which overrides your file. The flag
  suppresses that injection (and adds `$DBB_CONF/logging` to the classpath).
- **`-cp .` (explicit).** The script builds and `export`s an explicit `CLASSPATH`,
  so the current directory is **not** implied by the JVM. You must add it yourself.
  In the assembled classpath, `-cp` entries come **before** the appended
  `$DBB_CONF/logging`:

  ```
  $DBB_HOME/groovy/lib/*  :  .  :  $DBB_HOME/lib/*  :  …  :  $DBB_CONF/logging
  ```

  SLF4J SimpleLogger loads the **first** `simplelogger.properties` it finds, so your
  `./simplelogger.properties` (`debug`) wins over the WARN-level one in
  `$DBB_CONF/logging`. The DBB lib globs do not ship a `simplelogger.properties`, so
  they don't interfere.

## Key rules / gotchas

- **stderr is the SimpleLogger default.** `@Slf4j` only injects the logger; the
  output stream is decided by `logFile`. Set `logFile=System.out` for stdout.
  SimpleLogger writes to a **single** target — it can't tee to both stdout and a
  file; that needs Logback/Log4j2.
- **`-Dorg.slf4j.simpleLogger.defaultLogLevel=debug` on the `groovyz` command line
  does not work** — it is overridden by the forced `=off`. Drive the level from the
  classpath file instead.
- **`getProperty(...defaultLogLevel)` returning `null` is expected** when using the
  file route — the level isn't a system property in that path. Confirm logging by
  seeing the actual log lines, not the property.
- **`@Slf4j` logger name = class name** (package-qualified if a `package` is
  declared). `loadScript(...)` and which classloader instantiated the class are
  irrelevant — the logger and its level resolution are JVM-global. With
  `defaultLogLevel=debug` everything prints; to isolate one class use
  `org.slf4j.simpleLogger.log.<ClassName>=debug` alongside a higher default.
- **Daemon note:** this `groovyz` only routes to the DBB daemon when
  `-DBB_DAEMON_HOST`/`-DBB_DAEMON_PORT` (shared) or `-DBB_PERSONAL_DAEMON` are
  supplied. With none of those, a fresh local JVM runs and the above applies. If the
  daemon *were* used, command-line Java options and classpath would be ignored, and
  logging would have to be configured in `process_definitions.xml`.

## Quick reference

```
# Works: debug to stdout
groovyz -Dorg.slf4j.simpleLogger -cp . <script>.groovy <args...>
#   + ./simplelogger.properties with defaultLogLevel=debug, logFile=System.out

# Does NOT work: overridden by forced =off
groovyz -Dorg.slf4j.simpleLogger.defaultLogLevel=debug <script>.groovy <args...>

# Logging off entirely (default when flag absent)
groovyz <script>.groovy <args...>
```
