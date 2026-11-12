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
def foreground_activity():
    lines=adb("shell","dumpsys","activity","activities").splitlines()
    return next((line.strip() for line in reversed(lines) if "topResumedActivity=" in line),"")
def foreground_is(name):
    # Package/applicationId and Kotlin JVM package are intentionally distinct.
    return (PKG+"/it.riccardopinato.adaptivelandscape."+name) in foreground_activity()

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
def display_size():
    raw=adb("shell","wm","size")
    found=re.findall(r"(\d+)x(\d+)",raw)
    assert found, "Emulator display size missing"
    return tuple(map(int,found[-1]))
def gesture_blank(kind):
    w,h=display_size()
    x,y=w//2,int(h*.76)
    if kind=="menu":
        adb("shell","input","touchscreen","swipe",str(x),str(y),str(x),str(y),"850")
    elif kind=="drawer":
        adb("shell","input","touchscreen","swipe",str(x),str(y),str(x),str(int(h*.30)),"340")
    elif kind=="next":
        adb("shell","input","touchscreen","swipe",str(int(w*.82)),str(y),
            str(int(w*.18)),str(y),"340")
    elif kind=="previous":
        adb("shell","input","touchscreen","swipe",str(int(w*.18)),str(y),
            str(int(w*.82)),str(y),"340")
    else: raise ValueError(kind)
    time.sleep(.9)
def icon_nodes():
    return [n for n in nodes() if n.get("class")=="android.widget.LinearLayout"
            and n.get("content-desc") and n.get("clickable")=="true"
            and not n.get("content-desc","").startswith("Page ")]
def saved_icons():
    return json.loads(pref().get("auto_home_icons_v1","[]"))
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
    original=adb("shell","cmd","role","get-role-holders",ROLE)
    holders=re.findall(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+",original)
    ORIGINAL_HOLDERS=holders
    if PKG in holders: raise AssertionError("Deskland already owns Home before opt-in")
    adb("shell","cmd","role","add-role-holder",ROLE,PKG)
    ROLE_CHANGED=True
    wait("role holder",lambda:PKG in adb("shell","cmd","role","get-role-holders",ROLE))
    ok("shell_home_role","Test shell privileges; not physical user consent")
    adb("shell","input","keyevent","KEYCODE_HOME")
    wait("native Home activity",lambda:foreground_is("AdaptiveHomeActivity"))
    # Wallpaper consent is intentionally one-time; never make it a permanent
    # opaque Home banner. The emulator does NOT approve system wallpaper here.
    try:
        if (node_text("Set live wallpaper") is not None or
            node_text("Select Deskland live wallpaper to enable adaptive sand.") is not None):
            adb("shell","input","keyevent","KEYCODE_BACK")
            time.sleep(.8)
    except (ValueError,RuntimeError): pass
    wait("automatic app layout persisted",lambda:len(saved_icons())>=2)
    initial=saved_icons()
    assert len(initial)<=120 and len({x["id"] for x in initial})==len(initial)
    wait("visible installed app icons",lambda:(v if len(v:=icon_nodes())>=2 else None))
    assert not any(node_text(x) is not None for x in
        ("Add app","Remove app","Change Home launcher","Adaptive Home","Page 1/3")), (
        "Debug controls or page-number text visible on everyday Home")
    ok("clean_home_no_debug_chrome")
    ok("auto_populate_installed_apps",str(len(initial))+" real app shortcuts")
    wait("first page icon geometry",lambda:n_zones(min(40,len(initial))))
    ok("wallpaper_geometry_first_page")
    # Home management is intentionally hidden behind a blank-space long press.
    gesture_blank("menu")
    wait("blank-space manager visible",lambda:node_text("Organize") is not None)
    press("Deskland settings")
    wait("Flutter settings opened separately",lambda:foreground_is("MainActivity"))
    adb("shell","input","keyevent","KEYCODE_HOME")
    wait("Home after settings",lambda:foreground_is("AdaptiveHomeActivity"))
    ok("home_key_returns_to_native_launcher")
    gesture_blank("drawer")
    wait("installed-app drawer opened",lambda:node_text("Apps") is not None or node_text("Search apps") is not None)
    ok("swipe_up_opens_app_drawer")
    # Back once may only close the SearchView keyboard. Close the actual
    # drawer dialog as well before testing horizontal Home gestures.
    for _ in range(3):
        if node_text("Apps") is None and node_text("Search apps") is None: break
        adb("shell","input","keyevent","KEYCODE_BACK")
        time.sleep(.6)
    wait("Home after drawer",lambda:
         foreground_is("AdaptiveHomeActivity") and
         node_text("Apps") is None and node_text("Search apps") is None)
    gesture_blank("next")
    wait("page two preference",lambda:pref().get("auto_home_page_v1")==1)
    next_count=sum(1 for x in initial if x["cell"]//40==1)
    wait("page two coast matches actual icons",lambda:n_zones(next_count))
    gesture_blank("previous")
    wait("first page preference",lambda:pref().get("auto_home_page_v1")==0)
    wait("first page coast restored",lambda:n_zones(min(40,len(initial))))
    ok("swipe_pages_live_geometry")
    # Verify gesture no unwanted app launch and real drag-to-folder.
    icons=wait("native icon tiles",lambda:(v if len(v:=icon_nodes())>=2 else None))
    x,y=point(icons[0]); tx,ty=point(icons[1])
    assert (x,y)!=(tx,ty), "Home icon cells collide"
    adb("shell","input","touchscreen","swipe",str(x),str(y),str(x),str(y),"900")
    wait("stationary long press does not open app",
        lambda:foreground_is("AdaptiveHomeActivity") and len(icon_nodes())>=2)
    ok("long_press_no_unwanted_launch")
    snapshot=pref()["auto_home_icons_v1"]
    adb("shell","logcat","-c",strict=False)
    adb("shell","input","touchscreen","draganddrop",str(x),str(y),str(tx),str(ty),"1250")
    time.sleep(2)
    if not adb("shell","pidof",PKG,strict=False):
        logs=adb("shell","logcat","-d","-v","brief","-t","500",
                 "AndroidRuntime:E","DEBUG:E","ActivityManager:I","*:S",strict=False)
        print("DRAG_CRASH",logs[-6500:],flush=True)
        raise AssertionError("Home process terminated during icon drag")
    wait("drag persisted layout change",lambda:pref().get("auto_home_icons_v1")!=snapshot)
    def folder_created():
        data=json.loads(pref().get("auto_home_folders_v1","[]"))
        return len(data)>=1 and len(data[0]["members"])>=2
    wait("folder persisted after long drag",folder_created)
    wait("coast updates when icons become folder",
        lambda:n_zones(min(40,len(initial))-1))
    ok("icon_drag_to_folder_live_geometry")
    adb("shell","am","force-stop",PKG)
    adb("shell","input","keyevent","KEYCODE_HOME")
    wait("Home restored after process kill",lambda:foreground_is("AdaptiveHomeActivity"))
    assert folder_created(), "Folder was lost after process restart"
    ok("home_data_survives_process_restart")
    if holders:
        adb("shell","cmd","role","add-role-holder",ROLE,holders[0])
        wait("OEM Home role restored",lambda:holders[0] in adb("shell","cmd","role","get-role-holders",ROLE))
        ok("original_home_role_restored")
    else:
        adb("shell","cmd","role","remove-role-holder",ROLE,PKG)
        steps["original_home_role_restored"]="NOT VERIFIED: emulator had no original Home role holder"
    assert PKG not in adb("shell","cmd","role","get-role-holders",ROLE)
    adb("shell","input","keyevent","KEYCODE_HOME")
    ok("deskland_role_revoked")
    for key in ("live_wallpaper_consent","physical_device","water_photorealism","battery_thermal"):
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
    OUT.write_text(json.dumps({"base_candidate":"b0fe22f7fdaba0b2a51a2c9ca6f081ddaa6f738d","built_source_sha":os.getenv("QA_SOURCE_SHA",os.getenv("GITHUB_SHA")),"test_harness_sha":os.getenv("QA_SOURCE_SHA",os.getenv("GITHUB_SHA")),"apk_sha256":digest,"evidence":"Disposable API 35 emulator only; no photos/device identifiers","checks":steps},indent=2)+"\n")
    print("Evidence record:",OUT,flush=True)
