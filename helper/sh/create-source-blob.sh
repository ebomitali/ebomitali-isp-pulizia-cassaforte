#!/bin/sh
script_dir="$(cd "$(dirname "$0")" && pwd)"
echo "Usage: $0 <source-directory> <output-file>"
echo "curr dir: $(pwd)"
echo "script dir: ${script_dir}"
python3 ${script_dir}/../py/merge_groovy.py "$1" "$2"