#!/bin/sh
# Manual test for PuliziaCassaforte on z/OS USS.
# Run from any directory; no arguments required.
# Requires: groovyz, tsocmd, cp (z/OS USS).

set -eu

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
PC_HOME=/dist/DBB/work/dbb_build-develop/cassaforte
DBB_BUILD=/dist/DBB/work/u0g9700/pulizia-cassaforte/build
DBB_CONF=/prodotti/DEE/test/conf
HLQ=U0G9700

GROOVY_SCRIPT="${PC_HOME}/front-end/scripts/groovy/PuliziaCassaforte.groovy"
CLASSPATH="${DBB_BUILD}/groovy/pulizia-cassaforte/lib/pulizia-cassaforte.jar:${DBB_BUILD}/groovy/pulizia-cassaforte/lib/pulizia-cassaforte-zos.jar"

ATO_SRC="${PC_HOME}/ATO/yu_y_01_ato_r1/src/JCL/BATCH"
DS_SJCLCA7="${HLQ}.TW000.TEST.CSSAFORT.SJCLCA7"
DS_SJCLINP="${HLQ}.TW000.TEST.CSSAFORT.SJCLINP"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
fail() { printf 'FAIL: %s\n' "$*" >&2; }
ok()   { printf 'OK:   %s\n' "$*"; }

write_jcl() {
    cat <<'JCLEOF'
//HELLOJOB JOB ,'HELLO WORLD',CLASS=A,MSGCLASS=A,MSGLEVEL=(1,1)
//*
//STEP01 EXEC PGM=IEBGENER
//* Define the input data (the message)
//SYSUT1 DD *
HELLO, WORLD!
/*
//* Define the output destination (Job Log)
//SYSUT2 DD SYSOUT=*
//* Dummy DD statements for the utility
//SYSPRINT DD SYSOUT=*
//SYSIN DD DUMMY
/*
JCLEOF
}

# Ensure a PDS exists and contains no members, then copy all files from a
# USS directory into it (member name = filename without extension).
setup_pds() {
    dataset="$1"
    src_dir="$2"

    if ls "//'${dataset}'" > /dev/null 2>&1; then
        log "PDS ${dataset} exists — purging members"
        for member in $(ls -1 "//'${dataset}'" 2>/dev/null); do
            rm "//'${dataset}(${member})'"
            log "  deleted member ${member}"
        done
    else
        log "Creating PDS ${dataset}"
        tsocmd "ALLOCATE DATASET('${dataset}') NEW CATALOG UNIT(SYSDA) TRACKS SPACE(1,1) DSORG(PO) RECFM(F B) LRECL(80) BLKSIZE(3200) DIR(5)"
    fi

    for file in "${src_dir}"/*; do
        [ -f "$file" ] || continue
        fname=$(basename "$file")
        member=$(printf '%s' "$fname" | cut -d. -f1)
        log "  copying ${fname} -> ${dataset}(${member})"
        cp -T "$file" "//'${dataset}(${member})'"
        log "  copied ${fname}"
    done
}

# ---------------------------------------------------------------------------
# Step 1 — Create USS source files
# ---------------------------------------------------------------------------
log "Creating USS source files"

for subpath in \
    "SJCLCA7/YU7OMPAK.SJCLCA7" \
    "SJCLINP/YU7OMPAK.SJCLINP" \
    "SJCLINP/YU7OMPAJ.SJCLINP"
do
    target="${ATO_SRC}/${subpath}"
    mkdir -p "$(dirname "$target")"
    write_jcl > "$target"
    log "  wrote ${target}"
done

# ---------------------------------------------------------------------------
# Step 2 — Prepare PDS datasets
# ---------------------------------------------------------------------------
log "Setting up PDS ${DS_SJCLCA7}"
setup_pds "$DS_SJCLCA7" "${ATO_SRC}/SJCLCA7"

log "Setting up PDS ${DS_SJCLINP}"
setup_pds "$DS_SJCLINP" "${ATO_SRC}/SJCLINP"

# ---------------------------------------------------------------------------
# Step 3 — rules.csv
# Rule libraries contain the literal macro ${HLQ} — resolved at runtime by
# PuliziaCassaforte via --hlq argument.
# ---------------------------------------------------------------------------
RULES_FILE="${DBB_BUILD}/build-data/rules.csv"
log "Writing ${RULES_FILE}"
mkdir -p "$(dirname "$RULES_FILE")"
rm -f "$RULES_FILE"
printf 'SJCLCA7 ;${HLQ}.TW000.TEST.CASSAFORTE.SJCLCA7;NO\nSJCLINP ;${HLQ}.TW000.TEST.CASSAFORTE.SJCLINP;NO\n' \
    > "$RULES_FILE"

# ---------------------------------------------------------------------------
# Step 4 — lista-files.txt (only the two files to be cleaned)
# ---------------------------------------------------------------------------
LISTA_FILE="${PC_HOME}/ATO/lista-files.txt"
log "Writing ${LISTA_FILE}"
rm -f "$LISTA_FILE"
printf '%s\n%s\n' \
    "ATO/yu_y_01_ato_r1/src/JCL/BATCH/SJCLCA7/YU7OMPAK.SJCLCA7" \
    "ATO/yu_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YU7OMPAK.SJCLINP" \
    > "$LISTA_FILE"

# ---------------------------------------------------------------------------
# Step 5 — Run PuliziaCassaforte
# ---------------------------------------------------------------------------
ORIG_DIR=$(pwd)
log "Running PuliziaCassaforte from ${PC_HOME}/ATO"
cd "${PC_HOME}/ATO"

groovyz \
    -cp "$CLASSPATH" \
    "$GROOVY_SCRIPT" \
    --hlq "$HLQ" \
    lista-files.txt ATO ATO

cd "$ORIG_DIR"

# ---------------------------------------------------------------------------
# Step 6 — Verify outcomes
# ---------------------------------------------------------------------------
log "Verifying results"
RC=0

for expected_gone in \
    "${PC_HOME}/ATO/yu_y_01_ato_r1/src/JCL/BATCH/SJCLCA7/YU7OMPAK.SJCLCA7" \
    "${PC_HOME}/ATO/yu_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YU7OMPAK.SJCLINP"
do
    if [ -f "$expected_gone" ]; then
        fail "still exists (should have been deleted): ${expected_gone}"
        RC=1
    else
        ok "deleted: ${expected_gone}"
    fi
done

SURVIVOR="${PC_HOME}/ATO/yu_y_01_ato_r1/src/JCL/BATCH/SJCLINP/YU7OMPAJ.SJCLINP"
if [ -f "$SURVIVOR" ]; then
    ok "untouched: ${SURVIVOR}"
else
    fail "missing (should have been preserved): ${SURVIVOR}"
    RC=1
fi

if [ "$RC" -eq 0 ]; then
    log "All checks passed."
else
    log "One or more checks failed." >&2
fi

exit "$RC"
