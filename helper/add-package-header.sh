#!/bin/bash

if [[ $# -ne 2 ]]; then
    echo "Usage: $0 <package-name> <directory>"
    exit 1
fi

PACKAGE="$1"
DIR="$2"

if [[ ! -d "$DIR" ]]; then
    echo "Error: Directory '$DIR' does not exist"
    exit 1
fi

find "$DIR" -type f \( -name "*.groovy" -o -name "*.java" \) | while read -r file; do
    if ! head -1 "$file" | grep -q "^package "; then
        {
            echo "package $PACKAGE"
            cat "$file"
        } > "$file.tmp"
        mv "$file.tmp" "$file"
        echo "Updated: $file"
    else
        echo "Skipped: $file (already has package)"
    fi
done
