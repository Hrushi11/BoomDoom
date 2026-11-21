"""Load a film folder in headless Chromium, report errors, timing and (optionally) a contact sheet of stills.

Runs inside Stickman Studio's virtualenv (it has Playwright and Pillow), not the backend's:

    <stickman python> probe.py <film folder> <skill scripts dir> [--sheet out.jpg --times 1.5 4 ...]

Prints one JSON line: {"ok": bool, "errors": [...], "dur": 30, "cuts": [0, ...], "sheet": "<path>" | null}
"""

import argparse
import base64
import io
import json
import sys
from pathlib import Path


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("folder")
    ap.add_argument("scripts")
    ap.add_argument("--sheet")
    ap.add_argument("--times", nargs="*", type=float, default=[])
    a = ap.parse_args()
    sys.path.insert(0, a.scripts)
    from common import browser_args, page_url
    from playwright.sync_api import sync_playwright

    out = {"ok": False, "errors": [], "dur": None, "cuts": None, "sheet": None}
    with sync_playwright() as p:
        b = p.chromium.launch(args=browser_args(False))
        g = b.new_page(viewport={"width": 1920, "height": 1080})
        g.on("pageerror", lambda e: out["errors"].append(f"page error: {e}"))
        g.on("console", lambda m: out["errors"].append(f"console {m.type}: {m.text}") if m.type == "error" else None)
        g.goto(page_url(a.folder))
        try:
            g.wait_for_function("window.READY", timeout=60000)
        except Exception:
            out["errors"].append("film did not finish loading (window.READY never set)")
            b.close()
            print(json.dumps(out))
            return 0
        info = g.evaluate("({dur:DUR,cuts:CUTF})")
        out["dur"], out["cuts"] = info["dur"], info["cuts"]
        ims = []
        for t in a.times:
            try:
                r = g.evaluate("t=>{renderAt(t);return document.getElementById('c').toDataURL('image/jpeg',.85)}", t)
                ims.append(r)
            except Exception as e:  # a throw inside draw() at this time
                out["errors"].append(f"render at {t:.2f}s: {str(e)[:300]}")
        b.close()
    if a.sheet and ims and not out["errors"]:
        from PIL import Image

        pics = [Image.open(io.BytesIO(base64.b64decode(r.split(",")[1]))).convert("RGB") for r in ims]
        portrait = pics[0].height > pics[0].width
        cols = 3 if portrait or len(pics) > 4 else 2
        w, h = (360, 640) if portrait else (640, 360)
        sheet = Image.new("RGB", (cols * w, ((len(pics) + cols - 1) // cols) * h))
        for i, im in enumerate(pics):
            sheet.paste(im.resize((w, h)), ((i % cols) * w, (i // cols) * h))
        sheet.save(a.sheet, quality=85)
        out["sheet"] = str(Path(a.sheet).resolve())
    out["ok"] = not out["errors"]
    print(json.dumps(out))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
