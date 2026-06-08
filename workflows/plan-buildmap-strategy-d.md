# BuildMapClient — Strategy D refactoring

## Goal

Rename `LocalBuildMapClient` → `JsonBuildMapClient`, `ZosBuildMapClient` → `Db2BuildMapClient`,
add `DbbBuildMapClient`. All use `(String buildGroupName, PuliziaCassaforteConfig cfg)`.
Strip IBM imports from `BuildMapClientFactory` (Strategy D). Lazy `BuildGroup` creation.

## Three implementations

| buildMapClientType | Class               | IBM deps | How BuildGroup acquired                        |
|--------------------|---------------------|----------|------------------------------------------------|
| `json`             | `JsonBuildMapClient` | no       | Reads JSON file from `cfg.buildMapPath`        |
| `db2`              | `Db2BuildMapClient`  | yes      | Lazy: connects to DB2 on first `getGeneratedObjects()` |
| `dbb`              | `DbbBuildMapClient`  | yes      | Lazy: pulls `BUILD_GROUP` from `BuildContext` injected by task |

## Steps

0. Create this file + `checkpoint-buildmap-strategy-d.md`
1. `JsonBuildMapClient.groovy` — rename from `LocalBuildMapClient`; constructor `(buildGroupName, cfg)`
2. `Db2BuildMapClient.groovy` — rename from `ZosBuildMapClient`; primary `(buildGroupName, cfg)` + injection `(BuildGroup)` for tests; static `create()`; lazy DB2
3. `DbbBuildMapClient.groovy` — new; `(buildGroupName, cfg)` + `(buildGroupName, BuildContext)` injection; static `create()`; pulls from context
4. `BuildMapClientFactory.groovy` — remove IBM imports; single `create(type, buildGroupName, cfg)` dispatching via reflection for db2/dbb
5. `PuliziaCassaforteImpl.groovy` — replace buildMapClient setup block with `BuildMapClientFactory.create(...)`
6. `BuildMapClient.groovy` — update javadoc
7. Tests — rename specs, update constructor calls

## Scope

`library/` only.
