"""
Tap the first of the given texts that is on the emulator's screen (found with uiautomator, so it does not depend on
screen size, a promo Chrome shows on first run, or the page layout).

    python scripts/ui_tap.py emulator-5554 "No thanks" "Allow"      -> prints what it tapped, exit 0; exit 1 if none found
"""
import os, re, subprocess, sys
import xml.etree.ElementTree as ET

serial, wanted = sys.argv[1], sys.argv[2:]
if not serial.startswith("emulator-"):
    sys.exit("refusing: %s is not an emulator" % serial)
sdk = os.environ.get("ANDROID_SDK_ROOT") or os.path.join(os.environ["LOCALAPPDATA"], "Android", "Sdk")
adb = [os.path.join(sdk, "platform-tools", "adb.exe"), "-s", serial]


def run(*args):
    return subprocess.run(adb + list(args), capture_output=True).stdout.decode("utf-8", "replace").replace("\r", "")


run("shell", "uiautomator", "dump", "/sdcard/ui.xml")
xml = run("exec-out", "cat", "/sdcard/ui.xml")
if "<hierarchy" not in xml:
    sys.exit(1)
root = ET.fromstring(xml)
for text in wanted:
    for node in root.iter("node"):
        if text in (node.get("text"), node.get("content-desc")):
            left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
            run("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
            print("tapped", text)
            sys.exit(0)
sys.exit(1)
