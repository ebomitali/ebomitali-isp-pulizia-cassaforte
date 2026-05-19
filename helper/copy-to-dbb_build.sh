#!/usr/bin/env zsh
# copy-to-dbb_build.sh — Copy project artifacts to the local dbb_build target directory.

TARGET="${1:-/Users/bomitalievelino/Documents/Workspace/isp-ibm-mauden/repo/dbb_build/cassaforte}"

# ── Files and directories to copy ──────────────────────────────────────────────
# Each entry is relative to the project root (where this script is run from).
# Directories are copied recursively; files are copied as-is.
ITEMS=(
    build/libs/pulizia-cassaforte.jar
    scripts/PuliziaCassaforte.groovy
    scripts/PuliziaPostBuild.groovy
    scripts/build-data/
    scripts/build-data/
    src/
    .gradle/8.12/
    gradle/
    gradlew
    gradlep
    build.gradle
    settings.gradle
)
# ───────────────────────────────────────────────────────────────────────────────

set -euo pipefail

SCRIPT_DIR="${0:A:h}"
PROJECT_ROOT="${SCRIPT_DIR:h}"

echo "Target : $TARGET"
echo "Source : $PROJECT_ROOT"
echo

mkdir -p "$TARGET"

for item in "${ITEMS[@]}"; do
    src="$PROJECT_ROOT/$item"
    if [[ ! -e "$src" ]]; then
        echo "SKIP  (not found) $item"
        continue
    fi

    if [[ -d "$src" ]]; then
        dst="$TARGET/$item"
        mkdir -p "$dst"
        cp -r "$src/." "$dst/"
        echo "COPIED dir  $item"
    else
        dst_dir="$TARGET/$(dirname "$item")"
        mkdir -p "$dst_dir"
        cp "$src" "$dst_dir/"
        echo "COPIED file $item"
    fi
done

echo
echo "Done."
