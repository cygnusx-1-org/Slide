#!/usr/bin/env bash
#
# Runtime crash fuzzing via the Android Monkey.
#
# Drives random UI input against the installed debug build to surface crashes the static audit and
# Robolectric helper test can't reach (site-specific dialog triggers, real Activity lifecycle).
# Runs several fixed seeds so failures are reproducible. Any crash/ANR aborts the monkey and this
# script exits non-zero with the captured stack.
#
# Usage:
#   scripts/monkey_crash_test.sh [package] [events_per_seed] [seed ...]
# Examples:
#   scripts/monkey_crash_test.sh
#   scripts/monkey_crash_test.sh me.edgan.redditslide.debug 8000 1 2 3
#
# Prereqs: a device/emulator on `adb devices`, and the build installed
#   (./gradlew installNoGPlayDebug   or   ./gradlew installWithGPlayDebug).

set -uo pipefail

PKG="${1:-me.edgan.redditslide.debug}"
EVENTS="${2:-5000}"
shift $(( $# > 2 ? 2 : $# )) || true
SEEDS=("${@:-1 7 42}")
# normalize when defaulted (the :- above yields a single "1 7 42" element)
[ "${#SEEDS[@]}" -eq 1 ] && read -r -a SEEDS <<< "${SEEDS[0]}"

if ! adb get-state >/dev/null 2>&1; then
    echo "No device on adb. Connect a device/emulator first." >&2
    exit 2
fi
if ! adb shell pm list packages | tr -d '\r' | grep -q "package:${PKG}$"; then
    echo "Package $PKG is not installed. Run ./gradlew installNoGPlayDebug first." >&2
    exit 2
fi

log="$(mktemp -t monkey_${PKG//./_}_XXXX.log)"
echo "Package : $PKG"
echo "Events  : $EVENTS per seed"
echo "Seeds   : ${SEEDS[*]}"
echo "Log     : $log"
echo

adb logcat -c || true
fail=0
for seed in "${SEEDS[@]}"; do
    echo "=== monkey seed=$seed ==="
    # --throttle keeps it from outrunning the UI; --pct-syskeys 0 avoids leaving the app via
    # system keys; crashes/ANRs stop the run (default, no --ignore-crashes) so we can catch them.
    if ! adb shell monkey -p "$PKG" \
            --throttle 250 --pct-syskeys 0 --pct-anyevent 0 \
            --monitor-native-crashes -s "$seed" -v "$EVENTS" 2>&1 | tee -a "$log" \
            | grep -qE "Monkey finished"; then
        echo "  -> seed $seed did NOT finish cleanly (crash/ANR)."
        fail=1
    fi
done

echo
echo "=== crash buffer ==="
adb logcat -b crash -d | tee -a "$log" | tail -60

if [ "$fail" -ne 0 ] || grep -qE "CRASH|ANR|FATAL EXCEPTION" "$log"; then
    echo
    echo "FAIL: monkey surfaced a crash/ANR. Full log: $log"
    exit 1
fi
echo
echo "OK: all seeds finished with no crash. Log: $log"
