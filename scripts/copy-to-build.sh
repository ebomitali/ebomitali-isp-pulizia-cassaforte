#!/bin/sh

# POSIX-compliant script to copy files to build directory

set -e

BUILD_DIR="${BUILD_DIR:-.}/build"
SOURCE_DIR="${SOURCE_DIR:-.}"

# Create build directory if it doesn't exist
mkdir -p "$BUILD_DIR"

# Copy files to build directory
find "$SOURCE_DIR" -type f -name "*.groovy" | while read -r file; do
    if [ -f "$file" ]; then
        cp "$file" "$BUILD_DIR/"
    fi
done

echo "Copy to build complete"
