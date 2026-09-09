#!/bin/bash
set -euo pipefail
mkdir -p output/diagnostics
collect_diagnostics() {
  find "$HOME/Library/Logs/DiagnosticReports" -name '*AniBrowser*' -type f -exec cp {} output/diagnostics/ \; 2>/dev/null || true
  if [ -n "${DEVICE_ID:-}" ]; then
    xcrun simctl spawn "$DEVICE_ID" log show --style compact --last 3m --predicate 'process == "AniBrowser"' > output/diagnostics/app.log 2>&1 || true
  fi
}
trap collect_diagnostics EXIT
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
  # A fresh hosted simulator continues first-boot setup after bootstatus returns.
  sleep 20
  xcrun simctl install "$DEVICE_ID" simulator-build/Build/Products/Debug-iphonesimulator/AniBrowser.app
  xcrun simctl launch "$DEVICE_ID" app.anibrowser.ios
  sleep 5
  xcrun simctl io "$DEVICE_ID" screenshot "output/$family-home.png"
  xcrun simctl terminate "$DEVICE_ID" app.anibrowser.ios
  xcrun simctl shutdown "$DEVICE_ID"
done
