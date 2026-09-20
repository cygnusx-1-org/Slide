#!/usr/bin/env python3
#
# Does a judged row actually leave the Megareddit "Subreddits" list, and stay gone?
#
# The scan rewrites the list roughly once a second while it runs, so a + or - pressed during a page
# that is still in flight can be undone by the older definition that page carries back. The symptom
# is a row that vanishes and then returns, which reads as a tap that did not take. This taps one
# row and watches that subreddit for a few seconds:
#
#   ..........   left immediately and stayed gone   -- what should happen
#   T.........   the dump caught the tap in flight  -- fine
#   ...T......   came back after leaving            -- the definition was reverted
#   TTTTTTTTTT   never left                         -- the tap landed on another row, which is
#                                                      what re-sorting the live list causes
#
# Usage:
#   scripts/megareddit_row_stays_hidden.py [--serial SERIAL] [--package PKG] [--trials N] [--row N] [--samples N]
# Examples:
#   scripts/megareddit_row_stays_hidden.py
#   scripts/megareddit_row_stays_hidden.py --trials 10 --row 0
#
# Prereqs: a device/emulator on `adb devices`, and the Subreddits scan already open and listing
#   rows (Megareddits -> overflow -> Subreddits).
#
# Exits 0 when no row came back, 1 when one did, 2 on a setup problem.

import argparse
import re
import subprocess
import sys
import time

NODE = re.compile(r"<node[^>]*>")
DIGITS = re.compile(r"\d+")


def adb(serial, *args, timeout=60):
    cmd = ["adb"] + (["-s", serial] if serial else []) + list(args)
    try:
        return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout).stdout
    except subprocess.TimeoutExpired:
        return ""


def rows(serial):
    """Every listed subreddit, with the coordinates of its + and - buttons."""
    adb(serial, "shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb(serial, "exec-out", "cat", "/sdcard/ui.xml")
    found = []
    for m in NODE.finditer(xml):
        s = m.group(0)

        def attr(k):
            hit = re.search(k + r'="([^"]*)"', s)
            return hit.group(1) if hit else ""

        rid = attr("resource-id").split("/")[-1]
        if rid not in ("add_positive", "add_negative"):
            continue
        bounds = DIGITS.findall(attr("bounds"))
        if len(bounds) != 4:
            continue
        left, top, right, bottom = map(int, bounds)
        found.append(
            {
                "subreddit": re.sub(r"^Add /r/| to .*$", "", attr("content-desc")),
                "side": rid,
                "cx": (left + right) // 2,
                "cy": (top + bottom) // 2,
            }
        )
    return found


def main():
    ap = argparse.ArgumentParser(description="Watch whether a judged row stays gone.")
    ap.add_argument("--serial", default=None, help="adb serial; the only device by default")
    ap.add_argument("--package", default="me.edgan.redditslide.debug")
    ap.add_argument("--trials", type=int, default=6)
    ap.add_argument("--row", type=int, default=2, help="which listed row to tap")
    ap.add_argument("--samples", type=int, default=10)
    ap.add_argument("--interval", type=float, default=0.45)
    args = ap.parse_args()

    if "device" not in adb(args.serial, "get-state"):
        print("No device on adb. Connect a device/emulator first.", file=sys.stderr)
        return 2

    positives = [r for r in rows(args.serial) if r["side"] == "add_positive"]
    if not positives:
        print(
            "No rows listed. Open Megareddits -> overflow -> Subreddits first.", file=sys.stderr
        )
        return 2

    returned = 0
    for trial in range(1, args.trials + 1):
        listed = [r for r in rows(args.serial) if r["side"] == "add_positive"]
        if not listed:
            print(f"trial {trial}: nothing left to tap")
            break
        target = listed[min(args.row, len(listed) - 1)]
        name = target["subreddit"]
        adb(args.serial, "shell", "input", "tap", str(target["cx"]), str(target["cy"]))

        seen = []
        for _ in range(args.samples):
            time.sleep(args.interval)
            seen.append(name in [r["subreddit"] for r in rows(args.serial)])
        # A True after a False is the row coming back, which is the defect. A leading run of
        # Trues is the tap still in flight, or a tap that landed on a different row.
        came_back = any(seen[i] and not seen[i - 1] for i in range(1, len(seen)))
        returned += came_back
        trace = "".join("T" if s else "." for s in seen)
        note = "CAME BACK" if came_back else ("never left" if all(seen) else "ok")
        print(f"trial {trial}: /r/{name:<24} {trace}  {note}")

    print(f"rows that came back: {returned}/{args.trials}")
    return 1 if returned else 0


if __name__ == "__main__":
    sys.exit(main())
