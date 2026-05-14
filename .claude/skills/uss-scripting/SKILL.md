---
name: uss-scripting
description: Use when writing, editing, or debugging shell scripts that run on z/OS USS (remote) or that interact with z/OS USS from a local workstation via Zowe CLI (local). Triggers on: developing run.sh, zowe-push.sh, zowe-language-run.sh, adding Zowe RSE commands, uploading/downloading USS files, running DBB builds remotely, or any helper/ script work.
---

# USS Scripting

## Overview

Two distinct script types live in `helper/`:

| Type | Directory | Runs on | Purpose |
|------|-----------|---------|---------|
| **Remote** | `helper/uss/script/` | z/OS USS | Execute DBB build, set up environment |
| **Local** | `helper/local/script/` | Workstation | Push code, trigger remote run, download logs |

Both have an `.env` file for configuration. Never hardcode credentials or paths — always read from `.env`.

## Directory Layout

```
helper/
  uss/
    script/
      run.sh        # remote: DBB build entry point
      .env          # remote env vars (DBB_JAVA_HOME, DBB_CONF, DBB_HOME)
      ...           # other remote scripts if needed
  local/
    script/
      zowe-push.sh          # push local branch → sync remote working dir
      zowe-language-run.sh  # trigger remote run.sh + download logs
      .env                  # local env vars (ZOWE_*)
      ...                   # other local helper scripts if needed
```

## Remote `.env` Variables

```bash
DBB_JAVA_HOME=/usr/lpp/java/J17.0_64
DBB_CONF=/prodotti/DEE/test/conf
DBB_HOME=/prodotti/DEElink/test/dbb
```

## Local `.env` Variables

```bash
ZOWE_REMOTE_BASEDIR=/dist/DBB/work   # parent of all remote working dirs
ZOWE_USER=
ZOWE_PASS=
ZOWE_HOST=
ZOWE_PORT=
```

## Remote Working Dir Convention

Remote working directory is always:

```
${ZOWE_REMOTE_BASEDIR}/dbb_build-${BRANCH}
```

Where `BRANCH` = current local git branch (`git rev-parse --abbrev-ref HEAD`).

Sub-paths:
- Scripts: `…/test/script/`
- Build output: `…/test/build/`
- Logs: `…/test/logs/`, `…/test/dbb-build.log`, `…/test/dbb-script.log`

## Remote Script: `run.sh`

**Critical rule:** The zBuilder `dbb` command runs from the z/OS UNIX command line and must be launched from the root directory of the application. If the application is in a git repository, the root directory is the one containing the `.git` folder. If `dbb` is launched from a subdirectory, it will not be able to find the source files to build and the build will fail.

```bash
#!/bin/bash
# run.sh  — remote z/OS USS entry point
# Accepts one argument: path to a properties file with build parameters.
# Must be invoked with CWD = test/ (done by zowe-language-run.sh),
# but DBB itself is executed from the repo root.

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

# Source remote environment
set -a
source "$SCRIPT_DIR/.env"
set +a

PARAM_FILE="${1:?Usage: run.sh <property-file>}"

# Repo root = parent of test/
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd)

export PATH="$DBB_HOME/bin:$DBB_JAVA_HOME/bin:$PATH"

# Execute DBB from repo root so dbb-app.yaml is found if present
cd "$REPO_ROOT"
exec "$DBB_HOME/bin/groovyz" \
    -DBB_CONF="$DBB_CONF" \
    "$PARAM_FILE"
```

> If `dbb-app.yaml` is absent the build still works — DBB falls back to defaults.

## Local Scripts

### Common Zowe Options Pattern

```bash
ZOWE_OPTS=(
    --user     "$ZOWE_USER"
    --password "$ZOWE_PASS"
    --host     "$ZOWE_HOST"
    --port     "$ZOWE_PORT"
    --reject-unauthorized false
)
```

Pass `"${ZOWE_OPTS[@]}"` to every `zowe` call.

### Running Commands on USS

```bash
zowe rse issue unix "<command>" --cwd "$REMOTE_WDIR" "${ZOWE_OPTS[@]}"
```

### Downloading Files

```bash
# Single file
zowe rse download uss-file "$REMOTE_PATH" --file "$LOCAL_PATH" "${ZOWE_OPTS[@]}"

# Entire directory
zowe rse download uss-directory "$REMOTE_DIR" --directory "$LOCAL_DIR" --overwrite "${ZOWE_OPTS[@]}"
```

### `zowe-push.sh` — Push and Sync

Workflow:
1. `git add -A` + optional commit if staged changes exist
2. `git push origin $BRANCH`
3. `git reset --hard HEAD` on remote if dirty
4. `git switch $BRANCH` on remote if branch differs
5. `git pull` on remote
6. `chmod +x` remote `.sh` files

### `zowe-language-run.sh` — Run and Fetch Logs

Workflow:
1. Load `.env`
2. Validate: `zowe` in PATH, inside git repo, args present
3. Clean local `dbb-run/` directory
4. `zowe rse issue unix "run.sh <property-file>" --cwd $REMOTE_WDIR`
5. Download `dbb-build.log`, `dbb-script.log`, `logs/` → `dbb-run/`
6. Exit with remote RC (download logs even on failure)

## Boilerplate: Load `.env` (both script types)

```bash
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
set -a
source "$SCRIPT_DIR/.env"
set +a
```

## Prerequisites Validation Pattern

```bash
if [ -z "${ZOWE_USER:-}" ] || [ -z "${ZOWE_PASS:-}" ]; then
    echo "ERROR: ZOWE_USER and ZOWE_PASS must be set in .env" >&2; exit 1
fi
if ! command -v zowe >/dev/null 2>&1; then
    echo "ERROR: zowe CLI not found in PATH" >&2; exit 1
fi
if ! git rev-parse --git-dir >/dev/null 2>&1; then
    echo "ERROR: not a git repository" >&2; exit 1
fi
```

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| DBB run from `test/script/` | `cd` to repo root before `dbb` |
| Hardcoded creds/paths | Always source `.env` |
| Not capturing remote RC | `RUN_RC=0; zowe ... || RUN_RC=$?` then `exit "$RUN_RC"` |
| Skipping log download on failure | Download logs unconditionally, exit with `$RUN_RC` |
| Missing `--reject-unauthorized false` | Add to every Zowe call |
| Forgetting `chmod +x` after pull | Add to push script or run.sh setup |
| FileAnalysis task does not find source | source path should include the repository root |
