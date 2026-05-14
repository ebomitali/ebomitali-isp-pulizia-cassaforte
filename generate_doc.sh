#!/bin/bash
# Convert markdown file to docx using a reference document

# Parse arguments
REF_DOC=""
INPUT_FILE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --ref-doc)
            REF_DOC="$2"
            shift 2
            ;;
        *)
            INPUT_FILE="$1"
            shift
            ;;
    esac
done

# Check if input file is provided
if [ -z "$INPUT_FILE" ]; then
    echo "Usage: $0 [--ref-doc <reference.docx>] <filename.md>"
    echo "Example: $0 --ref-doc custom_ref.docx report.md"
    echo "Example: $0 report.md"
    echo ""
    echo "If --ref-doc is not specified, the script will look for:"
    echo "  1. hog_reference.docx"
    echo "  2. reference.docx"
    exit 1
fi

# Determine reference document
if [ -z "$REF_DOC" ]; then
    # No --ref-doc specified, search for default files
    if [ -f "hog_reference.docx" ]; then
        REF_DOC="hog_reference.docx"
    elif [ -f "reference.docx" ]; then
        REF_DOC="reference.docx"
    else
        echo "Error: No reference document found"
        echo "Please provide --ref-doc option or ensure 'hog_reference.docx' or 'reference.docx' exists"
        exit 1
    fi
fi

# Validate reference document exists
if [ ! -f "$REF_DOC" ]; then
    echo "Error: Reference file '$REF_DOC' not found"
    exit 1
fi

# Validate input file
BASENAME=$(basename "$INPUT_FILE" .md)
DIR=$(dirname "$INPUT_FILE")
OUTPUT_FILE="${DIR}/${BASENAME}.docx"

if [ ! -f "$INPUT_FILE" ]; then
    echo "Error: File '$INPUT_FILE' not found"
    exit 1
fi

echo "Converting $INPUT_FILE to $OUTPUT_FILE using $REF_DOC as reference..."
pandoc "$INPUT_FILE" --reference-doc="$REF_DOC" -o "$OUTPUT_FILE"

if [ $? -ne 0 ]; then
    echo "Error: Conversion failed"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
VENV_PYTHON="$SCRIPT_DIR/.venv/bin/python3"
if [ -x "$VENV_PYTHON" ]; then
    PYTHON="$VENV_PYTHON"
elif command -v python3 &>/dev/null; then
    PYTHON="python3"
else
    PYTHON=""
fi

if [ -n "$PYTHON" ] && "$PYTHON" -c "import docx" &>/dev/null; then
    "$PYTHON" "$SCRIPT_DIR/scripts/add_table_borders.py" "$OUTPUT_FILE"
fi

echo "Successfully created $OUTPUT_FILE"
