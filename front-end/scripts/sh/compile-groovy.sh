#!/bin/bash

# Compile all .groovy files in a directory tree, preserving directory structure
# Usage: ./compile-groovy.sh src/zos/groovy classes

SOURCE_DIR="${1:-.}"
OUTPUT_DIR="${2:-build/classes/groovy/main}"
CLASSPATH="${DBB_HOME}/lib/*"

# Validate input
if [[ ! -d "$SOURCE_DIR" ]]; then
    echo "ERROR: Source directory '$SOURCE_DIR' not found"
    exit 1
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Count files
file_count=$(find "$SOURCE_DIR" -name "*.groovy" -type f | wc -l)
if [[ $file_count -eq 0 ]]; then
    echo "WARNING: No .groovy files found in $SOURCE_DIR"
    exit 0
fi

echo "Compiling $file_count .groovy files from $SOURCE_DIR to $OUTPUT_DIR"
echo ""

# Track compilation status
failed_files=()
succeeded=0

# Compile each .groovy file
while IFS= read -r groovy_file; do
    # Get relative path from SOURCE_DIR
    rel_path="${groovy_file#$SOURCE_DIR/}"
    rel_dir=$(dirname "$rel_path")
    
    # Create corresponding output directory
    if [[ "$rel_dir" != "." ]]; then
        mkdir -p "$OUTPUT_DIR/$rel_dir"
    fi
    
    echo "Compiling: $rel_path"
    
    # Compile the file
    groovyc \
        -J-Dfile.encoding=IBM-1047 \
        -J-Dstdout.encoding=IBM-1047 \
        -J-Dstderr.encoding=IBM-1047 \
        -cp "$CLASSPATH" \
        -d "$OUTPUT_DIR" \
        "$groovy_file" 2>&1
    
    if [[ $? -eq 0 ]]; then
        ((succeeded++))
    else
        failed_files+=("$groovy_file")
    fi
done < <(find "$SOURCE_DIR" -name "*.groovy" -type f | sort)

echo ""
echo "========================================="
echo "Compilation Summary"
echo "========================================="
echo "Total files: $file_count"
echo "Succeeded:  $succeeded"
echo "Failed:     ${#failed_files[@]}"

if [[ ${#failed_files[@]} -gt 0 ]]; then
    echo ""
    echo "Failed files:"
    for f in "${failed_files[@]}"; do
        echo "  - $f"
    done
    exit 1
else
    echo ""
    echo "All files compiled successfully"
    exit 0
fi