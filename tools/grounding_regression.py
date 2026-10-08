#!/usr/bin/env python3
"""Check floor contact, movement and touches using deterministic JVM Android doubles.

Run: python3 tools/grounding_regression.py (Python 3 and JDK 21 required).
Also compares nonvisual gameplay/save outputs with the pre-refactor Git baseline.
This verifies Canvas coordinates, not pixels on a real Android device.
"""
import sys
sys.dont_write_bytecode = True
import difflib
from pathlib import Path
import subprocess
import tempfile
import refactor_regression as fixtures

PROBE = r'''
package com.lastdom.game;
import android.content.*;
import android.graphics.*;
import android.view.*;
import java.util.*;

public class RegressionProbe {
 static int checks;
 static void require(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
 static void close(float a,float b,String name){require(Math.abs(a-b)<.001f,name+": "+a+" != "+b);}
 static final String[] JOBS={"Ремонт","Еда","Лечение","Материалы","Охрана","Отдых"};
 static final float[] FLOORS={630,860,860,1088,390,1088};
 static final float[][] XS={{.18f,.29f,.40f},{.18f,.29f,.40f},{.62f,.73f,.84f},{.62f,.73f,.84f},{.28f,.50f,.72f},{.17f,.29f,.41f}};
 static GameView fresh(int w,int h){View.width=w;View.height=h;Context.preferences=new MemoryPreferences();GameView v=new GameView(new Context());v.game.people.clear();v.game.residentVisualReady=false;return v;}
 static Resident resident(String job){Resident s=new Resident("Житель","Инженер",4);s.job=job;return s;}
 static float bottom(GameView v){return Math.max(610f,v.H/v.scale-72f);}
 static void draw(GameView v){v.onDraw(new Canvas());}
 static void bootContact(GameView v,int i){
  Canvas c=new Canvas();float x=v.game.residentX[i],ground=v.game.residentY[i];
  v.residentRenderer.drawDynamicResident(c,v.game.people.get(i),x,ground,i);
  int boots=0;
  for(String command:c.commands){
   if(command.startsWith("drawLine[")&&command.contains(":"+(3.8f*v.scale)+":")){
    String[] args=command.substring(9).split(", ");
    close(Float.parseFloat(args[1])+3.8f*v.scale/2,ground*v.scale,"left boot stroke touches floor");
    close(Float.parseFloat(args[3])+3.8f*v.scale/2,ground*v.scale,"right boot stroke touches floor");boots++;
   }
   if(command.startsWith("drawOval[")){
    String[] args=command.substring(9).split(", ");
    close((Float.parseFloat(args[1])+Float.parseFloat(args[3]))/2,ground*v.scale,"shadow centred on ground");
   }
   if(command.startsWith("drawCircle[")){
    String[] args=command.substring(11).split(", ");
    if(Math.abs(Float.parseFloat(args[2])-6.8f*v.scale)<.001f){
     float headY=Float.parseFloat(args[1]),radius=Float.parseFloat(args[2]);
     require(headY-radius>=(ground-46)*v.scale,"drawn head inside upward hitbox");
     require(headY+radius<ground*v.scale,"drawn head above feet");
    }
   }
  }
  require(boots==2,"two boots drawn");
  require(ResidentRenderer.containsGroundedResident(x,ground,x,ground),"feet hitbox");
  require(ResidentRenderer.containsGroundedResident(x,ground-22,x,ground),"torso hitbox");
  require(ResidentRenderer.containsGroundedResident(x,ground-43,x,ground),"head hitbox");
  require(!ResidentRenderer.containsGroundedResident(x,ground+3,x,ground),"no old downward centre hitbox");
  require(!ResidentRenderer.containsGroundedResident(x,ground-47,x,ground),"above body excluded");
 }
 static void tap(GameView v,float x,float y){require(v.onTouchEvent(new MotionEvent(x*v.scale,y*v.scale,MotionEvent.ACTION_UP)),"tap consumed");}
 public static void main(String[] args){
  for(int width:new int[]{420,840})for(int height:new int[]{640,840,1200}){
   GameView v=fresh(width,height);
   for(int room=0;room<6;room++)for(int slot=0;slot<3;slot++)v.game.people.add(resident(JOBS[room]));
   draw(v);float top=116,b=bottom(v);
   for(int room=0;room<6;room++)for(int slot=0;slot<3;slot++){
    int i=room*3+slot;float[] target=ShelterGeometry.fullSceneResidentPos(room,slot,top,b);
    close(target[0],8+404*XS[room][slot],"preserved slot X");
    close(target[1],top+(b-top)*FLOORS[room]/1370,"artwork floor");
    close(v.game.residentY[i],target[1],"same floor for all three slots");
    require(!v.residentRenderer.residentState(v.game.people.get(i),i).startsWith("Идёт"),"no legacy false walking");
    bootContact(v,i);
    require(v.residentRenderer.shelterResidentAt(v.game.residentX[i],v.game.residentY[i])==i,"each room/slot feet selectable");
    tap(v,v.game.residentX[i],v.game.residentY[i]);
    require(v.game.selected==i&&v.game.overlay==1&&v.game.screen==0,"feet select resident before room/exit");v.game.overlay=0;
   }
   // Exact old room geometry, including boundary taps and the upper-right workshop extension.
   for(int x=0;x<=420;x+=3)for(int y=0;y<=height;y+=3)
    require(ShelterGeometry.fullSceneRoomAt(x,y,top,b)==RoomGolden.fullSceneRoomAt(x,y,top,b),"room hitbox unchanged");
   for(int ri=0;ri<6;ri++)require(Arrays.equals(ShelterGeometry.fullSceneRoomRect(ri,top,b),RoomGolden.fullSceneRoomRect(ri,top,b)),"room rectangle unchanged");
  }
  // Default five residents and larger groups must not collapse into the old clamped slot 2.
  for(int room=0;room<6;room++)for(int count:new int[]{5,8}){
   GameView crowded=fresh(420,840);for(int i=0;i<count;i++)crowded.game.people.add(resident(JOBS[room]));draw(crowded);
   for(int i=0;i<count;i++){
    close(crowded.game.residentY[i],crowded.game.residentY[0],"crowded room shares one floor");bootContact(crowded,i);
    for(float dx:new float[]{-5,0,5})require(crowded.residentRenderer.shelterResidentAt(crowded.game.residentX[i]+dx,crowded.game.residentY[i])==i,"crowded room both feet select own resident");
    if(i>0)require(crowded.game.residentX[i]>crowded.game.residentX[i-1],"distinct crowd slot X");
   }
  }
  // Every ordered pair of rooms, not just bedroom -> generator.
  for(int from=0;from<6;from++)for(int to=0;to<6;to++)if(from!=to){
   GameView v=fresh(420,840);Resident s=resident(JOBS[from]);v.game.people.add(s);draw(v);
   float oldX=v.game.residentX[0],oldY=v.game.residentY[0];s.job=JOBS[to];
   float[] target=ShelterGeometry.fullSceneResidentPos(to,0,116,bottom(v));
   draw(v);float travelled=(float)Math.hypot(v.game.residentX[0]-oldX,v.game.residentY[0]-oldY);
   close(travelled,3.8f,"existing movement step, no teleport");
   require(v.residentRenderer.residentState(s,0).startsWith("Идёт"),"walking state follows actual anchor");
   float distance=Float.POSITIVE_INFINITY;
   for(int frame=0;frame<400&&v.game.residentVisualRoom[0]!=to;frame++){
    float next=(float)Math.hypot(target[0]-v.game.residentX[0],target[1]-v.game.residentY[0]);
    require(next<=distance+.001f,"ground anchor approaches target");distance=next;
    bootContact(v,0);
    require(v.residentRenderer.shelterResidentAt(v.game.residentX[0],v.game.residentY[0])==0,"moving feet selectable");draw(v);
   }
   close(v.game.residentX[0],target[0],"arrived X");close(v.game.residentY[0],target[1],"arrived groundY");
   for(int frame=0;frame<5;frame++){draw(v);close(v.game.residentY[0],target[1],"no arrival/breathing Y offset");bootContact(v,0);}
   v.residentRenderer.drawLivingResidentsOverShelter(new Canvas());close(v.game.residentY[0],target[1],"legacy entry uses same ground point");
  }
  GameView v=fresh(420,840);v.game.people.add(resident("Отдых"));draw(v);v.game.people.get(0).job="Ремонт";draw(v);
  View.height=1200;draw(v);for(int i=0;i<400;i++)draw(v);float[] target=ShelterGeometry.fullSceneResidentPos(0,0,116,bottom(v));close(v.game.residentY[0],target[1],"resize mid-walk lands on resized floor");bootContact(v,0);
  v.game.people.add(resident("Лечение"));draw(v);target=ShelterGeometry.fullSceneResidentPos(2,0,116,bottom(v));close(v.game.residentY[1],target[1],"new resident initializes at room ground");
  v.game.reset();draw(v);target=ShelterGeometry.fullSceneResidentPos(5,0,116,bottom(v));close(v.game.residentY[0],target[1],"reset reinitializes cached anchors");
  v.game.save();v.game.load();draw(v);close(v.game.residentY[0],target[1],"load reinitializes transient anchors");
  // Guard body overlaps the existing exit rectangle; resident touch wins, exit is still clickable.
  v=fresh(420,840);v.game.people.add(resident("Охрана"));v.game.people.add(resident("Охрана"));draw(v);
  tap(v,v.game.residentX[1],v.game.residentY[1]-30);require(v.game.selected==1&&v.game.overlay==1&&v.game.screen==0,"surface body before exit");
  int room=v.game.selectedRoom;tap(v,40,400);require(v.game.overlay==0&&v.game.selectedRoom==room,"overlay dismiss does not click room");
  tap(v,210,190);require(v.game.screen==5,"unchanged city exit remains clickable");
  v=fresh(420,840);v.game.people.add(resident("Лечение"));draw(v);
  v.game.people.get(0).health=30;v.game.people.get(0).fatigue=90;bootContact(v,0);
  v.game.people.get(0).alive=false;draw(v);require(v.residentRenderer.shelterResidentAt(260,400)==-1,"dead resident excluded");
  v.game.people.get(0).alive=true;v.game.people.get(0).job="Экспедиция";draw(v);require(v.residentRenderer.shelterResidentAt(260,400)==-1,"existing expedition resident excluded");
  System.out.println("PASS: "+checks+" grounding assertions; six rooms/three slots, scales, 30 room transitions, boots, shadow, hitboxes, resize, reset/load and exit priority.");
 }
}
'''


def main():
    root = fixtures.ROOT
    current = {"com/lastdom/game/" + p.name: p.read_text() for p in (root / fixtures.JAVA_PATH).glob("*.java")}
    current["com/lastdom/game/R.java"] = "package com.lastdom.game; public class R {public static class drawable {public static final int shelter_clean=1,shelter_full_scene=2;}}"
    before_geometry = subprocess.check_output(["git", "show", "fd03753317d61373b7f9ca7d94f7fc6dabfd5866:" + fixtures.JAVA_PATH + "/ShelterGeometry.java"], cwd=root, text=True)
    current["com/lastdom/game/RoomGolden.java"] = before_geometry.replace("ShelterGeometry", "RoomGolden")
    original_probe = fixtures.PROBE
    with tempfile.TemporaryDirectory(prefix="grounding-regression-") as directory:
        temp = Path(directory)
        fixtures.PROBE = PROBE
        print(fixtures.run_version(temp, "grounding", current).strip())
        # The full old visual comparison intentionally differs after this fix. Keep all gameplay
        # assertions, compare saves/state, and exclude only visual coordinates/Canvas output.
        gameplay_probe = original_probe.replace("residentX residentY residentVisualRoom residentVisualReady ", "")
        # Stage 1 retains the HOME tab; the old map used any footer tap as Back.
        gameplay_probe = gameplay_probe.replace('tap(200,810);require', 'tap(50,810);require')
        gameplay_probe = gameplay_probe.replace('if(m.getName().equals(name)){', 'if(m.getName().equals(name)&&m.getParameterCount()==args.length){')
        gameplay_probe = gameplay_probe.replace('System.out.println(label+":"+hash(c.commands.toString()));', '')
        gameplay_probe = gameplay_probe.replace('System.out.println("hitbox-grid-"+height+":"+hash(grid.toString()));', '')
        # Stage 2 replaces automatic solo trips/rewards. Their new behavior is checked in
        # expedition_regression.py; retain the old baseline for all unrelated simulation.
        gameplay_probe = "\n".join(line for line in gameplay_probe.split("\n")
                                    if not line.strip().startswith("for(int li=0;li<6;li++){fresh();"))
        gameplay_probe = gameplay_probe.replace(',"Экспедиция"}', '}')
        gameplay_probe = gameplay_probe.replace('call(game,"startExpedition",0);', '')
        gameplay_probe = gameplay_probe.replace('if(!f.isSynthetic()){',
            'if(!f.isSynthetic()&&!f.getName().equals("id")&&!f.getName().equals("status")){')
        gameplay_probe = gameplay_probe.replace('state.put("saved",Context.preferences.values.toString());',
            'TreeMap<String,Object> legacy=new TreeMap<>(Context.preferences.values);'
            'legacy.keySet().removeIf(k->k.startsWith("exp2_")||k.matches("p[0-9]+_id"));'
            'state.put("saved",legacy.toString());')
        fixtures.PROBE = gameplay_probe
        baseline = subprocess.check_output(["git", "show", f"{fixtures.BASELINE}:{fixtures.JAVA_PATH}/MainActivity.java"], cwd=root, text=True)
        original = {"com/lastdom/game/MainActivity.java": baseline, "com/lastdom/game/R.java": current["com/lastdom/game/R.java"]}
        before = fixtures.run_version(temp, "gameplay-before", original)
        after = fixtures.run_version(temp, "gameplay-after", current)
        if before != after:
            print("".join(difflib.unified_diff(before.splitlines(True), after.splitlines(True))))
            raise SystemExit("FAIL: nonvisual gameplay/save regression")
        print(f"PASS: {len(after.splitlines()) - 1} unchanged gameplay/save comparisons; {after.splitlines()[-1]} in each version.")
    print("Device rendering/installation was not exercised; Canvas coordinates used Android doubles.")


if __name__ == "__main__":
    main()
