#!/bin/bash
# scripts/test/run_tests.sh
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$SCRIPT_DIR/.."
for f in "$SCRIPT_DIR"/Test*.groovy; do
    if groovy -cp "$ROOT/lib:$ROOT/tasks" "$f"; then
        echo "PASS: $(basename "$f")"
    else
        echo "FAIL: $(basename "$f")"
        exit 1
    fi
done
echo ""
echo "ALL TESTS PASSED"
