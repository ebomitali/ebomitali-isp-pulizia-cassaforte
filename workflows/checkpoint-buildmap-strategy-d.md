# BuildMapClient Strategy D — Checkpoint tracker

| # | Checkpoint                                                          | Status      |
|---|---------------------------------------------------------------------|-------------|
| 1 | `JsonBuildMapClient.groovy` created; `LocalBuildMapClient.groovy` deleted | TODO   |
| 2 | `Db2BuildMapClient.groovy` created; `ZosBuildMapClient.groovy` deleted    | TODO   |
| 3 | `DbbBuildMapClient.groovy` created                                  | TODO        |
| 4 | `BuildMapClientFactory`: IBM imports removed; `create()` method     | TODO        |
| 5 | `PuliziaCassaforteImpl`: buildMapClient block replaced              | TODO        |
| 6 | `BuildMapClient.groovy` javadoc updated                             | TODO        |
| 7 | `JsonBuildMapClientSpec.groovy` created; old spec deleted           | TODO        |
| 8 | `Db2BuildMapClientSpec.groovy` created; old spec deleted            | TODO        |
| 9 | `BuildMapClientFactorySpec` updated                                 | TODO        |
| 10 | Other specs (Delete/Sfilamento/PrevEnv) updated                   | TODO        |
| 11 | `./gradlew :library:test` green                                     | TODO        |
| 12 | `grep LocalBuildMapClient\|ZosBuildMapClient library/src/` = 0 hits | TODO       |
| 13 | `grep 'import com.ibm' BuildMapClientFactory.groovy` = 0 hits       | TODO       |
