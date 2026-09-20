#!/usr/bin/env python3
#
# Stress the Megareddit "Subreddits" scan.
#
# The scan walks r/all for minutes while the user adds subreddits to the Megareddit's positive and
# negative lists from the same screen, so a background thread and the foreground keep rewriting the
# same definition. That interleaving is what this drives: each round opens the scan, lets it run,
# scrolls the list, taps + or - on random rows, then leaves by Stop, by Back, or by way of a
# subreddit screen. Anything that crashes, ANRs, or drops the user back to the Megareddits screen
# shows up in the per-round line and in the captured logcat.
#
# Usage:
#   scripts/megareddit_scan_stress.py [--serial SERIAL] [--package PKG] [--rounds N] [--seed N] [--log PATH]
# Examples:
#   scripts/megareddit_scan_stress.py
#   scripts/megareddit_scan_stress.py --rounds 24 --seed 7
#
# Prereqs: a device/emulator on `adb devices`, the debug build installed
#   (./gradlew installWithGPlayDebug), and the app sitting on the Megareddits screen with at
#   least one Megareddit defined.
#
# Exits 0 when every round finished with no crash, 1 on a crash or ANR, 2 on a setup problem.

import argparse
import random
import re
import subprocess
import sys
import time

NODE = re.compile(r"<node[^>]*>")
DIGITS = re.compile(r"\d+")


class Device:
    def __init__(self, serial, package):
        self.serial = serial
        self.package = package

    def sh(self, *args, timeout=60):
        cmd = ["adb"]
        if self.serial:
            cmd += ["-s", self.serial]
        cmd += ["shell", *args]
        try:
            return subprocess.run(
                cmd, capture_output=True, text=True, timeout=timeout
            ).stdout
        except subprocess.TimeoutExpired:
            return ""

    def out(self, *args, timeout=60):
        cmd = ["adb"]
        if self.serial:
            cmd += ["-s", self.serial]
        cmd += [*args]
        try:
            return subprocess.run(
                cmd, capture_output=True, text=True, timeout=timeout
            ).stdout
        except subprocess.TimeoutExpired:
            return ""

    def dump(self):
        """The current window as uiautomator nodes. Retried: a busy window dumps nothing."""
        for _ in range(3):
            self.sh("uiautomator", "dump", "/sdcard/ui.xml")
            x = self.out("exec-out", "cat", "/sdcard/ui.xml")
            if "<node" in x:
                return parse(x)
            time.sleep(1)
        return []

    def tap(self, node):
        self.sh("input", "tap", str(node["cx"]), str(node["cy"]))

    def swipe(self, x1, y1, x2, y2, ms):
        self.sh("input", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms))

    def back(self):
        self.sh("input", "keyevent", "KEYCODE_BACK")

    def activity(self):
        m = re.search(
            r"topResumedActivity.*?Activities\.(\w+)", self.sh("dumpsys", "activity", "activities")
        )
        return m.group(1) if m else "?"

    def pss(self):
        m = re.search(r"TOTAL PSS:\s*(\d+)", self.sh("dumpsys", "meminfo", self.package))
        return int(m.group(1)) if m else -1

    def megareddits(self):
        raw = self.sh(
            "run-as",
            self.package,
            "cat",
            f"/data/data/{self.package}/shared_prefs/MEGAREDDITS.xml",
        ).replace("&quot;", '"')
        m = re.search(r"\[\{.*\}\]", raw)
        return m.group(0) if m else ""


def parse(xml):
    out = []
    for m in NODE.finditer(xml):
        s = m.group(0)

        def attr(k):
            found = re.search(k + r'="([^"]*)"', s)
            return found.group(1) if found else ""

        bounds = DIGITS.findall(attr("bounds"))
        if len(bounds) != 4:
            continue
        left, top, right, bottom = map(int, bounds)
        out.append(
            {
                "text": attr("text"),
                "desc": attr("content-desc"),
                "id": attr("resource-id").split("/")[-1],
                "cx": (left + right) // 2,
                "cy": (top + bottom) // 2,
            }
        )
    return out


def find(nodes, *, text=None, desc=None, rid=None, contains=None):
    for n in nodes:
        if text is not None and n["text"] != text:
            continue
        if desc is not None and n["desc"] != desc:
            continue
        if rid is not None and n["id"] != rid:
            continue
        if contains is not None and contains not in (n["text"] + n["desc"]):
            continue
        return n
    return None


def subreddit_of(node):
    """The subreddit a + / - button belongs to, read off its content description."""
    return re.sub(r"^Add /r/| to .*$", "", node["desc"])


def back_to_overview(dev, limit=6):
    for _ in range(limit):
        if dev.activity() == "MegaredditOverview":
            return True
        dev.back()
        time.sleep(2.5)
    return dev.activity() == "MegaredditOverview"


def open_subreddits(dev):
    """From the Megareddits screen: reveal the toolbar, then overflow -> Subreddits."""
    dev.swipe(640, 700, 640, 2300, 250)
    time.sleep(1.5)
    more = find(dev.dump(), desc="More options")
    if not more:
        return False
    dev.tap(more)
    time.sleep(2)
    item = find(dev.dump(), text="Subreddits")
    if not item:
        dev.back()
        return False
    dev.tap(item)
    time.sleep(3)
    return dev.activity() == "MegaredditCount"


def mutate(dev, rng, count):
    """Tap + or - on random rows. Re-dumps before each tap, since the list is live."""
    changed = []
    for _ in range(count):
        buttons = [
            n
            for n in dev.dump()
            if n["id"] in ("add_positive", "add_negative") and n["cy"] > 500
        ]
        if not buttons:
            return changed
        button = rng.choice(buttons)
        side = "positive" if button["id"] == "add_positive" else "negative"
        changed.append(f"{side}:{subreddit_of(button)}")
        dev.tap(button)
        time.sleep(1.2)
    return changed


def crashes(path, package):
    """Crashes and ANRs belonging to `package`, not to whatever else is on the device.

    A crash names its process a line or two under the FATAL EXCEPTION banner, so the banner alone
    is not enough to attribute one -- and attributing someone else's crash to this run would fail
    it for something it did not do.
    """
    try:
        with open(path, errors="replace") as f:
            lines = f.read().splitlines()
    except FileNotFoundError:
        return 0, 0
    fatal = 0
    for i, line in enumerate(lines):
        if "FATAL EXCEPTION" in line and any(
            package in near for near in lines[i : i + 4]
        ):
            fatal += 1
    anr = len(re.findall(r"ANR in " + re.escape(package), "\n".join(lines)))
    return fatal, anr


def main():
    ap = argparse.ArgumentParser(description="Stress the Megareddit Subreddits scan.")
    ap.add_argument("--serial", default=None, help="adb serial; the only device by default")
    ap.add_argument("--package", default="me.edgan.redditslide.debug")
    ap.add_argument("--rounds", type=int, default=12)
    ap.add_argument("--seed", type=int, default=20260919)
    ap.add_argument("--log", default="/tmp/megareddit-stress.log")
    args = ap.parse_args()

    dev = Device(args.serial, args.package)
    if "device" not in dev.out("get-state"):
        print("No device on adb. Connect a device/emulator first.", file=sys.stderr)
        return 2
    if f"package:{args.package}" not in dev.sh("pm", "list", "packages"):
        print(
            f"Package {args.package} is not installed. Run ./gradlew installWithGPlayDebug first.",
            file=sys.stderr,
        )
        return 2

    logcat_path = args.log + ".logcat"
    cmd = ["adb"] + (["-s", args.serial] if args.serial else []) + ["logcat", "-v", "time"]
    dev.sh("logcat", "-c")
    with open(logcat_path, "w") as sink:
        capture = subprocess.Popen(cmd, stdout=sink, stderr=subprocess.STDOUT)
        rng = random.Random(args.seed)
        before = dev.megareddits()
        log = open(args.log, "w")
        print(f"definitions before: {before}", file=log, flush=True)
        fatal = anr = 0
        try:
            for i in range(1, args.rounds + 1):
                if not back_to_overview(dev):
                    print(f"round {i}: not on the Megareddits screen ({dev.activity()}) -- stopping")
                    break
                if not open_subreddits(dev):
                    print(f"round {i}: could not open Subreddits ({dev.activity()})")
                    continue

                seconds = rng.choice([15, 20, 30, 45, 60])
                taps = rng.choice([2, 3, 4])
                style = rng.choice(["stop", "back", "stop", "back", "subview"])
                deadline = time.time() + seconds
                while time.time() < deadline:
                    dev.swipe(640, 2200, 640, 900, 150)
                    time.sleep(1)
                    dev.swipe(640, 900, 640, 2200, 150)
                    time.sleep(1)
                changed = mutate(dev, rng, taps)

                if style == "subview":
                    row = next(
                        (n for n in dev.dump() if n["id"] == "name" and n["cy"] > 500), None
                    )
                    if row:
                        dev.tap(row)
                        time.sleep(4)
                        dev.back()
                        time.sleep(3)
                elif style == "stop":
                    stop = find(dev.dump(), desc="Stop")
                    if stop:
                        dev.tap(stop)
                        time.sleep(3)

                summary = find(dev.dump(), rid="summary")
                fatal, anr = crashes(logcat_path, args.package)
                line = (
                    f"round {i:2d} scan={seconds:>2}s taps={taps} style={style:<7} "
                    f"act={dev.activity():<18} pss={dev.pss()}kB fatal={fatal} anr={anr}"
                )
                print(line, flush=True)
                print(
                    line
                    + "\n    summary: "
                    + (summary["text"].replace("&#10;", " / ") if summary else "(none)")
                    + "\n    changed: "
                    + ", ".join(changed),
                    file=log,
                    flush=True,
                )
                dev.back()
                time.sleep(3)
                if fatal or anr:
                    print("crash seen -- stopping", flush=True)
                    break
        finally:
            capture.terminate()
            capture.wait(timeout=10)
            print(f"definitions after: {dev.megareddits()}", file=log, flush=True)
            log.close()

    print(f"log: {args.log}   logcat: {logcat_path}")
    if fatal or anr:
        print(f"FAILED: {fatal} crash(es), {anr} ANR(s) -- see {logcat_path}", file=sys.stderr)
        return 1
    print("no crashes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
