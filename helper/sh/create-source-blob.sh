#!/bin/sh
script_dir="$(cd "$(dirname "$0")" && pwd)"
# if arguments are not provided, print usage and exit
if [ "$#" -ne 2 ]; then
    echo "Error: Missing arguments"
    echo "Usage: $0 <source-directory> <output-file>"
    exit 1
fi
python3 ${script_dir}/../py/merge_groovy.py "$1" "$2"