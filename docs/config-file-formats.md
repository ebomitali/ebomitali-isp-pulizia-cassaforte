# Configuration File Formats on Linux/Unix

## Overview

There is no single universal standard for configuration files on Linux/Unix. The choice of format and extension depends on the ecosystem, the tools that will read the file, and the syntax requirements. This document compares the three most common options: `.properties`, `.cfg`, and `.conf`.

---

## Format Comparison

### `.properties`

The `.properties` format originates from the Java ecosystem and uses a simple `key=value` (or `key: value`) syntax.

```properties
# Comment
DB_HOST=myhost.example.com
DB_PORT=5432
APP_NAME=myapp
MAX_RETRIES=3
```

**Supported by:**
- **Java**: Native support via `java.util.Properties` — no external library needed
- **Groovy / JVM tools**: Inherited from Java; used natively in DBB, Gradle, Spring
- **Unix shell**: Sourceable with `. ./file.properties` or parseable with `grep`/`awk`
- **Python**: Readable with a minimal wrapper or `configparser` in no-section mode

**Strengths:**
- Unambiguous semantics: universally understood as `key=value`
- No sections — flat structure ideal for simple configuration
- Native Java/JVM support without any additional dependency
- Easy to `export` in shell: `export $(grep -v '^#' app.properties | xargs)`

**Limitations:**
- No support for sections/namespaces natively
- Multiline values require backslash continuation (`\`)
- Some parsers handle Unicode escapes (`\uXXXX`) in Java-specific ways

---

### `.cfg`

The `.cfg` format is most commonly associated with Python's `configparser` module and follows the INI-style syntax with sections.

```cfg
[database]
host = myhost.example.com
port = 5432

[application]
name = myapp
max_retries = 3
```

**Supported by:**
- **Python**: Native support via `configparser` — the standard library module
- **Unix shell**: Requires manual parsing; sections make `grep`/`awk` more complex
- **Java**: No native support — requires a third-party library (e.g., Apache Commons Configuration)

**Strengths:**
- Sections allow logical grouping of related parameters
- Well suited for complex configurations with multiple components
- Pythonic and familiar in Python-heavy projects

**Limitations:**
- Not natively supported by Java without additional dependencies
- Shell parsing is more cumbersome due to the `[section]` headers
- Ambiguous: `.cfg` is also used by many unrelated tools (e.g., network daemons, game engines)

---

### `.conf`

The `.conf` extension is the conventional choice for Unix/Linux system-level configuration files. The syntax varies widely by application.

```conf
# nginx-style
server {
    listen 80;
    server_name example.com;
}

# Or simple key=value (e.g., sysctl.conf)
net.ipv4.ip_forward = 1
```

**Supported by:**
- **Unix daemons and services**: nginx, rsyslog, sshd, systemd, etc.
- **Shell**: Sourceable if the syntax is simple `key=value`
- **Java / Python**: No standard parser — each tool defines its own format

**Strengths:**
- Idiomatic for system-level Unix configuration
- Recognized as a system file by conventions and packaging tools
- Flexible syntax — each service defines its own grammar

**Limitations:**
- No standard parsing library — every consumer must implement its own reader
- Format varies significantly between tools
- Not suitable as a shared format across Java, Python, and shell simultaneously

---

## Summary Table

| Feature                        | `.properties` | `.cfg`        | `.conf`         |
|-------------------------------|---------------|---------------|-----------------|
| Java native support            | ✅ Yes         | ❌ No          | ❌ No            |
| Python native support          | ⚠️ Partial    | ✅ Yes         | ❌ No            |
| Unix shell sourceable          | ✅ Yes         | ⚠️ Partial    | ⚠️ Depends      |
| Sections / namespaces          | ❌ No          | ✅ Yes         | ✅ Depends       |
| Standard parsing library       | Java only     | Python only   | None            |
| Interoperability across stacks | ✅ Best        | ⚠️ Limited    | ❌ Poor          |
| Typical use context            | JVM / DevOps  | Python apps   | System services |

---

## Recommendation

### Use `.properties` when:
- The file must be consumed by **Java, Groovy, or any JVM-based tool**
- The file is also read by **shell scripts** (easily sourceable)
- You want **maximum interoperability** across ecosystems
- The configuration is flat (no need for sections)

### Use `.cfg` when:
- The file is primarily consumed by **Python** via `configparser`
- You need **sections** to organize parameters by component
- Java consumers are absent or can use Apache Commons Configuration

### Use `.conf` when:
- The file is a **system-level configuration** for a Linux daemon or service
- The consuming tool has its own parser and the format is tool-specific
- Portability across programming languages is not a concern

---

## Cross-Ecosystem Compatibility Notes

### Shell sourcing of `.properties`

```bash
# Source all non-comment lines
set -a
. ./app.properties
set +a

# Or export selectively
export $(grep -v '^\s*#' app.properties | xargs)
```

> **Warning:** Avoid spaces around `=` and avoid quoting values — Java ignores surrounding quotes, but shell treats them as literal characters.

### Python reading `.properties` without sections

```python
import configparser

config = configparser.RawConfigParser()
# Add a dummy section header so configparser can parse flat key=value files
with open('app.properties') as f:
    content = '[DEFAULT]\n' + f.read()
config.read_string(content)

host = config['DEFAULT']['DB_HOST']
```

### Java reading `.properties`

```java
Properties props = new Properties();
try (FileInputStream fis = new FileInputStream("app.properties")) {
    props.load(fis);
}
String host = props.getProperty("DB_HOST");
```

---

## Conclusion

For a file shared across **Unix shell, Java, and Python**, `.properties` with flat `key=value` syntax is the most pragmatic choice. It has native support in Java, is trivially sourceable in shell, and requires only a minimal workaround in Python. The `.cfg` and `.conf` extensions are better reserved for Python-centric or system-level contexts respectively.
