#!/usr/bin/env bash
# Runs on a booted emulator. Locale comes from the test runner; setprop records the same choice.
if [ -n "${GITHUB_WORKSPACE:-}" ] && [ -f "$GITHUB_WORKSPACE/emulator-job.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$GITHUB_WORKSPACE/emulator-job.env"
  set +a
fi
set -euo pipefail

PKG=com.intelliverse.croman
APK="${CROMAN_APK:?}"
TEST_APK="${CROMAN_TEST_APK:?}"
LOCALES="${CROMAN_LOCALES:?}"
API="${CROMAN_API:?}"
NOTE="${CROMAN_IMAGE_NOTE:-exact}"
OUT="${CROMAN_RESULTS:-device-results}"
rm -rf "$OUT"
mkdir -p "$OUT"

adb wait-for-device
if [ ! -f "$APK" ] || ! adb install -r -t -g "$APK" >"$OUT/install-app.txt" 2>&1; then
  echo "fail: API ${API} could not install the app ($APK)" | tee "$OUT/status.txt"
  cat "$OUT/install-app.txt" >> "$OUT/status.txt" 2>/dev/null || true
  exit 1
fi
if [ ! -f "$TEST_APK" ] || ! adb install -r -t -g "$TEST_APK" >"$OUT/install-tests.txt" 2>&1; then
  echo "fail: API ${API} could not install the tests ($TEST_APK)" | tee "$OUT/status.txt"
  cat "$OUT/install-tests.txt" >> "$OUT/status.txt" 2>/dev/null || true
  exit 1
fi

pull_shots() {
  local dest="$1"
  mkdir -p "$dest"
  local names name
  names=$(adb shell run-as "$PKG" ls files/screenshots 2>/dev/null | tr -d '\r' || true)
  if [ -n "$names" ]; then
    for name in $names; do
      case "$name" in
        *.png)
          adb exec-out run-as "$PKG" cat "files/screenshots/$name" >"$dest/$name" || true
          ;;
      esac
    done
    return 0
  fi
  adb pull "/sdcard/Android/data/${PKG}/files/screenshots/." "$dest" || true
}

apply_locale() {
  local tag="$1"
  local lang country
  case "$tag" in
    ru) lang=ru; country=RU ;;
    iw|he) lang=he; country=IL ;;
    *) lang=en; country=US ;;
  esac
  adb shell setprop persist.sys.locale "${lang}-${country}" || true
  adb shell setprop persist.sys.language "$lang" || true
  adb shell setprop persist.sys.country "$country" || true
  adb shell settings put system system_locales "${lang}-${country}" || true
  adb shell cmd locale set-app-locales "$PKG" --locales "$lang" || true
}

for locale in $LOCALES; do
  adb shell pm clear "$PKG" >/dev/null
  apply_locale "$locale"
  adb logcat -c || true
  set +e
  adb shell am instrument -w -e locale "$locale" \
    "${PKG}.test/com.intelliverse.croman.CromanDeviceRunner" \
    >"$OUT/instrument-${locale}.txt" 2>&1
  code=$?
  set -e
  adb exec-out screencap -p >"$OUT/${locale}-foreground.png" || true
  pull_shots "$OUT/shots-${locale}"
  if [ "$code" -ne 0 ] || grep -q "FAILURES!!!" "$OUT/instrument-${locale}.txt"; then
    echo "fail: API ${API} locale ${locale} instrumented tests exit ${code}" | tee "$OUT/status.txt"
    tail -n 30 "$OUT/instrument-${locale}.txt" | tee -a "$OUT/status.txt" || true
    exit 1
  fi
done

apply_locale "${LOCALES##* }"
adb shell am force-stop "$PKG" || true
adb logcat -c || true
adb shell am start -W -n "${PKG}/.MainActivity"
sleep 2
adb exec-out screencap -p >"$OUT/launch.png" || true
set +e
timeout 180 adb shell monkey -p "$PKG" -s 20261126 --throttle 40 -v 500 >"$OUT/monkey.txt" 2>&1
monkey_code=$?
set -e
adb logcat -d >"$OUT/logcat.txt" || true
if [ "$monkey_code" -ne 0 ]; then
  echo "fail: API ${API} monkey exit ${monkey_code}" | tee "$OUT/status.txt"
  exit 1
fi
if awk '
  /FATAL EXCEPTION/ { capture = 1; next }
  capture && /Process: com.intelliverse.croman/ { found = 1 }
  capture && NF == 0 { capture = 0 }
  END { exit found ? 0 : 1 }
' "$OUT/logcat.txt"; then
  echo "fail: API ${API} crash in logcat" | tee "$OUT/status.txt"
  exit 1
fi
echo "pass: API ${API} (${NOTE}) locales ${LOCALES}" | tee "$OUT/status.txt"
