#!/bin/bash
set -euo pipefail
xcodebuild -project AniBrowser.xcodeproj -scheme AniBrowser -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath simulator-build CODE_SIGNING_ALLOWED=NO build > simulator-build.log 2>&1
mkdir -p output
for family in iPhone iPad; do
  DEVICE_FAMILY="$family" python3 - <<'PY' > /tmp/ani-device-id
import json, os, subprocess
devices = json.loads(subprocess.check_output(['xcrun','simctl','list','devices','available','--json']))
matches = [d for group in devices['devices'].values() for d in group
           if d.get('isAvailable') and os.environ['DEVICE_FAMILY'] in d['name']]
if not matches:
    raise SystemExit('No available simulator for ' + os.environ['DEVICE_FAMILY'])
print(matches[0]['udid'])
PY
  DEVICE_ID="$(cat /tmp/ani-device-id)"
  xcrun simctl boot "$DEVICE_ID" || true
  xcrun simctl bootstatus "$DEVICE_ID" -b
  xcrun simctl install "$DEVICE_ID" simulator-build/Build/Products/Debug-iphonesimulator/AniBrowser.app
  xcrun simctl launch "$DEVICE_ID" app.anibrowser.ios
  sleep 5
  xcrun simctl io "$DEVICE_ID" screenshot "output/$family-home.png"
  xcrun simctl terminate "$DEVICE_ID" app.anibrowser.ios
  xcrun simctl shutdown "$DEVICE_ID"
done
