#!/usr/bin/env python3
"""Deskland M1: disposable emulator runtime test, never physical-device certification."""
import hashlib, json, os, re, subprocess, sys, time, xml.etree.ElementTree as ET
from pathlib import Path
PKG="it.riccardopinato.adaptive_landscape"
ROLE="android.app.role.HOME"
APK=Path(sys.argv[1])
ROLE_CHANGED=False
ORIGINAL_HOLDERS=[]
OUT=Path(os.getenv("RUNNER_TEMP","/tmp"))/"deskland-m1-evidence.json"
steps={}; digest=None
def adb(*a, strict=True):
    r=subprocess.run(["adb",*a],capture_output=True,text=True,timeout=75)
    if strict and r.returncode: raise RuntimeError("ADB failed "+str(a)+" "+r.stderr[-280:])
    return r.stdout.strip()
def ok(k,v="PASS"):
    steps[k]=v; print("PASS",k,v,flush=True)
def wait(label,pred):
    for i in range(30):
        try:
            result=pred()
            if isinstance(result, ET.Element) or result: return result
        except (ValueError,RuntimeError,ET.ParseError,KeyError,AssertionError): pass
        time.sleep(1)
    try:
        ui_labels=[n.get("text") for n in nodes() if n.get("text","").startswith("Page ")]
        current=ET.fromstring(adb("exec-out","run-as",PKG,"cat","shared_prefs/adaptive_landscape.xml"))
        pages=[n.get("value") for n in current if n.get("name")=="auto_home_page_v1"]
        activity=[l.strip() for l in adb("shell","dumpsys","activity","activities").splitlines() if "topResumedActivity=" in l][-2:]
        current_zones=zones()
        roles=adb("shell","cmd","role","get-role-holders",ROLE,strict=False)
        process=bool(adb("shell","pidof",PKG,strict=False))
        print("SAFE_DIAG",json.dumps({"test":label,"page_labels":ui_labels,"stored_page":pages,"zones_count":len(current_zones),"role_still_held":PKG in roles,"process_alive":process,"activity":activity}),flush=True)
    except Exception as err:
        print("SAFE_DIAG_UNAVAILABLE",type(err).__name__,flush=True)
    raise AssertionError("Timed out: "+label)
def nodes():
    x=adb("exec-out","uiautomator","dump","/dev/tty")
    a=x.find("<?xml"); b=x.find("</hierarchy>",a)
    if a<0 or b<0: raise ValueError("No UI hierarchy")
    return list(ET.fromstring(x[a:b+12]).iter("node"))
def node_text(s):
    return next((n for n in nodes() if n.get("text")==s),None)
def point(n):
    m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",n.get("bounds",""))
    if not m: raise ValueError("No bounds")
    l,t,r,b=map(int,m.groups())
    return (l+r)//2,(t+b)//2
def tap(n):
    x,y=point(n); adb("shell","input","tap",str(x),str(y)); time.sleep(.8)
def press(s): tap(wait(s,lambda:node_text(s)))
def first_row():
    for n in nodes():
        if n.get("class")=="android.widget.ListView":
            return next((c for c in n.iter("node") if c is not n and c.get("text")),None)
    return None
def pref():
    root=ET.fromstring(adb("exec-out","run-as",PKG,"cat","shared_prefs/adaptive_landscape.xml"))
    d={}
    for n in root:
        if n.tag=="string": d[n.get("name")]=n.text or ""
        elif n.tag in ("int","long"): d[n.get("name")]=int(n.get("value"))
    return d
def zones():
    j=json.loads(pref()["auto_home_config_v1"])
    assert j["schema"]==2 and j["occupied"]==[]
    assert all(all(0<=float(z[k])<=1 for k in ("x","y","width","height")) for z in j["zones"])
    return j["zones"]
def n_zones(n):
    z=zones()
    return len(z)==n
def main():
    global digest, ROLE_CHANGED, ORIGINAL_HOLDERS
    assert APK.is_file()
    digest=hashlib.sha256(APK.read_bytes()).hexdigest()
    ok("apk_sha256",digest)
    model=adb("shell","getprop","ro.product.model").lower()
    assert adb("shell","getprop","ro.build.version.sdk")=="35" and ("sdk" in model or "emulator" in model), "Emulator only!"
    ok("isolated_emulator")
    adb("install","-r",str(APK))
    assert PKG in adb("shell","pm","list","packages",PKG)
    ok("clean_install")
    adb("shell","am","start","-W","-n",PKG+"/it.riccardopinato.adaptivelandscape.MainActivity")
    wait("editor starts",lambda:adb("shell","pidof",PKG,strict=False))
    ok("editor_start")
    original=adb("shell","cmd","role","get-role-holders",ROLE)
    holders=re.findall(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+",original)
    ORIGINAL_HOLDERS=holders
    if PKG in holders:
        raise AssertionError("Deskland unexpectedly owns Home before assignment")
    if not holders:
        steps["original_launcher_role"]="NOT VERIFIED: system had no original role holder"
    else:
        ok("original_launcher_role_present")
    adb("shell","cmd","role","add-role-holder",ROLE,PKG)
    ROLE_CHANGED=True
    wait("role holder",lambda:PKG in adb("shell","cmd","role","get-role-holders",ROLE))
    ok("shell_home_role","Test privilege; not real consent dialog")
    adb("shell","input","keyevent","KEYCODE_HOME")
    wait("Home screen",lambda:node_text("Add app"))
    ok("home_visible")
    z=wait("icon geometry",lambda:(v if (v:=zones()) else None))
    base=len(z)
    ok("live_icon_geometry",str(base)+" zones")
    press("Page 1/3"); wait("second page",lambda:node_text("Page 2/3"))
    wait("page two clears sand",lambda:n_zones(0)); ok("empty_page_zero_sand")
    press("Page 2/3"); wait("third page",lambda:node_text("Page 3/3"))
    press("Page 3/3"); wait("first page persisted",lambda:pref().get("auto_home_page_v1")==0)
    wait("page1 recovered",lambda:n_zones(base)); ok("page_roundtrip")
    # An emulator may have only six launchable packages: remove first, then re-add.
    press("Remove app"); tap(wait("remove picker",first_row))
    wait("app removed",lambda:n_zones(base-1)); ok("remove_app_erases_sand")
    press("Add app"); tap(wait("app picker",first_row))
    wait("app added",lambda:n_zones(base)); ok("add_app_new_sand")
    controls=("Widgets","Apps","Add app","Remove app","Set live wallpaper","Change Home launcher","Adaptive Home")
    def icon():
        # Native app tiles are LinearLayout. A description-only search can
        # accidentally select a system navigation control outside the Home.
        return next((n for n in nodes() if n.get("class")=="android.widget.LinearLayout" and n.get("content-desc") and n.get("clickable")=="true" and not n.get("content-desc").startswith(controls) and not n.get("content-desc").startswith("Page ")),None)
    target=wait("draggable app icon",icon)
    x,y=point(target)
    assert target.get("class")=="android.widget.LinearLayout"
    assert PKG in adb("shell","cmd","role","get-role-holders",ROLE)
    ok("tile_targeted_for_drag","native clickable tile")
    # A stationary long-press is not a tap: must not launch an app.
    adb("shell","input","touchscreen","swipe",str(x),str(y),str(x),str(y),"900")
    wait("Home retained after stationary long press",lambda:node_text("Add app"))
    ok("long_press_no_unwanted_launch")
    dimensions=re.search(r"(\d+)x(\d+)",adb("shell","wm","size"))
    assert dimensions
    width=int(dimensions.group(1)); step=round(width/5)
    destination=x+step if x+step<width*.90 else x-step
    saved=pref()["auto_home_icons_v1"]
    # One Android InputShellCommand process keeps a single touch pointer
    # throughout DOWN -> long press -> MOVE -> UP. Separate adb motionevent
    # invocations do NOT reliably maintain the pointer across processes.
    # The 1250ms duration exceeds ViewConfiguration.longPressTimeout.
    adb("shell","input","touchscreen","draganddrop",str(x),str(y),str(destination),str(y),"1250")
    wait("Home retained after drag",lambda:node_text("Add app"))
    assert PKG in adb("shell","cmd","role","get-role-holders",ROLE),"Deskland lost HOME role"
    wait("drag writes new layout",lambda:pref().get("auto_home_icons_v1")!=saved)
    wait("drag preserves geometry",lambda:n_zones(base))
    ok("long_press_drag")
    adb("shell","am","force-stop",PKG)
    adb("shell","input","keyevent","KEYCODE_HOME")
    wait("Home after process kill",lambda:node_text("Add app"))
    wait("geometry after process kill",lambda:n_zones(base))
    ok("process_restart")
    if holders:
        adb("shell","cmd","role","add-role-holder",ROLE,holders[0])
        wait("original Home restored",lambda:holders[0] in adb("shell","cmd","role","get-role-holders",ROLE))
        ok("original_home_role_restored")
    else:
        adb("shell","cmd","role","remove-role-holder",ROLE,PKG)
        steps["original_home_role_restored"]="NOT VERIFIED: emulator initially had no role holder"
    assert PKG not in adb("shell","cmd","role","get-role-holders",ROLE)
    adb("shell","input","keyevent","KEYCODE_HOME")
    def stock_home_selected():
        if node_text("Add app") is not None: return False
        return PKG not in adb("shell","cmd","role","get-role-holders",ROLE)
    wait("stock Home in foreground after restore",stock_home_selected)
    ok("deskland_role_revoked")
    for key in ("live_wallpaper_consent","physical_device","aesthetic_quality","battery_thermal"):
        steps[key]="NOT VERIFIED"
try: main()
except Exception as exc:
    steps["FAIL"]=str(exc)
    print("FAIL",exc,file=sys.stderr,flush=True)
    sys.exit(1)
finally:
    # Always undo the emulator-only HOME role mutation, even on failures.
    if ROLE_CHANGED:
        try:
            device=adb("shell","getprop","ro.product.model",strict=False).lower()
            if "sdk" in device or "emulator" in device:
                if ORIGINAL_HOLDERS:
                    adb("shell","cmd","role","add-role-holder",ROLE,ORIGINAL_HOLDERS[0],strict=False)
                else:
                    adb("shell","cmd","role","remove-role-holder",ROLE,PKG,strict=False)
                steps["teardown_home_role"]="attempted (ephemeral emulator)"
        except Exception as err:
            steps["teardown_home_role"]="FAILED: "+type(err).__name__
    OUT.write_text(json.dumps({"base_candidate":"6f5d1fb60b6b8fb2e35c3c396f11aed198cb08fd","built_source_sha":os.getenv("QA_SOURCE_SHA",os.getenv("GITHUB_SHA")),"apk_sha256":digest,"evidence":"Disposable API 35 emulator only; no photos/device identifiers","checks":steps},indent=2)+"\n")
    print("Evidence record:",OUT,flush=True)
