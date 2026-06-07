# Strategy D — Checkpoint tracker

| # | Checkpoint                                              | Status      |
|---|---------------------------------------------------------|-------------|
| 1 | `UssFileService.groovy`: class renamed, implements FileService | DONE   |
| 2 | `MacosFileService.groovy` created                       | DONE        |
| 3 | `ZosFileOpsFactory`: return FileService, load JzosFileService | DONE  |
| 4 | `SfilamentoLogic.ops` type → FileService                | DONE        |
| 5 | `PuliziaCassaforteImpl`: field + switch (macos/uss/zos) | DONE        |
| 6 | `ZosFileOps.groovy` deleted                             | DONE        |
| 7 | Tests: LocalFileOps → MacosFileService                  | DONE        |
| 8 | `./gradlew test` green                                  | DONE        |
| 9 | `./gradlew jar` green (no IBM deps)                     | DONE        |
| 10 | `grep ZosFileOps\|LocalFileOps library/src/` = zero hits | DONE      |
