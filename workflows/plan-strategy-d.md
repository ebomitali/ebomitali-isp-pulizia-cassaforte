# Strategy D — Depend on FileService abstraction

## Goal

Consolidate `library/` on `FileService` abstraction; retire `ZosFileOps` duplicate trait;
introduce `MacosFileService` for local dev/tests; wire three `fileOpsType` values in
`PuliziaCassaforteImpl`: `macos`, `uss`, `zos`.

## Three implementations

| fileOpsType | Class              | Where runs       | How                          |
|-------------|-------------------|------------------|------------------------------|
| `macos`     | `MacosFileService` | macOS dev + tests | java.nio, maps //DS(MBR) to dirs |
| `uss`       | `UssFileService`   | USS Unix FS      | java.nio, same mapping       |
| `zos`       | `JzosFileService`  | USS + IBM JZOS   | IBM ZFile API, real PDS      |

## Steps

1. `UssFileService.groovy` — rename class `LocalFileOps` → `UssFileService`; `implements ZosFileOps` → `implements FileService`
2. Create `MacosFileService.groovy` — same java.nio logic, class `MacosFileService implements FileService`; used by unit tests
3. `ZosFileOpsFactory.groovy` — return type `FileService`; reflection loads `JzosFileService`; `mockZos()` returns `new MacosFileService()`
4. `SfilamentoLogic.groovy` — `ZosFileOps ops` → `FileService ops`
5. `PuliziaCassaforteImpl.groovy` — `ZosFileOps fileOps` → `FileService fileOps`; rename `'local'` → `'macos'`; add `'uss'`
6. Delete `ZosFileOps.groovy`
7. Tests — `LocalFileOps` → `MacosFileService`

## Scope

`library/` only. Do not touch `full-fat-source/`, `scripts/`, `src/zos/`.
