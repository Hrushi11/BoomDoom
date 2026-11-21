"""Automated dev-data collection from a connected phone (debug build only).

Drives Instagram Reels over adb (one swipe every few seconds) while the BoomDoom debug dump records, for each
reel, the captured frame, the caption text Instagram exposes and its sponsored flag. Files are pulled into
ml/data/reels/ (git-ignored). This is the developer's own feed on the developer's own phone; nothing here runs in
release builds (the dump needs `adb run-as`, which only works on debuggable apps).

Before running: open Instagram on the Reels tab and turn on screen understanding in BoomDoom.
    python collect_reels.py --reels 300
"""

import argparse
import random
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
PKG = "app.boomdoom.demo"
OUT_DIR = "files/ml_debug_out"


def adb(*args, binary=False):
    out = subprocess.run(["adb", *args], capture_output=True, check=False)
    return out.stdout if binary else out.stdout.decode(errors="replace")


def foreground():
    for line in adb("shell", "dumpsys", "activity", "activities").splitlines():
        if "topResumedActivity" in line:
            return line
    return ""


def capture_on():
    return "isForeground=true" in adb("shell", "dumpsys", "activity", "services", f"{PKG}/app.boomdoom.monitor.capture.MonitorService")


def recover():
    """Bring Instagram back (e.g. after a system dialog); its task keeps the Reels tab."""
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "am", "start", "-n", "com.instagram.android/.activity.MainTabActivity")
    time.sleep(3)
    return "com.instagram.android" in foreground()


def pull(dest):
    names = adb("shell", "run-as", PKG, "ls", OUT_DIR).split()
    new = 0
    for name in names:
        target = dest / name
        if target.exists():
            continue
        data = adb("exec-out", "run-as", PKG, "cat", f"{OUT_DIR}/{name}", binary=True)
        if data:
            target.write_bytes(data)
            new += name.endswith(".json")
    return new


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--reels", type=int, default=300)
    parser.add_argument("--dwell", type=float, default=3.0, help="mean seconds on each reel")
    parser.add_argument("--out", type=Path, default=HERE / "data/reels")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)

    width, height = (int(v) for v in adb("shell", "wm", "size").split(":")[-1].strip().split("x"))
    if "com.instagram.android" not in foreground():
        sys.exit("Open Instagram on the Reels tab first.")
    if not capture_on():
        sys.exit("Turn on screen understanding in BoomDoom first (Home banner -> Turn on -> Instagram).")

    before = len(list(args.out.glob("*.json")))
    adb("shell", "svc", "power", "stayon", "true")  # keep the screen on (screen-off ends the capture)
    try:
        run(args, width, height)
    finally:
        adb("shell", "svc", "power", "stayon", "false")
        pull(args.out)
    print(f"done: {len(list(args.out.glob('*.json'))) - before} new reels in {args.out}")


def run(args, width, height):
    recoveries = 0
    for i in range(1, args.reels + 1):
        if "com.instagram.android" not in foreground():
            recoveries += 1
            print(f"Instagram left the foreground ({foreground().strip()[:90]}); recovering", flush=True)
            if recoveries > 3 or not recover():
                print("could not bring Instagram back; stopping.")
                break
        if i % 25 == 0 and not capture_on():
            print("screen capture is off (re-enable it in BoomDoom); stopping.")
            break
        x = width // 2 + random.randint(-60, 60)
        adb("shell", "input", "swipe", str(x), str(int(height * 0.75)), str(x), str(int(height * 0.25)), str(random.randint(180, 260)))
        time.sleep(max(1.8, random.gauss(args.dwell, 0.5)))
        if i % 25 == 0:
            print(f"{i} swipes, +{pull(args.out)} reels pulled", flush=True)


if __name__ == "__main__":
    main()
