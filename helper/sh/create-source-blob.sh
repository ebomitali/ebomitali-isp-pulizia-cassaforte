#!/bin/sh

if [ $# -ne 2 ]; then
    echo "Usage: $0 <source-directory> <output-file>" >&2
    exit 1
fi

script_dir="$(cd "$(dirname "$0")" && pwd)"
python3 ${script_dir}/../py/merge_groovy.py "$1" "$2"