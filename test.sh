#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$PROJECT_DIR/build"

"$PROJECT_DIR/build.sh"
rm -rf "$BUILD_DIR/test-classes"
mkdir -p "$BUILD_DIR/test-classes"

find "$PROJECT_DIR/src/test/java" -name '*.java' -print0 \
  | xargs -0 javac --release 17 -encoding UTF-8 -cp "$BUILD_DIR/classes" -d "$BUILD_DIR/test-classes"

TEST_CLASSES=(
  com.istad.library.model.LoanTest
  com.istad.library.storage.RepositoryTest
  com.istad.library.service.LibraryServiceTest
  com.istad.library.service.SelfServiceTest
  com.istad.library.document.FineInvoicePdfTest
  com.istad.library.ui.UiPanelSmokeTest
  com.istad.library.MainSmokeTest
)

for test_class in "${TEST_CLASSES[@]}"; do
  java -ea -Djava.awt.headless=true -cp "$BUILD_DIR/classes:$BUILD_DIR/test-classes" "$test_class"
done

PRESERVATION_DIR="$(mktemp -d /tmp/library-build-preservation.XXXXXX)"
trap 'rm -rf "$PRESERVATION_DIR"' EXIT
mkdir -p "$PRESERVATION_DIR/dist/data"
printf '%s\n' 'keep-existing-library-data' > "$PRESERVATION_DIR/dist/data/preservation-marker.txt"
LIBRARY_BUILD_DIR="$PRESERVATION_DIR/build" \
LIBRARY_DIST_DIR="$PRESERVATION_DIR/dist" \
  "$PROJECT_DIR/build.sh" >/dev/null
if [[ "$(<"$PRESERVATION_DIR/dist/data/preservation-marker.txt")" != "keep-existing-library-data" ]]; then
  echo "BuildDataPreservationTest failed: build replaced existing dist/data." >&2
  exit 1
fi
echo "BuildDataPreservationTest passed."

echo "All tests passed."
