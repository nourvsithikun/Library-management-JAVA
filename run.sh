#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_FILE="$PROJECT_DIR/dist/LibraryManagementSystem.jar"

if [[ ! -f "$JAR_FILE" ]]; then
  "$PROJECT_DIR/build.sh"
fi

cd "$PROJECT_DIR/dist"
java -jar "$JAR_FILE" "$@"
