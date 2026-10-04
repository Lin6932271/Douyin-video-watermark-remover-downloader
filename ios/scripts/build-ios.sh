#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
if [[ "$(uname -s)" != "Darwin" ]]; then
  echo 'This build requires macOS and Xcode.' >&2
  exit 1
fi
command -v xcodebuild >/dev/null || { echo 'Install Xcode and select its developer directory first.' >&2; exit 1; }
xcodebuild -version
MODE="${1:-simulator}"
mkdir -p build
case "$MODE" in
  simulator)
    xcodebuild -project Shiying.xcodeproj -scheme Shiying -configuration Debug \
      -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
      -derivedDataPath build/DerivedData CODE_SIGNING_ALLOWED=NO build 2>&1 | tee build/simulator-build.log
    echo "App: $ROOT/build/DerivedData/Build/Products/Debug-iphonesimulator/Shiying.app"
    ;;
  archive)
    : "${TEAM_ID:?Set TEAM_ID to your Apple signing team ID}"
    BUNDLE_ID="${BUNDLE_ID:-com.aojiao.shiying.ios}"
    xcodebuild -project Shiying.xcodeproj -scheme Shiying -configuration Release \
      -destination 'generic/platform=iOS' -archivePath build/Shiying.xcarchive \
      DEVELOPMENT_TEAM="$TEAM_ID" PRODUCT_BUNDLE_IDENTIFIER="$BUNDLE_ID" \
      -allowProvisioningUpdates archive 2>&1 | tee build/archive-build.log
    echo "Archive: $ROOT/build/Shiying.xcarchive"
    echo 'Export/install through Xcode Organizer with your signing account.'
    ;;
  *) echo 'Usage: bash scripts/build-ios.sh [simulator|archive]' >&2; exit 2 ;;
esac
