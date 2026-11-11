#!/usr/bin/env python3
"""Deskland M1: disposable emulator runtime test, never physical-device certification."""
import hashlib, json, os, re, subprocess, sys, time, xml.etree.ElementTree as ET
from pathlib import Path
PKG="it.riccardopinato.adaptive_landscape"
ROLE="android.app.role.HOME"
APK=Path(sys.argv[1])
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
            if result: return result
        except (ValueError,RuntimeError,ET.ParseError,KeyError,AssertionError): pass
        time.sleep(1)
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
    global digest
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
    assert holders and PKG not in holders,"Original launcher role not identifiable"
    adb("shell","cmd","role","add-role-holder",ROLE,PKG)
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
    press("Page 3/3"); wait("first page",lambda:node_text("Page 1/3"))
    wait("page1 recovered",lambda:n_zones(base)); ok("page_roundtrip")
    press("Add app"); tap(wait("app picker",first_row))
    wait("app added",lambda:n_zones(base+1)); ok("add_app_new_sand")
    press("Remove app"); tap(wait("remove picker",first_row))
    wait("app removed",lambda:n_zones(base)); ok("remove_app_erases_sand")
    controls=("Widgets","Apps","Add app","Remove app","Set live wallpaper","Change Home launcher","Adaptive Home")
    def icon():
        return next((n for n in nodes() if n.get("content-desc") and n.get("clickable")=="true" and not n.get("content-desc").startswith(controls) and not n.get("content-desc").startswith("Page ")),None)
    x,y=point(wait("draggable app icon",icon))
    dimensions=re.search(r"(\d+)x(\d+)",adb("shell","wm","size"))
    assert dimensions
    width=int(dimensions.group(1)); step=round(width/5)
    destination=x+step if x+step<width*.90 else x-step
    saved=pref()["auto_home_icons_v1"]
    adb("shell","input","motionevent","DOWN",str(x),str(y))
    try:
        time.sleep(.9)
        adb("shell","input","motionevent","MOVE",str(destination),str(y))
        time.sleep(.3)
        adb("shell","input","motionevent","UP",str(destination),str(y))
    finally:
        adb("shell","input","motionevent","UP",str(destination),str(y),strict=False)
    wait("drag writes new layout",lambda:pref().get("auto_home_icons_v1")!=saved)
    ok("long_press_drag")
    adb("shell","am","force-stop",PKG)
    adb("shell","input","keyevent","KEYCODE_HOME")
    wait("Home after process kill",lambda:node_text("Add app"))
    wait("geometry after process kill",lambda:n_zones(base))
    ok("process_restart")
    adb("shell","cmd","role","add-role-holder",ROLE,holders[0])
    wait("original Home restored",lambda:holders[0] in adb("shell","cmd","role","get-role-holders",ROLE))
    assert PKG not in adb("shell","cmd","role","get-role-holders",ROLE)
    adb("shell","input","keyevent","KEYCODE_HOME")
    assert node_text("Add app") is None
    ok("original_home_rollback")
    for key in ("live_wallpaper_consent","physical_device","aesthetic_quality","battery_thermal"):
        steps[key]="NOT VERIFIED"
try: main()
except Exception as exc:
    steps["FAIL"]=str(exc)
    print("FAIL",exc,file=sys.stderr,flush=True)
    sys.exit(1)
finally:
    OUT.write_text(json.dumps({"source_head":"6f5d1fb60b6b8fb2e35c3c396f11aed198cb08fd","source_run":42947060839,"apk_sha256":digest,"evidence":"Disposable API 35 emulator only; no photos/device identifiers","checks":steps},indent=2)+"\n")
    print("Evidence record:",OUT,flush=True)
