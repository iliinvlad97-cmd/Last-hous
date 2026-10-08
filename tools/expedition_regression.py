#!/usr/bin/env python3
"""Stage 2 controller, persistence, Canvas/touch and simulation-clock regression checks.

Run with Python 3 and JDK 21: python3 tools/expedition_regression.py
Android doubles exercise actual Java code; no device installation is simulated.
"""
import sys
sys.dont_write_bytecode = True
from pathlib import Path
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
 static void require(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
 static void close(float a,float b,String s){require(Math.abs(a-b)<.001,s+": "+a+" != "+b);}
 static GameView fresh(int w,int h){View.width=w;View.height=h;Context.preferences=new MemoryPreferences();return loaded();}
 static GameView loaded(){GameView v=new GameView(new Context());v.game.rnd=new Random(){public double nextDouble(){return .99;}public int nextInt(int n){return n-1;}};draw(v);return v;}
 static Canvas draw(GameView v){Canvas c=new Canvas();v.onDraw(c);return c;}
 static void text(Canvas c,String text){require(c.commands.stream().anyMatch(s->s.contains(text)),"text: "+text);}
 static void tap(GameView v,float x,float y){require(v.onTouchEvent(new MotionEvent(x*v.scale,y*v.scale,MotionEvent.ACTION_UP)),"tap consumed");}
 static void nav(GameView v,int index){tap(v,38+77*index,v.H/v.scale-40);}
 static List<String> ids(GameView v,int... indices){List<String> ids=new ArrayList<>();for(int i:indices)ids.add(v.game.people.get(i).id);return ids;}
 static String save(GameView v){v.save();return Context.preferences.values.toString();}
 static void fail(GameView v,String location,List<String> ids,String message){
  String before=save(v);require(!v.game.expeditionController.start(location,ids).isEmpty(),message);
  require(before.equals(save(v)),"failed launch atomic: "+message);require(v.game.expeditions.isEmpty(),"no partial expedition");
 }
 static void prepare(GameView v,int index){
  nav(v,2);CityMapLayout m=new CityMapLayout(v.H/v.scale);MapLocation target=v.cityMap.locations.get(index);
  tap(v,m.x(target.mapX),m.y(target.mapY));require(v.cityMap.selected()==target,"selected location");
  text(draw(v),"Путь туда / обратно");tap(v,210,m.panelBottom-40);
  require(v.cityMap.preparation()==target,"preparation opened");text(draw(v),"ПОДГОТОВКА ЭКСПЕДИЦИИ");
 }
 static void row(GameView v,int index){
  ExpeditionPreparationLayout p=new ExpeditionPreparationLayout(new CityMapLayout(v.H/v.scale).panelBottom);
  while(v.cityMap.page<index/p.capacity)tap(v,355,p.pageY);
  while(v.cityMap.page>index/p.capacity)tap(v,50,p.pageY);
  tap(v,180,p.rowTop(index%p.capacity)+25);
 }
 static void send(GameView v){tap(v,210,new CityMapLayout(v.H/v.scale).panelBottom-34);}
 static float[] marker(Canvas c){
  for(String command:c.commands)if(command.startsWith("drawCircle[")){
   String[] args=command.substring(11).split(", ");return new float[]{Float.parseFloat(args[0]),Float.parseFloat(args[1])};
  }throw new AssertionError("marker missing");
 }
 public static void main(String[] args){
  for(int w:new int[]{420,840})for(int logicalHeight:new int[]{640,840,1200}){
   GameView v=fresh(w,logicalHeight*w/420);GameController g=v.game;
   prepare(v,0);send(v);require(g.expeditions.isEmpty(),"empty squad blocked");text(draw(v),"Выберите от 1 до 3 жителей");
   for(int i=0;i<3;i++)row(v,i);row(v,3);require(v.cityMap.selectedIds.size()==3,"fourth selection blocked");
   text(draw(v),"не более 3 жителей");row(v,1);require(v.cityMap.selectedIds.size()==2,"deselect");row(v,1);
   String fourthJob=g.people.get(3).job;send(v);
   Expedition e=g.expeditionController.active();require(e!=null&&e.participantIds.size()==3,"three launched");
   require(e.state()==Expedition.State.TRAVELING_TO_TARGET&&e.elapsedMinutes()==0,"start state");
   require(v.cityMap.preparation()==null&&g.screen==GameView.CITY_MAP,"launch closes preparation");
   require(e.departureMinute==480&&e.durationMinutes==45,"departure and near duration");
   int food=g.food,water=g.water,mats=g.mats,power=g.power;
   // Isolate reward assertions from Stage 6 ration purchases (covered by survival regression).
   for(Resident resident:g.people)if(!g.isOnExpedition(resident)){resident.foodMinutes=1440;resident.waterMinutes=1440;}
   for(int i=0;i<3;i++){
    Resident resident=g.people.get(i);require(resident.status==Resident.Status.ON_EXPEDITION,"resident status");
    require(g.homeRoomFor(resident)==-1&&!g.assignJob(i,"Отдых"),"away has no room and cannot be reassigned");
   }
   require(g.people.get(3).job.equals(fourthJob),"home worker untouched");
   nav(v,0);draw(v);
   require(v.residentRenderer.shelterResidentAt(g.residentX[0],g.residentY[0]-20)!=0,"away not selectable in home");
   g.people.get(3).job="Материалы";g.people.get(4).job="Ремонт";require(g.findBestResident("Инженер","")>=3,"AI excludes participants and chooses a working resident");
   g.people.get(3).job="Материалы";g.people.get(4).job="Ремонт";
   int health=g.people.get(0).health,fatigue=g.people.get(0).fatigue;g.processJobs();
   require(g.mats==mats&&g.productionController.workshopSavingBasis()>0,"remaining workshop worker conserves materials without free generation");require(g.people.get(0).health==health&&g.people.get(0).fatigue==fatigue,"away not processed as home worker");
   g.mats=mats;power=g.power; // processJobs now also runs the Stage 5 generator.
   g.people.get(3).job="Отдых";g.people.get(4).job="Отдых";
   nav(v,3);text(draw(v),"В экспедиции");g.selected=0;g.overlay=1;draw(v);
   ResidentNeedsLayout needsLayout=new ResidentNeedsLayout(logicalHeight);tap(v,180,needsLayout.footer+22);require(!g.jobMenu,"away assignment menu blocked");
   tap(v,100,needsLayout.footer+72);require(g.people.get(0).job.equals("Экспедиция"),"rest button cannot recall squad");g.overlay=0;
   nav(v,2);CityMapLayout m=new CityMapLayout(logicalHeight);draw(v);
   tap(v,180,m.bottom-39);require(v.cityMap.expeditionPanel,"indicator opens expedition panel");text(draw(v),"ЭКСПЕДИЦИЯ");
   tap(v,210,m.panelBottom-40);require(!v.cityMap.expeditionPanel,"panel close");
   MapLocation next=v.cityMap.locations.get(1);tap(v,m.x(next.mapX),m.y(next.mapY));tap(v,210,m.panelBottom-40);
   require(v.cityMap.preparation()==null,"second preparation blocked");text(draw(v),"Сначала завершите текущую экспедицию");
   v.cityMap.closeSelection();String before=save(v);
   require(!g.expeditionController.start(next.id,ids(v,3)).isEmpty(),"second launch blocked");require(before.equals(save(v)),"second launch atomic");
   for(int speed:new int[]{1,2,4}){
    g.speed=speed;int elapsed=e.elapsedMinutes(),minute=g.gameMinute;v.tick.run();
    require(e.elapsedMinutes()==elapsed+speed&&g.gameMinute==minute+speed,"simulation speed "+speed);
    require(Context.preferences.getInt("exp2_0_elapsed",-1)==e.elapsedMinutes(),"travel persisted every game minute");
   }
   draw(v);float progress=v.cityMap.displayProgress;
   Canvas route=new Canvas();v.expeditionRenderer.drawRoute(route,m);
   float[] center=marker(route),point=ExpeditionConfig.point(g.cityLocations.get(0),v.cityMap.displayProgress);
   close(center[0],m.x(point[0])*v.scale,"marker X matches map transform");close(center[1],m.y(point[1])*v.scale,"marker Y matches map transform");
   require(v.cityMap.displayProgress>progress&&v.cityMap.displayProgress<e.progress(),"visual movement interpolated");
   float[] touchPoint=ExpeditionConfig.point(g.cityLocations.get(0),v.cityMap.displayProgress);
   tap(v,m.x(touchPoint[0]),m.y(touchPoint[1]));require(v.cityMap.expeditionPanel,"moving marker opens panel");tap(v,210,m.panelBottom-40);
   g.paused=true;float pausedProgress=v.cityMap.displayProgress;int elapsed=e.elapsedMinutes();
   for(int i=0;i<5;i++){v.tick.run();draw(v);}
   close(v.cityMap.displayProgress,pausedProgress,"paused marker freezes");require(e.elapsedMinutes()==elapsed,"paused travel freezes");g.paused=false;
   for(String flag:new String[]{"event","gameOver"}){
    if(flag.equals("event"))g.event=true;else g.gameOver=true;
    v.tick.run();require(e.elapsedMinutes()==elapsed,"existing tick blocker "+flag);g.event=false;g.gameOver=false;
   }
   nav(v,0);nav(v,2);require(e.elapsedMinutes()==elapsed,"navigation does not reset progress");
   save(v);TreeMap<String,Object> saved=new TreeMap<>(Context.preferences.values);GameView restored=loaded();
   Expedition copy=restored.game.expeditionController.active();require(copy!=null&&copy.elapsedMinutes()==elapsed,"active travel restores");
   require(copy.participantIds.equals(e.participantIds)&&copy.locationId.equals(e.locationId)&&copy.departureMinute==e.departureMinute,"identity and departure restore");
   for(int i=0;i<3;i++)require(restored.game.homeRoomFor(restored.game.people.get(i))==-1,"restored participants absent from rooms");
   save(restored);require(saved.equals(Context.preferences.values),"save/load exact roundtrip");
   restored.game.load();require(restored.game.expeditions.size()==1&&restored.game.people.size()==5,"repeated load has no duplication");
   while(copy.state()==Expedition.State.TRAVELING_TO_TARGET){restored.game.advanceMinute();copy=restored.game.expeditionController.active();}
   require(copy.state()==Expedition.State.EXPLORING&&copy.remainingMinutes()==30&&copy.routeProgress()==1,"arrival starts exploration");
   for(int i=0;i<60;i++)restored.game.advanceMinute();
   require(copy.state()==Expedition.State.AWAITING_RETURN&&copy.resultGenerated,"research result waits for manual return");
   require(restored.game.food==food&&restored.game.water==water&&restored.game.mats==mats&&restored.game.power==power,"no reward before return");
   nav(restored,2);text(draw(restored),"ЭКСПЕДИЦИЯ — РЕЗУЛЬТАТЫ");
   save(restored);GameView arrived=loaded();require(arrived.game.expeditionController.active().state()==Expedition.State.AWAITING_RETURN,"result survives restart");
   require(!arrived.game.expeditionController.start("shop",ids(arrived,3)).isEmpty(),"waiting squad still occupies active slot");
  }
  GameView v=fresh(420,840);
  fail(v,"hospital",ids(v,0),"locked");fail(v,"missing",ids(v,0),"unknown target");
  fail(v,"shop",new ArrayList<>(),"empty");fail(v,"shop",ids(v,0,1,2,3),"four");fail(v,"shop",ids(v,0,0),"duplicate");
  fail(v,"shop",Arrays.asList(v.game.people.get(0).id,"missing"),"missing resident");
  v.game.people.get(1).alive=false;fail(v,"shop",ids(v,0,1),"dead second participant");
  v.game.people.get(1).alive=true;v.game.people.get(1).health=0;fail(v,"shop",ids(v,1),"no health");
  v.game.people.get(1).health=100;v.game.people.get(1).job="Экспедиция";fail(v,"shop",ids(v,1),"already assigned");
  v.game.people.get(1).job="Отдых";
  prepare(v,0);row(v,1);v.game.people.get(1).alive=false;send(v);require(v.game.expeditions.isEmpty(),"launch revalidates selection");
  v.game.people.get(1).alive=true;v.game.cityLocations.get(0).setState(MapLocation.State.LOCKED);send(v);require(v.game.expeditions.isEmpty(),"launch revalidates lock");
  v=fresh(420,640);for(int i=0;i<8;i++)v.game.people.add(v.game.make("Новый "+i,"Выживший",2));
  prepare(v,0);row(v,12);require(v.cityMap.selectedIds.contains(v.game.people.get(12).id),"paging reaches all residents");
  send(v);require(v.game.expeditionController.active().participantIds.size()==1,"one resident launched");
  for(int location:new int[]{0,2,4}){
   v=fresh(420,840);MapLocation target=v.game.cityLocations.get(location);target.setState(MapLocation.State.AVAILABLE);
   int duration=location==0?45:location==2?90:150;require(ExpeditionConfig.oneWayMinutes(target)==duration,"central distance balance");
   v.game.gameMinute=1439;v.game.rnd=new Random(){public double nextDouble(){return .99;}public int nextInt(int n){return n-1;}};
   require(v.game.expeditionController.start(target.id,ids(v,0)).isEmpty(),"distance launches");
   v.game.advanceMinute();require(v.game.day==2&&v.game.expeditionController.active().elapsedMinutes()==1,"midnight travel continuous");
   float[] start=ExpeditionConfig.point(target,0),end=ExpeditionConfig.point(target,1);
   close(start[0],CityMapLayout.SHELTER_X,"route begins at shelter X");close(start[1],CityMapLayout.SHELTER_Y,"route begins at shelter Y");
   close(end[0],target.mapX,"route ends at target X");close(end[1],target.mapY,"route ends at target Y");
  }
  View.width=420;View.height=840;Context.preferences=new MemoryPreferences();
  Context.preferences.edit().putInt("day",7).putInt("count",1).putString("p0_name","Старый житель").putInt("room0",3).apply();
  v=loaded();require(Context.preferenceName.equals("save_v02")&&v.game.day==7&&v.game.people.size()==1&&v.game.roomLevels[0]==1,"old pre-Stage5 levels migrate to baseline1");
  require(v.game.expeditions.isEmpty()&&v.game.people.get(0).id.equals("legacy-0"),"legacy stable ID default");
  require(v.game.expeditionController.start("shop",ids(v,0)).isEmpty(),"legacy resident can launch");save(v);String id=v.game.people.get(0).id;
  v=loaded();require(v.game.people.get(0).id.equals(id),"legacy identity stable after restart");
  Context.preferences=new MemoryPreferences();Context.preferences.edit().putInt("day",3).putInt("count",1).putString("p0_job","Экспедиция").putInt("expPerson",0).putInt("expLoc",1).putInt("expRemain",12).apply();
  v=loaded();Expedition migrated=v.game.expeditionController.active();require(migrated!=null&&migrated.locationId.equals("pharmacy")&&migrated.remainingMinutes()==12,"old active expedition migrates");
  require(v.game.expeditionPerson==-1&&v.game.homeRoomFor(v.game.people.get(0))==-1,"legacy no duplicate timer/room");
  int mats=v.game.mats;for(int i=0;i<13;i++)v.game.advanceMinute();require(migrated.state()==Expedition.State.EXPLORING&&v.game.mats==mats,"legacy migration no old rewards");
  save(v);v=loaded();require(v.game.expeditions.size()==1,"migration happens once");
  Context.preferences.values.put("exp2_0_state","bad state");v=loaded();require(v.game.expeditions.isEmpty()&&v.game.homeRoomFor(v.game.people.get(0))==-1,"bad record fails safely without returning/duplicating resident");
  System.out.println("PASS: "+checks+" expedition assertions; atomic launch, selection/paging, clock/pause/speeds, normalized route, AI exclusion, arrival, restart, legacy migration and invalid save.");
 }
}
'''


def main():
    sources = {"com/lastdom/game/" + p.name: p.read_text()
               for p in (fixtures.ROOT / fixtures.JAVA_PATH).glob("*.java")}
    sources["com/lastdom/game/R.java"] = "package com.lastdom.game; public class R {public static class drawable {public static final int shelter_clean=1,shelter_full_scene=2;}}"
    fixtures.PROBE = PROBE
    with tempfile.TemporaryDirectory(prefix="expedition-regression-") as directory:
        print(fixtures.run_version(Path(directory), "expedition", sources).strip())


if __name__ == "__main__":
    main()
