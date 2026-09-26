#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="${LIBRARY_BUILD_DIR:-$PROJECT_DIR/build}"
DIST_DIR="${LIBRARY_DIST_DIR:-$PROJECT_DIR/dist}"

rm -rf "$BUILD_DIR/classes"
mkdir -p "$BUILD_DIR/classes" "$DIST_DIR"
rm -f "$DIST_DIR/LibraryManagementSystem.jar"

find "$PROJECT_DIR/src/main/java" -name '*.java' -print0 \
  | xargs -0 javac --release 17 -encoding UTF-8 -d "$BUILD_DIR/classes"

jar --create \
  --file "$DIST_DIR/LibraryManagementSystem.jar" \
  --main-class com.istad.library.Main \
  -C "$BUILD_DIR/classes" .

if [[ ! -d "$DIST_DIR/data" ]]; then
  cp -R "$PROJECT_DIR/data" "$DIST_DIR/data"
fi

echo "Created $DIST_DIR/LibraryManagementSystem.jar"
