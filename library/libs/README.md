# libs/

IBM JZOS and DBB jars required to compile `src/zos/groovy/ZosFileOpsUSS.groovy`
into `build/libs/pulizia-cassaforte-zos.jar` for USS deployment.

These jars are NOT on Maven Central. Obtain from your z/OS installation or DBB toolkit:
- `jzos-2.x.x.jar` — from z/OS Java SDK (`/usr/lpp/java/...`)
- `dbb-zappbuild-*.jar` — from DBB toolkit

Place jars here, then run: `./gradlew zosJar`

Output: `build/libs/pulizia-cassaforte-zos.jar` — deploy to `${DBB_BUILD}/groovy/pulizia-cassaforte/lib/`
alongside `pulizia-cassaforte.jar`.

The main build (`./gradlew build`) and tests do NOT require these jars.
