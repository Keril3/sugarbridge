#!/usr/bin/env python3
"""
Walks through the optimized "smoke" build on an emulator, from outside. Instrumented tests can't run against code
that R8 optimized, and what R8 can break is exactly what they would not see: anything found by name at run time.
So this goes through every such place in the real app: Health Connect, WorkManager, the foreground service, the
receivers, the WebView's bridge, fonts, the Keystore. An emulator with Android 14 works best.

    ./gradlew assembleSmoke -Psugarbridge.r8
    python3 tools/smoke_test.py app/build/outputs/apk/smoke/app-smoke.apk

Set ANDROID_SERIAL if more than one device is connected, and ADB if adb is not on the PATH.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB", "adb")
PKG = "com.sugarbridge.app"
STRINGS = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "values", "strings.xml")
TEXT = {
    e.get("name"): (e.text or "").replace("\\'", "'")
    for e in ET.parse(STRINGS).getroot()
    if e.tag == "string"
}
steps_done = 0


def adb(*args, timeout=60, check=False):
    r = subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
    if check and r.returncode:
        raise SystemExit(f"adb {' '.join(args)} failed: {r.stdout} {r.stderr}")
    return r.stdout


def shell(*args, timeout=60):
    return adb("shell", *args, timeout=timeout)


def nodes():
    """The screen as a list of (text, description, centre)."""
    shell("uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/ui.xml")
    start = xml.find("<hierarchy")
    if start < 0:
        return []
    out = []
    try:
        root = ET.fromstring(xml[start:])
    except ET.ParseError:
        return []
    for n in root.iter("node"):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
        if m:
            a, b, c, d = map(int, m.groups())
            out.append((n.get("text", ""), n.get("content-desc", ""), ((a + c) // 2, (b + d) // 2)))
    return out


def find(text, contains=False):
    for t, d, centre in nodes():
        if (text in t or text in d) if contains else (t == text or d == text):
            return centre
    return None


def wait(text, seconds=25, contains=False):
    deadline = time.time() + seconds
    while time.time() < deadline:
        at = find(text, contains)
        if at:
            return at
        time.sleep(1)
    fail(f"not on the screen: {text!r}")


def tap(text, seconds=25, contains=False):
    x, y = wait(text, seconds, contains)
    shell("input", "tap", str(x), str(y))
    time.sleep(1.5)


def down(times=1):
    for _ in range(times):
        shell("input", "swipe", "540", "1700", "540", "800", "300")
        time.sleep(0.8)


def scroll_to(text, contains=False, swipes=8, lowest=1950):
    for _ in range(swipes):
        at = find(text, contains)
        if at and 200 < at[1] < lowest:
            return at
        down()
    fail(f"not found by scrolling: {text!r}")


def scroll_tap(text, contains=False, lowest=1950):
    x, y = scroll_to(text, contains, lowest=lowest)
    shell("input", "tap", str(x), str(y))
    time.sleep(1.5)


def crashes():
    log = adb("logcat", "-d", "-v", "brief", timeout=120)
    bad = re.compile(r"FATAL EXCEPTION|NoSuchMethodError|NoClassDefFoundError|ClassNotFoundException|VerifyError|UnsatisfiedLinkError|AbstractMethodError|IncompatibleClassChangeError")
    lines = log.splitlines()
    found = []
    for i, line in enumerate(lines):
        if bad.search(line) and (PKG in "\n".join(lines[max(0, i - 2) : i + 4])):
            found.append("\n".join(lines[i : i + 12]))
    return found


def fail(why):
    adb("exec-out", "screencap", "-p")
    print(f"FAILED after {steps_done} steps: {why}")
    for c in crashes():
        print(c[:1500])
    sys.exit(1)


def step(name):
    global steps_done
    problems = crashes()
    if problems:
        fail(f"a crash before: {name}\n" + problems[0][:1500])
    steps_done += 1
    print(f"[{steps_done}] {name}", flush=True)


def main():
    apk = sys.argv[1]
    step("install")
    adb("uninstall", PKG)
    print(adb("install", "-r", apk, timeout=240).strip().splitlines()[-1])
    for p in ("android.permission.POST_NOTIFICATIONS", "android.permission.health.WRITE_BLOOD_GLUCOSE"):
        shell("pm", "grant", PKG, p)
    shell("dumpsys", "deviceidle", "whitelist", f"+{PKG}")
    adb("logcat", "-c")

    step("the first start: language")
    shell("am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    wait("SugarBridge", 30)
    scroll_tap(TEXT["continue_button"], lowest=2300)   # this screen has no tab bar under it

    step("sign in: the Keystore and the client")
    tap(TEXT["login_email"])
    shell("input", "text", "smoke@example.com")
    shell("input", "keyevent", "KEYCODE_BACK")
    time.sleep(1)
    tap(TEXT["login_password"])
    shell("input", "text", "secret1")
    shell("input", "keyevent", "KEYCODE_BACK")
    time.sleep(1)
    tap(TEXT["login_button"])
    wait("tenshi.exe", 40)

    step("the first sync: Health Connect")
    wait(TEXT["home_chart_title"], 40)

    step("tabs of the app")
    tap(TEXT["tab_history"])
    tap(TEXT["tab_settings"])

    step("look: themes and all fonts")
    tap(TEXT["settings_tab_look"])
    tap(TEXT["theme_neutral"])
    tap(TEXT["theme_system"])
    for font in ("font_pixel", "font_sans", "font_serif", "font_system"):
        at = find(TEXT[font]) or scroll_to(TEXT[font])
        shell("input", "tap", str(at[0]), str(at[1]))
        time.sleep(2)

    step("developer tools")
    tap(TEXT["settings_tab_app"])
    scroll_tap(TEXT["settings_developer_switch"])
    scroll_tap(TEXT["settings_developer_open"])
    wait(TEXT["dev_title"])
    down(2)
    time.sleep(2)
    shell("input", "keyevent", "KEYCODE_BACK")
    time.sleep(1.5)

    step("minute mode: the foreground service and its notification")
    tap(TEXT["settings_tab_sync"])
    scroll_tap(TEXT["settings_live_switch"])
    time.sleep(6)
    if "LiveSyncService" not in shell("dumpsys", "activity", "services", PKG):
        fail("minute mode's service is not running")
    if PKG not in shell("dumpsys", "notification"):
        fail("minute mode's notification is not there")
    tap(TEXT["close"])   # the notice that background sync is off covers half of the screen

    step("an export opened from another app: the CSV reader")
    shell("am", "start", "-a", "android.intent.action.VIEW", "-d", f"content://{PKG}.smoke/export.csv", "-t", "text/csv", "-n", f"{PKG}/.MainActivity")
    wait(TEXT["history_title"], 40)

    step("writing the history to Health Connect")
    tap(TEXT["history_write"])
    wait(TEXT["history_select_prompt"], 60)   # every day written, none left selected

    step("the LibreView window: WebView and its bridge")
    time.sleep(4)
    shell("input", "keyevent", "KEYCODE_BACK")
    time.sleep(1.5)
    tap(TEXT["history_open_libreview"])
    wait(TEXT["merge_title"], 40)
    tap(TEXT["cancel"])

    step("a reading from another app: the receiver")
    tap(TEXT["tab_settings"])
    tap(TEXT["settings_tab_sync"])
    scroll_tap(TEXT["local_broadcasts"])
    now_ms = str(int(time.time() * 1000) - 20_000)
    shell(
        "am", "broadcast", "-n", f"{PKG}/.local.LocalGlucoseReceiver", "-a", "glucodata.Minute",
        "--ei", "glucodata.Minute.mgdl", "123", "--el", "glucodata.Minute.Time", now_ms, "--ef", "glucodata.Minute.Rate", "0.5",
    )
    scroll_to("Juggluco", contains=True)

    step("the background job: WorkManager finds its worker")
    # with minute mode off the job comes back and runs at once; forced later, WorkManager would put it off until due
    tap(TEXT["settings_tab_app"])   # and back: a tab opens at its top
    tap(TEXT["settings_tab_sync"])
    x, y = scroll_to(TEXT["settings_live_switch"])
    adb("logcat", "-c")
    shell("input", "tap", str(x), str(y))
    deadline = time.time() + 90
    while time.time() < deadline and "written:" not in adb("logcat", "-d", "-s", "SugarSync:I", timeout=60):
        time.sleep(2)
    if "written:" not in adb("logcat", "-d", "-s", "SugarSync:I", timeout=60):
        fail("the background job didn't sync")

    tap(TEXT["tab_now"])
    shell("am", "force-stop", PKG)
    shell("am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    wait("tenshi.exe", 40)

    step("signed in after a restart, the chart and the estimate come back")
    wait(TEXT["home_chart_title"], 30)
    scroll_to(TEXT["gmi_title"])   # reads SugarBridge's records back from Health Connect
    print("all steps passed" if not crashes() else "CRASHES:\n" + "\n".join(crashes()[:2]))
    sys.exit(0 if not crashes() else 1)


if __name__ == "__main__":
    main()
