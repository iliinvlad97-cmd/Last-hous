#!/usr/bin/env python3
"""Stage 3 actual Java logic, durable snapshots, phases, loot and Canvas/touch regression.

Run: python3 tools/loot_return_regression.py (Python 3/JDK 21).
Android doubles are not a physical-device test.
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
 static void close(double a,double b,String s){require(Math.abs(a-b)<.001,s);}
 static class Fixed extends Random {
  final double roll;final int negative;Fixed(double r,int n){roll=r;negative=n;}
  public int nextInt(int bound){return bound==3?negative:bound-1;}
  public double nextDouble(){return roll;}
 }
 static GameView fresh(int w,int h){View.width=w;View.height=h;Context.preferences=new MemoryPreferences();return load();}
 static GameView load(){GameView v=new GameView(new Context());v.game.rnd=new Fixed(.99,2);draw(v);return v;}
 static GameView kill(){TreeMap<String,Object> disk=new TreeMap<>(Context.preferences.values);Context.preferences=new MemoryPreferences();Context.preferences.values.putAll(disk);return load();}
 static Canvas draw(GameView v){Canvas c=new Canvas();v.onDraw(c);return c;}
 static void text(Canvas c,String s){require(c.commands.stream().anyMatch(x->x.contains(s)),"drawn: "+s);}
 static void tap(GameView v,float x,float y){v.onTouchEvent(new MotionEvent(x*v.scale,y*v.scale,MotionEvent.ACTION_UP));}
 static void nav(GameView v,int i){tap(v,38+77*i,v.H/v.scale-40);}
 static List<String> ids(GameView v,int... indices){List<String> result=new ArrayList<>();for(int i:indices)result.add(v.game.people.get(i).id);return result;}
 static Expedition e(GameView v){return v.game.expeditionController.active();}
 static void start(GameView v,String location,int... indices){require(v.game.expeditionController.start(location,ids(v,indices)).isEmpty(),"launch "+location);}
 static void until(GameView v,Expedition.State state){int guard=1000;while(e(v)!=null&&e(v).state()!=state&&guard-->0)v.game.advanceMinute();require(guard>0&&e(v)!=null&&e(v).state()==state,"reached "+state);}
 static String loot(Expedition e){String value=e.id+":"+e.explorationEvent.type+":"+e.explorationEvent.message+":"+e.explorationEvent.injuredResidentId+":"+e.explorationEvent.damage+":"+e.resultGenerated;for(ExpeditionLoot.Resource r:ExpeditionLoot.Resource.values())value+=":"+r+"="+e.found.get(r)+"/"+e.cargo.get(r);return value;}
 static void full(int number,String location){
  GameView v=fresh(420,840);for(Resident r:v.game.people){r.fatigue=0;r.health=100;}
  int[] selected=number==1?new int[]{3}:number==2?new int[]{3,4}:new int[]{3,4,1};
  int food=v.game.food,water=v.game.water,mats=v.game.mats,medicine=v.game.expeditionWarehouse.get(ExpeditionLoot.Resource.MEDICINE),equipment=0;
  start(v,location,selected);Expedition first=e(v);String id=first.id;
  require(Context.preferences.commits>0,"launch is durable before UI");
  v.game.advanceMinute();int elapsed=e(v).elapsedMinutes();v=kill();require(e(v).id.equals(id)&&e(v).elapsedMinutes()==elapsed,"kill restores outbound");
  until(v,Expedition.State.EXPLORING);require(e(v).routeProgress()==1&&e(v).elapsedMinutes()==0,"arrival starts timed exploration on target");
  require(e(v).phaseDurationMinutes==ExpeditionConfig.explorationMinutes(v.game.expeditionController.location(location)),"risk duration config");
  v.game.advanceMinute();v=kill();require(e(v).state()==Expedition.State.EXPLORING&&e(v).elapsedMinutes()==1,"kill restores exploration");
  for(int speed:new int[]{1,2,4}){
   int before=e(v).elapsedMinutes();v.game.speed=speed;v.tick.run();require(e(v).elapsedMinutes()==before+speed,"exploration speed "+speed);
  }
  v.game.paused=true;elapsed=e(v).elapsedMinutes();for(int i=0;i<5;i++)v.tick.run();require(e(v).elapsedMinutes()==elapsed,"exploration pause");v.game.paused=false;
  until(v,Expedition.State.AWAITING_RETURN);Expedition result=e(v);String generated=loot(result);
  require(result.resultGenerated&&!result.rewardCredited,"result generated but not credited");
  require(result.cargo.total()<=number*10&&result.capacity()==number*10,"cargo capacity");
  require(v.game.food==food&&v.game.water==water&&v.game.mats==mats&&v.game.expeditionWarehouse.total()==0,"nothing credited at research");
  require(v.game.expeditionController.location(location).depletion()==10,"depletion raised once");
  int injuredHealth=v.game.people.get(selected[0]).health;
  v=kill();require(loot(e(v)).equals(generated),"result/event/cargo stable after kill");
  v.game.rnd=new Random(){public int nextInt(int b){throw new AssertionError("unexpected reroll");}public double nextDouble(){throw new AssertionError("unexpected reroll");}};
  for(int i=0;i<5;i++)v.game.advanceMinute();require(loot(e(v)).equals(generated),"waiting never rerolls");
  require(v.game.expeditionController.location(location).depletion()==10,"waiting never repeats depletion");
  require(!v.game.assignJob(selected[0],"Еда"),"participants unavailable before return");
  require(!v.game.expeditionController.start("shop",ids(v,0)).isEmpty(),"second squad blocked");
  nav(v,2);Canvas panel=draw(v);text(panel,"ЭКСПЕДИЦИЯ — РЕЗУЛЬТАТЫ");text(panel,"НАЙДЕНО / ВЗЯТО С СОБОЙ");text(panel,"ВЕРНУТЬСЯ В УБЕЖИЩЕ");
  CityMapLayout layout=new CityMapLayout(v.H/v.scale);tap(v,380,layout.panelTop+25);
  require(!v.cityMap.expeditionPanel,"closing result preserves expedition");
  tap(v,180,layout.bottom-39);require(v.cityMap.expeditionPanel,"result can reopen from map");
  tap(v,180,layout.panelBottom-40);require(e(v).state()==Expedition.State.RETURNING,"button starts same expedition return");
  require(e(v).id.equals(id)&&e(v).routeProgress()==1,"return begins at target");
  int foodCargo=e(v).cargo.get(ExpeditionLoot.Resource.FOOD),waterCargo=e(v).cargo.get(ExpeditionLoot.Resource.WATER),matsCargo=e(v).cargo.get(ExpeditionLoot.Resource.MATERIALS),medCargo=e(v).cargo.get(ExpeditionLoot.Resource.MEDICINE),equipCargo=e(v).cargo.get(ExpeditionLoot.Resource.EQUIPMENT);
  v.game.advanceMinute();elapsed=e(v).elapsedMinutes();require(e(v).routeProgress()<1&&e(v).routeProgress()>0,"reverse route progresses");
  require(!v.game.expeditionController.returnHome(id).isEmpty()&&e(v).elapsedMinutes()==elapsed,"duplicate return cannot reset progress");
  v=kill();require(e(v).state()==Expedition.State.RETURNING&&e(v).elapsedMinutes()==elapsed,"kill restores return");
  v.game.paused=true;v.tick.run();require(e(v).elapsedMinutes()==elapsed,"return pause");v.game.paused=false;
  for(int speed:new int[]{1,2,4}){v.game.speed=speed;int before=e(v).elapsedMinutes();v.tick.run();require(e(v).elapsedMinutes()==before+speed,"return speed "+speed);}
  require(v.game.food==food&&v.game.water==water&&v.game.mats==mats,"no credit while returning");
  nav(v,0);while(e(v)!=null)v.tick.run();
  Expedition complete=v.game.expeditionController.report();require(complete!=null&&complete.state()==Expedition.State.COMPLETED&&complete.rewardCredited,"completion grants before report confirmation");
  require(v.game.food==food+foodCargo&&v.game.water==water+waterCargo&&v.game.mats==mats+matsCargo,"exact core resource credit");
  require(v.game.expeditionWarehouse.get(ExpeditionLoot.Resource.MEDICINE)==medicine+medCargo&&v.game.expeditionWarehouse.get(ExpeditionLoot.Resource.EQUIPMENT)==equipment+equipCargo,"separate inventory credit");
  require(v.game.people.get(selected[0]).health==injuredHealth,"no second injury on return");
  for(int index:selected){Resident r=v.game.people.get(index);require(r.status==Resident.Status.HOME&&r.job.equals("Отдых")&&v.game.homeRoomFor(r)==5,"resident available again");require(r.fatigue==complete.fatigueGain,"travel fatigue once");}
  text(draw(v),"ОТРЯД ВЕРНУЛСЯ");require(v.game.screen==0,"completion report works over home");
  int total=v.game.food+v.game.water+v.game.mats+v.game.expeditionWarehouse.total();
  v.game.expeditionController.complete(complete);require(total==v.game.food+v.game.water+v.game.mats+v.game.expeditionWarehouse.total(),"repeated complete is idempotent");
  for(int restart=0;restart<5;restart++){
   v=kill();Expedition record=v.game.expeditions.get(0);require(record.rewardCredited&&record.state()==Expedition.State.COMPLETED,"completed survives kill");
   v.game.expeditionController.complete(record);v.game.expeditionController.acknowledge(id);v.game.expeditionController.acknowledge(id);
   require(total==v.game.food+v.game.water+v.game.mats+v.game.expeditionWarehouse.total(),"no double credit across restart/ack");
  }
  require(v.game.expeditionController.report()==null,"acknowledgement closes notification only");require(v.game.expeditionWarehouse.get(ExpeditionLoot.Resource.EQUIPMENT)==equipCargo,"equipment never mixes with materials");
  require(v.game.expeditionController.start("shop",ids(v,0)).isEmpty(),"new expedition after return");
 }
 public static void main(String[] args){
  for(int n=1;n<=3;n++)for(String location:new String[]{"shop","pharmacy","garage","police"})full(n,location);
  GameView v=fresh(420,840);start(v,"shop",3,4,1);Expedition expedition=e(v);
  close(v.game.expeditionController.professionBonus(expedition,ExpeditionLoot.Resource.FOOD),.2,"gatherer +20% food");
  close(v.game.expeditionController.professionBonus(expedition,ExpeditionLoot.Resource.WATER),.2,"gatherer +20% water");
  close(v.game.expeditionController.professionBonus(expedition,ExpeditionLoot.Resource.MATERIALS),.2,"mechanic +20%");
  close(v.game.expeditionController.professionBonus(expedition,ExpeditionLoot.Resource.MEDICINE),.2,"doctor +20%");
  v=fresh(420,840);start(v,"garage",0);close(v.game.expeditionController.professionBonus(e(v),ExpeditionLoot.Resource.MATERIALS),.1,"engineer +10%");
  for(Resident r:v.game.people){r.health=100;r.fatigue=0;}close(v.game.expeditionController.conditionFactor(e(v)),1,"healthy condition");
  v.game.people.get(0).health=50;close(v.game.expeditionController.conditionFactor(e(v)),.5,"health affects yield");
  v.game.people.get(0).fatigue=100;close(v.game.expeditionController.conditionFactor(e(v)),.25,"fatigue affects yield");
  for(MapLocation.Risk risk:MapLocation.Risk.values()){
   MapLocation target=v.game.cityLocations.get(3);MapLocation copy=new MapLocation("test","test","test","test",target.kind,target.distance,risk,.5f,.5f,MapLocation.State.AVAILABLE);
   double base=risk==MapLocation.Risk.LOW?.05:risk==MapLocation.Risk.LOW_MEDIUM?.10:risk==MapLocation.Risk.MEDIUM?.15:.25;
   close(ExpeditionConfig.negativeProbability(copy,false),base,"base negative probability");close(ExpeditionConfig.negativeProbability(copy,true),base*.85,"guard relative reduction");
  }
  for(int type=0;type<3;type++){
   v=fresh(420,840);v.game.rnd=new Fixed(0,type);start(v,"shop",0);until(v,Expedition.State.AWAITING_RETURN);
   require(e(v).explorationEvent.type==(type==0?ExpeditionEvent.Type.DAMAGED:type==1?ExpeditionEvent.Type.INJURY:ExpeditionEvent.Type.THREAT),"negative event branch");
   require(v.game.people.get(0).health>0,"injury cannot kill");
   int health=v.game.people.get(0).health;String event=loot(e(v));v=kill();require(event.equals(loot(e(v)))&&health==v.game.people.get(0).health,"injury/event saved exactly once");
   for(ExpeditionLoot.Resource resource:ExpeditionLoot.Resource.values())require(e(v).found.get(resource)>=0,"no negative resources");
  }
  v=fresh(420,840);v.game.people.get(0).health=1;v.game.rnd=new Fixed(0,1);start(v,"shop",0);until(v,Expedition.State.AWAITING_RETURN);require(v.game.people.get(0).health==1,"near-zero health safe injury");
  for(int index:new int[]{0,2}){v=fresh(420,840);v.game.rnd=new Fixed(.22,2);start(v,"police",index);until(v,Expedition.State.AWAITING_RETURN);require(e(v).explorationEvent.type==(index==2?ExpeditionEvent.Type.QUIET:ExpeditionEvent.Type.THREAT),"guard changes actual risk outcome");}
  v=fresh(420,840);v.game.rnd=new Fixed(.99,2){public int nextInt(int b){return b==100?0:super.nextInt(b);}};start(v,"shop",0);until(v,Expedition.State.AWAITING_RETURN);require(e(v).explorationEvent.type==ExpeditionEvent.Type.CACHE,"positive cache event");
  ExpeditionLoot found=new ExpeditionLoot();for(ExpeditionLoot.Resource r:ExpeditionLoot.Resource.values())found.set(r,15);
  ExpeditionLoot cargo=found.cargo(30);require(cargo.get(ExpeditionLoot.Resource.WATER)==15&&cargo.get(ExpeditionLoot.Resource.FOOD)==15&&cargo.get(ExpeditionLoot.Resource.MEDICINE)==0,"cargo priority order");
  cargo=found.cargo(43);require(cargo.get(ExpeditionLoot.Resource.MEDICINE)==13&&cargo.get(ExpeditionLoot.Resource.MATERIALS)==0,"medicine before materials");
  v=fresh(420,840);for(int trip=0;trip<10;trip++){
   start(v,"shop",0);until(v,Expedition.State.AWAITING_RETURN);require(v.game.cityLocations.get(0).depletion()==(trip+1)*10,"depletion each successful research");
   v.game.expeditionController.returnHome(e(v).id);while(e(v)!=null)v.game.advanceMinute();
   v.game.expeditionController.acknowledge(v.game.expeditions.get(v.game.expeditions.size()-1).id);
  }
  require(!v.game.expeditionController.start("shop",ids(v,0)).isEmpty()&&v.game.cityLocations.get(0).statusLabel().equals("Истощена"),"depleted prevents new trip");v=kill();require(v.game.cityLocations.get(0).depletion()==100,"depletion survives kill");
  require(v.game.cityLocations.get(4).isLocked()&&v.game.cityLocations.get(5).isLocked(),"no automatic district unlock");
  for(int depletion:new int[]{0,90}){
   v=fresh(420,840);for(Resident r:v.game.people){r.health=100;r.fatigue=0;}v.game.cityLocations.get(2).setDepletion(depletion);start(v,"garage",4);until(v,Expedition.State.AWAITING_RETURN);
   int expected=(int)Math.round(25*.8*1.2*(1-depletion/100.0));require(e(v).found.get(ExpeditionLoot.Resource.MATERIALS)==expected,"actual yield bonus and depletion");
  }
  for(int width:new int[]{420,840})for(int height:new int[]{640,840,1200}){
   v=fresh(width,height*width/420);nav(v,2);CityMapLayout map=new CityMapLayout(height);MapLocation shop=v.game.cityLocations.get(0);tap(v,map.x(shop.mapX),map.y(shop.mapY));
   Canvas c=draw(v);text(c,"ВОЗМОЖНАЯ ДОБЫЧА");text(c,"Еда: 8–20");text(c,"ПОДГОТОВИТЬ ЭКСПЕДИЦИЮ");
   v.cityMap.closeSelection();start(v,"shop",3,4,1);until(v,Expedition.State.AWAITING_RETURN);v.cityMap.openPendingReport();draw(v);
   boolean overflow=v.cityMap.panelLineCount>MapPanelContent.visibleLines(map);
   float y=map.panelTop+160;
   v.onTouchEvent(new MotionEvent(180*v.scale,y*v.scale,MotionEvent.ACTION_DOWN));
   v.onTouchEvent(new MotionEvent(180*v.scale,(y-72)*v.scale,MotionEvent.ACTION_MOVE));
   v.onTouchEvent(new MotionEvent(180*v.scale,(y-72)*v.scale,MotionEvent.ACTION_UP));
   require(overflow?v.cityMap.panelScroll>0:v.cityMap.panelScroll==0,"drag scroll respects actual result viewport");text(draw(v),"ВЕРНУТЬСЯ В УБЕЖИЩЕ");
   tap(v,180,map.panelBottom-40);require(e(v).state()==Expedition.State.RETURNING,"button reachable while scrolled");
   draw(v);float old=v.cityMap.displayProgress;v.game.advanceMinute();draw(v);require(v.cityMap.displayProgress<old,"return marker moves toward shelter");
   v.game.paused=true;float frozen=v.cityMap.displayProgress;draw(v);close(v.cityMap.displayProgress,frozen,"paused reverse marker freezes");
  }
  View.width=420;View.height=840;Context.preferences=new MemoryPreferences();Context.preferences.edit().putInt("day",7).putInt("count",1).putString("p0_id","old").putString("p0_job","Экспедиция").putInt("exp2_schema",1).putInt("exp2_count",1).putString("exp2_0_location","shop").putString("exp2_0_participants","old").putString("exp2_0_state","AT_LOCATION").putString("exp2_0_departure","9100").putInt("exp2_0_duration",45).putInt("exp2_0_elapsed",45).apply();
  v=load();require(e(v).state()==Expedition.State.EXPLORING&&e(v).elapsedMinutes()==0&&!e(v).resultGenerated,"Stage 2 arrival migration starts safe exploration");
  require(v.game.expeditionWarehouse.total()==0&&v.game.cityLocations.get(0).depletion()==0,"Stage 2 defaults safe");
  require(v.game.homeRoomFor(v.game.people.get(0))==-1&&Context.preferenceName.equals("save_v02"),"legacy membership/namespace preserved");
  System.out.println("PASS: "+checks+" loot/return assertions; 1/2/3-member cycles, exactly-once grants, all phase restarts, bonuses, capacity, events/injury, depletion, scrolling and Stage 2 saves.");
 }
}
'''


def main():
    sources = {"com/lastdom/game/" + p.name: p.read_text()
               for p in (fixtures.ROOT / fixtures.JAVA_PATH).glob("*.java")}
    sources["com/lastdom/game/R.java"] = "package com.lastdom.game; public class R {public static class drawable {public static final int shelter_clean=1,shelter_full_scene=2;}}"
    fixtures.PROBE = PROBE
    with tempfile.TemporaryDirectory(prefix="loot-return-regression-") as directory:
        print(fixtures.run_version(Path(directory), "loot-return", sources).strip())


if __name__ == "__main__":
    main()
