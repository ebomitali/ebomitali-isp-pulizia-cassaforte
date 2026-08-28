#!/usr/bin/env zsh
# copy-uss-to-dbb_build.sh — Copy project artifacts to the local dbb_build target directory.
# The dbb_build is a repository that is accessible to the target USS environemnt in z/OS machine.
# 1. copy to dbb_build
# 2. connect to z/OS
# 3. git sparse-checkout of uss directory
# 4. have fun
# This will ensure correct conversion/tagging by git USS client using .gitattributes configuration

TARGET="${1:-/Users/bomitalievelino/Documents/Workspace/isp-ibm-mauden/repo/dbb_build}"

# fat-source/FullPuliziaCassaforte.groovy and front-end/PuliziaPostBuild.groovy land flattened
# under this fixed cassaforte groovy directory, not at their source-relative path.
CASSAFORTE_ITEMS=(
    fat-source/src/main/groovy/FullPuliziaCassaforte.groovy
    front-end/src/main/groovy/PuliziaPostBuild.groovy
)
CASSAFORTE_DST_SUBDIR="puliziapostbuild/build/groovy/cassaforte"
# ───────────────────────────────────────────────────────────────────────────────

set -euo pipefail

SCRIPT_DIR="${0:A:h}"
PROJECT_ROOT="${SCRIPT_DIR:h:h}"

echo "Target : $TARGET"
echo "Project root : $PROJECT_ROOT"
echo

mkdir -p "$TARGET"

cassaforte_dst="$TARGET/$CASSAFORTE_DST_SUBDIR"
mkdir -p "$cassaforte_dst"
for item in "${CASSAFORTE_ITEMS[@]}"; do
    src="$PROJECT_ROOT/$item"
    if [[ ! -e "$src" ]]; then
        echo "SKIP  (not found) $item"
        continue
    fi
    cp "$src" "$cassaforte_dst/"
    echo "COPIED file $item -> $cassaforte_dst/$(basename "$item")"
done

echo
echo "Done."
