# Multi-Language Configuration Architecture for DBB 3.0.3

## Canonical Configuration Source

**`config/application-config.yaml`**

```
Version: 1.0
Application: CassaforteManager
Build: { hlq: USER.BUILD, workspace: /u/<user-home> }
Cassaforte: { deleteMode: physical, retentionDays: 30 }
Stages: [...list of stage definitions...]
Features: { enableListingUpload: true, ... }
```

This file fans out to five consumers:

| Consumer | Config Reader |
|---|---|
| Groovy Tasks (DBB) | `ConfigurationLoader.groovy` |
| Shell Scripts | `load-config.sh` (yq/grep/awk) → export env vars |
| Jenkins Pipeline | `readYaml` (built-in) |
| Groovy (Groovyz) | `ConfigLoader` |
| Standalone Groovy Scripts | `ConfigurationLoader` (same class) |

---

## Configuration Hierarchy & Override Pattern

```
config/application-config.yaml    ← Base configuration (git committed)
│
├─ version: 1.0
├─ application: { name: CassaforteManager, ... }
├─ build: { hlq: USER.BUILD, logLevel: INFO }
├─ cassaforte:
│  ├─ deleteMode: physical
│  └─ retentionDays: 30
├─ stages:
│  ├─ { id: ATI1, name: Development, ... }
│  ├─ { id: ATI2, name: Pre-Integration, ... }
│  ├─ { id: ATO,  name: Test, ... }
│  ├─ { id: ST,   name: Stage, ... }
│  └─ { id: PR,   name: Production, ... }
└─ features: { enableListingUpload: true, ... }
```

At runtime, stage overrides are loaded and deep-merged:

```
config/stages/ATI1.yaml           ← Stage-specific overrides (git committed)
│
├─ cassaforte:
│  ├─ deleteMode: logical         ← Override: use logical delete in dev
│  └─ retentionDays: 15           ← Override: shorter retention in dev
```

Result after deep merge for ATI1:

```
cassaforte:
  deleteMode: logical         ← From override (replaces base)
  retentionDays: 15           ← From override (replaces base)
  archiveDestination: /u/...  ← From base (not overridden)
```

---

## Data Flow: From File to Context

### Groovy Task Context

1. `ConfigurationLoader.load(path, stageId)`
2. `YamlSlurper.parse(baseFile)` — read base YAML
3. `YamlSlurper.parse(stageFile)` — read stage YAML
4. `mergeConfigs(base, stage)` — deep merge
5. `interpolateEnvVars(config)` — replace `${ENV_VAR}`
6. `validateConfig(schema)` — validate structure
7. `cache(config)` — cache result
8. Return `Map<String, Object>` — access via dot notation

### Shell Script Context

1. `source load-config.sh /path/to/config.yaml ATI1`
2. `load_configuration(baseFile)` — parse base YAML
3. `load_stage_overrides(stage)` — parse stage YAML (if exists)
4. `set -a; source props; set +a` — export env vars
5. Variables now available as `$BUILD_HLQ`, `$CASSAFORTE_DELETEMODE`, etc.

### Jenkins Pipeline Context

1. `readYaml file: "config/application-config.yaml"`
2. Jenkins built-in handles YAML parsing
3. Assign to env: `env.BUILD_HLQ = cfg.build.hlq`
4. Variables now available as `${BUILD_HLQ}`

---

## Accessing Configuration

### Groovy (dot notation)

```groovy
config.build.hlq                                                  // ✓
config.cassaforte.deleteMode                                      // ✓
ConfigurationLoader.getNestedValue(config, "cassaforte.deleteMode") // ✓
```

### Shell (env vars with underscores)

```bash
$BUILD_HLQ                    # ✓
$CASSAFORTE_DELETEMODE        # ✓ (yq converts to env vars)
$FEATURE_ENABLELISTINGUPLOAD  # ✓
```

### Jenkins (Groovy map)

```groovy
cfg.build.hlq                   // ✓
cfg['cassaforte']['deleteMode'] // ✓
```

---

## Validation Flow

Schema: `config/configuration-schema.json`

- `version` (required)
- `application.name` (required)
- `build.hlq` (required)
- `cassaforte.deleteMode` (enum: `physical` | `logical`)
- `cassaforte.retentionDays` (integer, min: 1)

`ConfigurationLoader.validateConfiguration(config, schema)`:

1. Check required fields exist
2. Check types match
3. Check enum values valid
4. Throw `Exception` if validation fails → else return valid config

---

## Secrets Management Pattern

`config/application-config.yaml` (committed to git):

```yaml
database:
  host: db.internal.example.com
  port: 5432
  userVar: DB_USER        # Reference to env var
  passwordVar: DB_PASSWORD # Reference to env var
```

Environment setup (not in git):

```bash
export DB_USER="dbadmin"
export DB_PASSWORD="secret123"  # Never commit this!
```

Groovy resolution:

```groovy
String dbUser     = System.getenv(config.database.userVar)
String dbPassword = System.getenv(config.database.passwordVar)
```

---

## Caching Strategy

Static cache in `ConfigurationLoader`:

```
configCache = ["path:base" → config, "path:ATI1" → config, ...]
```

| Scenario | Steps | Time |
|---|---|---|
| First load (cache miss) | Parse YAML + merge + validate + cache | < 100ms |
| Subsequent load (cache hit) | Check cache → return | ~1ms |

Clear cache for testing: `ConfigurationLoader.clearCache()`

---

## File Structure

```
your-dbb-repo/
│
├── config/
│   ├── application-config.yaml       # Base configuration (canonical)
│   ├── configuration-schema.json     # Validation schema
│   └── stages/
│       ├── ATI1.yaml                 # Development overrides
│       ├── ATI2.yaml                 # Pre-integration overrides
│       ├── ATO.yaml                  # Test overrides
│       ├── ST.yaml                   # Stage overrides
│       └── PR.yaml                   # Production overrides
│
├── groovy/
│   ├── ConfigurationLoader.groovy    # Reusable configuration class
│   ├── ConfigurationExample.groovy   # Example usage in DBB task
│   └── ...
│
├── scripts/
│   ├── load-config.sh               # Shell configuration loader
│   ├── cassaforte-standalone.groovy # Standalone Groovy app
│   └── ...
│
├── tasks/
│   ├── LoadConfiguration.groovy     # First task: load config
│   ├── CassaforteDelete.groovy      # Uses loaded config
│   └── ...
│
├── dbb-build.yaml                   # Build configuration
├── dbb-app.yaml                     # Application configuration
├── Jenkinsfile                      # Jenkins pipeline
└── README.md
```

---

## Integration Checklist

### Configuration Files

- [ ] `config/application-config.yaml` created
- [ ] `config/configuration-schema.json` created
- [ ] `config/stages/{ATI1,ATI2,ATO,ST,PR}.yaml` created

### Groovy Integration

- [ ] `ConfigurationLoader.groovy` copied to `groovy/`
- [ ] First task uses `ConfigurationLoader.load()`
- [ ] Configuration cached in context for other tasks
- [ ] `dbb-build.yaml` updated with `LoadConfiguration` task

### Shell Integration

- [ ] `load-config.sh` copied to `scripts/`
- [ ] Shell scripts source `load-config.sh`
- [ ] Environment variables verified

### Jenkins Integration

- [ ] Jenkinsfile uses `readYaml`
- [ ] Configuration passed to dbb build step
- [ ] Stage-specific values handled

### Testing

- [ ] Test `ConfigurationLoader` in Groovy
- [ ] Test `load-config.sh` in shell
- [ ] Test `readYaml` in Jenkins
- [ ] Verify stage overrides applied correctly

### Documentation

- [ ] `README.md` documents configuration usage
- [ ] Team trained on configuration approach
- [ ] Secrets management documented
