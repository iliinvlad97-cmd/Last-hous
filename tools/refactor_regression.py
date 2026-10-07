#!/usr/bin/env python3
"""Compare this refactor with its Git baseline using deterministic JVM Android doubles.

Requires Python 3 and JDK 21 on PATH. No Android runtime or third-party Python modules.
Canvas command equivalence is checked, not real device pixels or Android integration.
"""
import difflib
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
BASELINE = "42854919b6e9dba7b55981ec9f4db86b9e9e0ec4"
JAVA_PATH = "app/src/main/java/com/lastdom/game"

STUBS = {
    "android/content/SharedPreferences.java": r'''
package android.content;
public interface SharedPreferences {
 boolean contains(String k); int getInt(String k,int d); boolean getBoolean(String k,boolean d); String getString(String k,String d); Editor edit();
 interface Editor {Editor putInt(String k,int v); Editor putBoolean(String k,boolean v); Editor putString(String k,String v); void apply();}
}
''',
    "android/content/MemoryPreferences.java": r'''
package android.content;
import java.util.*;
public class MemoryPreferences implements SharedPreferences {
 public final TreeMap<String,Object> values=new TreeMap<>();
 public boolean contains(String k){return values.containsKey(k);}
 public int getInt(String k,int d){return (Integer)values.getOrDefault(k,d);}
 public boolean getBoolean(String k,boolean d){return (Boolean)values.getOrDefault(k,d);}
 public String getString(String k,String d){return (String)values.getOrDefault(k,d);}
 public Editor edit(){return new Editor(){
  final Map<String,Object> pending=new HashMap<>();
  public Editor putInt(String k,int v){pending.put(k,v);return this;}
  public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}
  public Editor putString(String k,String v){pending.put(k,v);return this;}
  public void apply(){values.putAll(pending);}
 };}
}
''',
    "android/content/Context.java": r'''
package android.content;
public class Context {
 public static MemoryPreferences preferences=new MemoryPreferences();
 public static String preferenceName;
 public SharedPreferences getSharedPreferences(String name,int mode){preferenceName=name;return preferences;}
 public android.content.res.Resources getResources(){return new android.content.res.Resources();}
}
''',
    "android/content/res/Resources.java": "package android.content.res; public class Resources {}",
    "android/app/Activity.java": r'''
package android.app;
public class Activity extends android.content.Context {
 public void onCreate(android.os.Bundle b){} protected void onPause(){}
 public android.view.Window getWindow(){return new android.view.Window();}
 public void setContentView(android.view.View v){}
}
''',
    "android/os/Bundle.java": "package android.os; public class Bundle {}",
    "android/os/Handler.java": r'''
package android.os;
public class Handler {
 public static final java.util.List<Long> delays=new java.util.ArrayList<>();
 public boolean postDelayed(Runnable r,long delay){delays.add(delay);return true;}
}
''',
    "android/view/View.java": r'''
package android.view;
public class View {
 public static int width=420,height=840;
 private final android.content.Context context;
 public View(android.content.Context c){context=c;}
 public android.content.res.Resources getResources(){return context.getResources();}
 public int getWidth(){return width;} public int getHeight(){return height;}
 public void invalidate(){} public void postInvalidateDelayed(long delay){}
 protected void onDraw(android.graphics.Canvas c){}
 public boolean onTouchEvent(MotionEvent e){return false;}
}
''',
    "android/view/Window.java": "package android.view; public class Window {public void setFlags(int a,int b){}}",
    "android/view/WindowManager.java": "package android.view; public class WindowManager {public static class LayoutParams {public static final int FLAG_FULLSCREEN=1024;}}",
    "android/view/MotionEvent.java": r'''
package android.view;
public class MotionEvent {
 public static final int ACTION_UP=1,ACTION_DOWN=0;
 private final float x,y; private final int action;
 public MotionEvent(float x,float y,int action){this.x=x;this.y=y;this.action=action;}
 public int getAction(){return action;} public float getX(){return x;} public float getY(){return y;}
}
''',
    "android/text/TextUtils.java": r'''
package android.text;
public class TextUtils {public static String join(CharSequence separator,Iterable<?> values){java.util.List<String> strings=new java.util.ArrayList<>();for(Object value:values)strings.add(value.toString());return String.join(separator,strings);}}
''',
    "android/graphics/Color.java": r'''
package android.graphics;
public class Color {
 public static final int WHITE=0xffffffff,TRANSPARENT=0;
 public static int rgb(int r,int g,int b){return argb(255,r,g,b);}
 public static int argb(int a,int r,int g,int b){return (a<<24)|(r<<16)|(g<<8)|b;}
}
''',
    "android/graphics/Paint.java": r'''
package android.graphics;
public class Paint {
 public enum Style {FILL,STROKE} public enum Cap {BUTT,ROUND}
 private Style style=Style.FILL; private Cap cap=Cap.BUTT; private int color=0xff000000;
 private float width=0,size=0; private Typeface type; private Shader shader;
 public Paint(int flags){} public void setStyle(Style value){style=value;} public void setStrokeCap(Cap value){cap=value;}
 public void setColor(int value){color=value;} public void setStrokeWidth(float value){width=value;}
 public void setTextSize(float value){size=value;} public void setTypeface(Typeface value){type=value;}
 public void setShader(Shader value){shader=value;} public float measureText(String s){return s.length()*size*.5f;}
 public String toString(){return style+":"+cap+":"+color+":"+width+":"+size+":"+type+":"+shader;}
}
''',
    "android/graphics/Typeface.java": r'''
package android.graphics;
public class Typeface {public static final int NORMAL=0,BOLD=1;public static final Typeface DEFAULT_BOLD=create("default",BOLD);private final String name;private final int style;private Typeface(String n,int s){name=n;style=s;}public static Typeface create(String n,int s){return new Typeface(n,s);}public String toString(){return name+":"+style;}}
''',
    "android/graphics/Bitmap.java": r'''
package android.graphics;
public class Bitmap {private final int id;public Bitmap(int id){this.id=id;}public int getWidth(){return 1024;}public int getHeight(){return 1536;}public String toString(){return "bitmap:"+id;}}
''',
    "android/graphics/BitmapFactory.java": "package android.graphics; public class BitmapFactory {public static Bitmap decodeResource(android.content.res.Resources r,int id){return new Bitmap(id);}}",
    "android/graphics/Shader.java": "package android.graphics; public class Shader {public enum TileMode {CLAMP}}",
    "android/graphics/RadialGradient.java": r'''
package android.graphics;
public class RadialGradient extends Shader {private final String args;public RadialGradient(Object... args){this.args=java.util.Arrays.toString(args);}public String toString(){return "gradient:"+args;}}
''',
    "android/graphics/Path.java": r'''
package android.graphics;
public class Path {private final java.util.List<String> steps=new java.util.ArrayList<>();public void moveTo(float x,float y){steps.add("M"+x+","+y);}public void lineTo(float x,float y){steps.add("L"+x+","+y);}public void close(){steps.add("Z");}public String toString(){return steps.toString();}}
''',
    "test/TestTime.java": "package test; public class TestTime {public static long now(){return 123456789L;}}",
}
for rect in ("Rect", "RectF"):
    STUBS[f"android/graphics/{rect}.java"] = (
        f"package android.graphics; public class {rect} {{"
        "private final String args; public " + rect
        + "(Object... args){this.args=java.util.Arrays.toString(args);}public String toString(){return args;}}"
    )
DRAW_METHODS = "drawColor drawText drawRect drawRoundRect drawBitmap drawCircle drawOval drawArc drawLine drawPath".split()
STUBS["android/graphics/Canvas.java"] = (
    "package android.graphics; public class Canvas {"
    "public final java.util.List<String> commands=new java.util.ArrayList<>();"
    + "".join(
        f'public void {name}(Object... args){{commands.add("{name}"+java.util.Arrays.toString(args));}}'
        for name in DRAW_METHODS
    ) + "}"
)

PROBE = r'''
package com.lastdom.game;
import java.lang.reflect.*;
import java.util.*;
import android.content.*;
import android.graphics.*;
import android.view.*;

public class RegressionProbe {
 static Object view,game;
 static int checks=0;
 static Field field(Object object,String name) throws Exception {
  for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
  throw new NoSuchFieldException(name);
 }
 static Object get(Object object,String name) throws Exception {return field(object,name).get(object);}
 static void set(Object object,String name,Object value) throws Exception {field(object,name).set(object,value);}
 static Object call(Object object,String name,Object... args) throws Exception {
  for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);return m.invoke(object,args);}
  throw new NoSuchMethodException(name);
 }
 static Object geometry(String name,Object... args) throws Exception {
  try{return call(view,name,args);}catch(NoSuchMethodException e){Class<?> c=Class.forName("com.lastdom.game.ShelterGeometry");for(Method m:c.getDeclaredMethods())if(m.getName().equals(name)){m.setAccessible(true);return m.invoke(null,args);}throw e;}
 }
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
 static void fresh() throws Exception {
  Context.preferences=new MemoryPreferences();android.os.Handler.delays.clear();
  MainActivity activity=new MainActivity();activity.onCreate(null);view=get(activity,"view");
  try{game=get(view,"game");}catch(NoSuchFieldException e){game=view;}
  ((Random)get(game,"rnd")).setSeed(407);
  require("save_v02".equals(Context.preferenceName),"save namespace");
  require(android.os.Handler.delays.equals(Arrays.asList(1000L)),"initial tick interval");
 }
 static List<?> people() throws Exception {return (List<?>)get(game,"people");}
 static String canonical(Object value) throws Exception {
  if(value==null)return "null";
  if(value.getClass().isArray()){List<String> r=new ArrayList<>();for(int i=0;i<Array.getLength(value);i++)r.add(canonical(Array.get(value,i)));return r.toString();}
  if(value instanceof Iterable){List<String> r=new ArrayList<>();for(Object v:(Iterable<?>)value)r.add(canonical(v));return r.toString();}
  if(value.getClass().getName().startsWith("com.lastdom.game.")){TreeMap<String,String> r=new TreeMap<>();for(Field f:value.getClass().getDeclaredFields())if(!f.isSynthetic()){f.setAccessible(true);r.put(f.getName(),canonical(f.get(value)));}return r.toString();}
  return String.valueOf(value);
 }
 static String hash(String value) throws Exception {return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
 static void snapshot(String label) throws Exception {
  call(game,"save");TreeMap<String,String> state=new TreeMap<>();
  String names="day gameMinute speed food water power mats threat shelter selected selectedRoom screen overlay buildingRoom buildRemaining incidentRoom paused event gameOver jobMenu eventTitle eventText eventChoices expeditionPerson expeditionLocation expeditionRemaining selectedLocation expeditionEvent roomLevels roomCondition residentX residentY residentVisualRoom residentVisualReady people log locations";
  for(String name:names.split(" "))state.put(name,canonical(get(game,name)));
  state.put("saved",Context.preferences.values.toString());
  System.out.println(label+":"+hash(state.toString()));
 }
 static void draw(String label) throws Exception {Canvas c=new Canvas();call(view,"onDraw",c);require(!c.commands.isEmpty(),"draw executed");System.out.println(label+":"+hash(c.commands.toString()));}
 static void tap(float x,float y) throws Exception {float scale=(Float)get(view,"scale");require((Boolean)call(view,"onTouchEvent",new MotionEvent(x*scale,y*scale,MotionEvent.ACTION_UP)),"touch consumed");}
 static void tick() throws Exception {((Runnable)get(view,"tick")).run();require(android.os.Handler.delays.get(android.os.Handler.delays.size()-1)==1000L,"rescheduled tick interval");}
 static class FixedRandom extends Random {final int value;FixedRandom(int value){this.value=value;}@Override public int nextInt(int bound){return value%bound;}}
 public static void main(String[] args) throws Exception {
  Locale.setDefault(Locale.US);
  fresh();snapshot("default-save");draw("default-canvas");
  for(int speed:new int[]{1,2,4}){set(game,"speed",speed);int before=(Integer)get(game,"gameMinute");tick();require((Integer)get(game,"gameMinute")==before+speed,"tick speed "+speed);snapshot("tick-"+speed);}
  for(String flag:new String[]{"paused","event","gameOver"}){set(game,flag,true);int before=(Integer)get(game,"gameMinute");tick();require((Integer)get(game,"gameMinute")==before,"tick blocked by "+flag);set(game,flag,false);snapshot("blocked-"+flag);}
  for(int minute:new int[]{0,359,360,599,600,719,720,1079,1080,1319,1320,1439}){set(game,"gameMinute",minute);System.out.println("clock-"+minute+":"+call(game,"clock")+":"+call(game,"phase"));draw("lighting-"+minute);}
  fresh();set(game,"mats",100);call(game,"startUpgrade",3);snapshot("upgrade-start");int duration=(Integer)get(game,"buildRemaining");for(int i=0;i<duration;i++)call(game,"advanceMinute");require(((int[])get(game,"roomLevels"))[3]==2,"upgrade completed");snapshot("upgrade-finish");
  ((int[])get(game,"roomCondition"))[0]=40;call(game,"repairRoom",0);require(((int[])get(game,"roomCondition"))[0]==70,"repair");snapshot("repair");
  for(String job:new String[]{"Отдых","Еда","Вода","Материалы","Ремонт","Охрана","Лечение","Экспедиция"}){fresh();for(Object p:people())set(p,"job",job);set(people().get(0),"health",30);set(people().get(1),"fatigue",90);call(game,"processJobs");snapshot("production-"+job);draw("residents-"+job);}
  fresh();set(game,"gameMinute",1439);tick();require((Integer)get(game,"day")==2,"day transition");snapshot("new-day");
  fresh();set(game,"food",0);set(game,"water",0);for(Object p:people())set(p,"health",1);call(game,"dailyCycle");require((Boolean)get(game,"gameOver"),"starvation/game over");snapshot("game-over");draw("game-over-canvas");
  for(int event=0;event<5;event++)for(int choice=0;choice<2;choice++){fresh();set(game,"rnd",new FixedRandom(event));call(game,"triggerEvent");require((Boolean)get(game,"event"),"event opened");snapshot("event-"+event);draw("event-canvas-"+event);call(game,"choose",choice);require(!(Boolean)get(game,"event")&&(Integer)get(game,"incidentRoom")==-1,"event closed");snapshot("choice-"+event+"-"+choice);}
  for(int li=0;li<6;li++){fresh();List<?> locations=(List<?>)get(game,"locations");set(locations.get(li),"discovered",true);call(game,"startExpedition",li);snapshot("expedition-start-"+li);set(game,"expeditionRemaining",1);call(game,"advanceMinute");require((Integer)get(game,"expeditionPerson")==-1,"expedition returned");snapshot("expedition-finish-"+li);}
  fresh();Context.preferences.values.put("day",7);Context.preferences.values.put("p0_name","Старый житель");Context.preferences.values.put("room0",3);Context.preferences.values.put("count",1);Context.preferences.values.put("log","Старая запись\n§\nВторая запись");call(game,"load");require((Integer)get(game,"day")==7&&people().size()==1,"legacy save loaded");require("Старый житель".equals(get(people().get(0),"name")),"legacy resident");snapshot("legacy-missing-keys");
  fresh();set(game,"mats",100);call(game,"startUpgrade",2);call(game,"startExpedition",0);set(game,"speed",4);set(game,"paused",true);call(game,"save");TreeMap<String,Object> saved=new TreeMap<>(Context.preferences.values);MainActivity loaded=new MainActivity();loaded.onCreate(null);view=get(loaded,"view");try{game=get(view,"game");}catch(NoSuchFieldException e){game=view;}call(game,"save");require(saved.equals(Context.preferences.values),"save/load roundtrip");snapshot("roundtrip-active-timers");
  fresh();for(int height:new int[]{640,840,1200}){View.height=height;draw("geometry-canvas-"+height);float top=116f,bottom=Math.max(610f,height-72f);StringBuilder grid=new StringBuilder();for(int x=0;x<=420;x+=7)for(int y=0;y<=height;y+=7)grid.append(geometry("fullSceneRoomAt",(float)x,(float)y,top,bottom)).append(',');for(int room=0;room<6;room++){float[] rect=(float[])geometry("fullSceneRoomRect",room,top,bottom);grid.append(Arrays.toString(rect));for(int slot=-1;slot<=4;slot++)grid.append(Arrays.toString((float[])geometry("fullSceneResidentPos",room,slot,top,bottom)));}System.out.println("hitbox-grid-"+height+":"+hash(grid.toString()));}
  View.height=840;fresh();draw("touch-initial");float x=((float[])get(game,"residentX"))[0],y=((float[])get(game,"residentY"))[0];tap(x,y-20);require((Integer)get(game,"selected")==0&&(Integer)get(game,"overlay")==1,"resident before room");snapshot("resident-priority");tap(50,400);require((Integer)get(game,"overlay")==0&&(Integer)get(game,"selectedRoom")==-1,"overlay dismiss blocks background");
  float[] q=(float[])geometry("fullSceneRoomRect",0,116f,768f);tap(q[0]+3,q[1]+3);require((Integer)get(game,"overlay")==2&&(Integer)get(game,"selectedRoom")==0,"room opens overlay");snapshot("room-overlay");tap(100,700);require((Integer)get(game,"overlay")==2&&(Integer)get(game,"screen")==0,"overlay blocks background");tap(300,750);require((Integer)get(game,"overlay")==0,"overlay close");tap(210,190);require((Integer)get(game,"screen")==5,"city exit");snapshot("city-exit");tap(200,810);require((Integer)get(game,"screen")==0,"map back");
  for(int i=0;i<4;i++){tap(18+4*77+20,810);snapshot("speed-navigation-"+i);}require(!(Boolean)get(game,"paused")&&(Integer)get(game,"speed")==1,"pause x1 x2 x4 cycle");
  for(int screen=0;screen<=5;screen++){set(game,"screen",screen);set(game,"selected",0);set(game,"selectedRoom",0);draw("screen-"+screen);}set(game,"screen",0);for(int overlay=1;overlay<=3;overlay++){set(game,"overlay",overlay);draw("overlay-"+overlay);}set(game,"jobMenu",true);draw("job-menu");
  System.out.println("ASSERTIONS:"+checks);
 }
}
'''


def write(path, contents):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(contents, encoding="utf-8")


def run_version(temp, name, sources):
    directory = temp / name
    for filename, contents in {**STUBS, **sources}.items():
        # Fix wall time only in temporary test copies; production sources stay untouched.
        write(directory / filename, contents.replace("System.currentTimeMillis()", "test.TestTime.now()"))
    write(directory / "com/lastdom/game/RegressionProbe.java", PROBE)
    javac = str(Path(os.environ["JAVA_HOME"]) / "bin/javac") if "JAVA_HOME" in os.environ else "javac"
    java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if "JAVA_HOME" in os.environ else "java"
    classes = directory / "classes"
    files = sorted(str(p) for p in directory.rglob("*.java"))
    subprocess.run([javac, "-encoding", "UTF-8", "-d", str(classes), *files], check=True)
    return subprocess.check_output([java, "-cp", str(classes), "com.lastdom.game.RegressionProbe"], text=True)


def main():
    baseline = subprocess.check_output(
        ["git", "show", f"{BASELINE}:{JAVA_PATH}/MainActivity.java"], cwd=ROOT, text=True
    )
    # Both runs use the same resource identifiers without touching the Android build.
    resource_names = sorted(set(re.findall(r"R\.drawable\.(\w+)", baseline)))
    resource_class = "package com.lastdom.game; public class R {public static class drawable {" + "".join(
        f"public static final int {name}={i};" for i, name in enumerate(resource_names, 1)
    ) + "}}"
    original = {"com/lastdom/game/MainActivity.java": baseline, "com/lastdom/game/R.java": resource_class}
    current = {"com/lastdom/game/" + p.name: p.read_text() for p in (ROOT / JAVA_PATH).glob("*.java")}
    current["com/lastdom/game/R.java"] = resource_class
    with tempfile.TemporaryDirectory(prefix="last-hous-regression-") as directory:
        temp = Path(directory)
        before = run_version(temp, "before", original)
        after = run_version(temp, "after", current)
        if before != after:
            print("".join(difflib.unified_diff(before.splitlines(True), after.splitlines(True), fromfile="baseline", tofile="refactor")))
            raise SystemExit("FAIL: behavior differs from baseline")
    print(f"PASS: {len(after.splitlines()) - 1} deterministic baseline comparisons; {after.splitlines()[-1]}")
    print("Covers save keys/defaults/roundtrip, ticks/pause/speeds, resources, AI, all event choices,")
    print("upgrades/repair, existing expeditions, room grids/slots, touch priority, overlays and Canvas commands.")
    print("Android doubles do not verify real device rendering or lifecycle integration.")


if __name__ == "__main__":
    main()
