# Gradle: Running One Subproject's Tests in Another

## Question
Can a Gradle subproject keep its `main` sources in its own `src/main` while taking
test code from another subproject's `src/test`? Goal: run the **same tests** from
`project(:A)` inside `project(:B)`. `java-test-fixtures` is not an option.

## Short Answer
Yes. Redirect B's `test` source set at A's test directory so the sources are
**recompiled against B's classpath** and executed as B's tests. This is the standard
"two implementations, one test suite" (contract test) pattern.

## Configuration

```groovy
// project B build.gradle
sourceSets {
    test {
        groovy.srcDirs     = [file("${project(':A').projectDir}/src/test/groovy")]
        resources.srcDirs  = [file("${project(':A').projectDir}/src/test/resources")]
    }
}

dependencies {
    // B must declare the SAME test deps A uses
    testImplementation 'org.spockframework:spock-core:2.3-groovy-4.0'
    // ...plus anything else A's tests need
}
```

`:B:test` now compiles A's test sources against B's own `main` output and runs them.

## Gotchas

1. **Test dependencies are not inherited.** Source-set redirection moves only the
   source files, not A's `testImplementation` deps. B needs its own copy — a shared
   convention plugin or a root `subprojects {}` block keeps this DRY.

2. **Tests must compile against B, not A.** If A's tests `import` A's concrete classes,
   they only compile in B if B exposes the same types. To make this meaningful, the
   tests should reference a shared API/interface and get the object-under-test through
   an abstract factory method that each project overrides:

   ```groovy
   abstract class CassaforteContractSpec extends Specification {
       abstract Cassaforte createSubject()   // A and B each provide their impl
       // shared tests use createSubject()
   }
   ```

   If the tests hard-code A's implementation, redirecting into B just re-tests A's
   classes from B's build — defeating the purpose.

3. **Project isolation / configuration cache.** Reaching into
   `project(':A').projectDir` at configuration time is flagged by Gradle's
   project-isolation and configuration-cache features.

## Cleaner Alternative
Move the shared test sources to a neutral top-level path (e.g. `shared-tests/`) and
have **both** A and B point their `test` source set there, so neither project reaches
into the other's directory.

## Approaches Considered (and why ruled out here)
- **`java-test-fixtures`** — idiomatic way to share test/helper code, but excluded by
  constraint.
- **Test jar via custom configuration** (`from sourceSets.test.output` + `artifacts {}`,
  consumed with `project(path: ':B', configuration: 'testArtifacts')`) — ships compiled
  classes, not recompiled sources; superseded by test fixtures.
