#!/bin/bash
set -euo pipefail

MODEL="my-zgroovy"
CONTEXT_SIZE=10240
# First arg: optional specific file, or --auto to process all without preview
TARGET_FILE=""
AUTO=""
for arg in "$@"; do
  if [ "$arg" = "--auto" ]; then
    AUTO="--auto"
  elif [ -f "$arg" ]; then
    TARGET_FILE="$arg"
  fi
done

BACKUP_DIR="./zgroovy_comments_backup_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BACKUP_DIR"

echo "🔍 Scanning .groovy files..."
COUNT=0

# If a specific file was given, process only that file; otherwise scan all .groovy files
if [ -n "$TARGET_FILE" ]; then
  FILE_LIST="$TARGET_FILE"
else
  FILE_LIST=$(find . -type f -name "*.groovy")
fi

echo "$FILE_LIST" | while read -r FILE; do
  [ -z "$FILE" ] && continue
  echo "→ $FILE"
  
  # 1. Backup original — create intermediate backup dirs to mirror source structure
  mkdir -p "$(dirname "$BACKUP_DIR/$FILE.bak")"
  cp "$FILE" "$BACKUP_DIR/$FILE.bak"
  
  # 2. Generate inline comments via Ollama
  COMMENTED=$(ollama run "$MODEL" \
    -p "Language: Groovy (IBM Z Mainframe)\nCode:\n$(cat "$FILE")" \
    --ctx-size "$CONTEXT_SIZE" 2>/dev/null | sed '/^```/d; /^$/d')
  
  if [ -z "$COMMENTED" ]; then
    echo "⚠️ Skipped (empty output)"
    continue
  fi
  
  # 3. Safe temp file
  TEMP="${FILE}.tmp"
  printf '%s\n' "$COMMENTED" > "$TEMP"
  
  # 4. Structure validation (prevents silent corruption)
  ORIG_LINES=$(wc -l < "$FILE")
  NEW_LINES=$(wc -l < "$TEMP")
  
  if [ "$ORIG_LINES" -ne "$NEW_LINES" ]; then
    echo "⚠️ Line count mismatch (orig: $ORIG_LINES, new: $NEW_LINES). Keeping original."
    rm -f "$TEMP"
    continue
  fi
  
  # 5. Syntax check (if groovy CLI is available)
  if command -v groovy &>/dev/null; then
    if ! groovy -e "new groovy.lang.GroovyShell().parse(new File('$TEMP'))" 2>/dev/null; then
      echo "⚠️ Syntax check failed. Keeping original."
      rm -f "$TEMP"
      continue
    fi
  fi
  
  # 6. Preview or apply
  if [ "$AUTO" != "--auto" ]; then
    echo "📄 Preview diff:"
    diff -u "$FILE" "$TEMP" | head -20
    read -p "✅ Apply to $FILE? (y/n): " -n 1 -r
    echo
    [[ $REPLY =~ ^[Yy]$ ]] || { rm -f "$TEMP"; continue; }
  fi
  
  mv "$TEMP" "$FILE"
  echo "✅ Injected: $FILE"
  COUNT=$((COUNT + 1))
done

echo "🎉 Done. $COUNT files updated."
echo "📁 Backups saved in: $BACKUP_DIR"
echo "🔄 Restore all: cp $BACKUP_DIR/*.bak $(find . -name '*.groovy' -exec dirname {} \; | sort -u | tr '\n' ' ')"
