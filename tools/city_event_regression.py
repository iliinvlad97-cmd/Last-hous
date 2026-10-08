#!/usr/bin/env python3
"""Stage 4: actual Java controllers/save/Canvas input with Android test doubles.

Run: python3 tools/city_event_regression.py (Python 3/JDK 21).
Includes every choice, adverse/success rolls, process restart and exactly-once effects.
This does not replace physical Android testing.
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
 static int checks, choices;
 static void require(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
 static void close(double a,double b,String s){require(Math.abs(a-b)<.001,s);}
 static class Roll extends Random {
  final double value; Roll(double value){this.value=value;}
  public double nextDouble(){return value;}
  public int nextInt(int bound){return 0;}
 }
 static class Forbidden extends Random {
  public double nextDouble(){throw new AssertionError("unexpected reroll");}
  public int nextInt(int bound){throw new AssertionError("unexpected reroll");}
 }
 static GameView load(){GameView v=new GameView(new Context());v.game.rnd=new Roll(.999);draw(v);return v;}
 static GameView fresh(int w,int h){View.width=w;View.height=h;Context.preferences=new MemoryPreferences();GameView v=load();for(Resident r:v.game.people){r.health=100;r.fatigue=0;}return v;}
 static GameView kill(){TreeMap<String,Object> disk=new TreeMap<>(Context.preferences.values);Context.preferences=new MemoryPreferences();Context.preferences.values.putAll(disk);return load();}
 static Expedition e(GameView v){return v.game.expeditionController.active();}
 static Canvas draw(GameView v){Canvas c=new Canvas();v.onDraw(c);return c;}
 static void text(Canvas c,String s){require(c.commands.stream().anyMatch(x->x.contains(s)),"drawn: "+s);}
 static void tap(GameView v,float x,float y){v.onTouchEvent(new MotionEvent(x*v.scale,y*v.scale,MotionEvent.ACTION_UP));}
 static List<String> ids(GameView v,int... indices){List<String> list=new ArrayList<>();for(int i:indices)list.add(v.game.people.get(i).id);return list;}
 static void start(GameView v,String location,int... indices){require(v.game.expeditionController.start(location,ids(v,indices)).isEmpty(),"send squad");}
 // Advance the expedition controller only to isolate reward accounting from home production.
 static void until(GameView v,Expedition.State state){int guard=1000;while(e(v)!=null&&e(v).state()!=state&&guard-->0)v.game.expeditionController.advanceMinute();require(guard>0&&e(v)!=null&&e(v).state()==state,"reached "+state);v.game.save();}
 static String target(ExpeditionEvent.Type type){return type==ExpeditionEvent.Type.DANGEROUS_AREA?"police":"shop";}
 static void trigger(GameView v,ExpeditionEvent.Type type,int... indices){
  String location=target(type);start(v,location,indices);until(v,Expedition.State.EXPLORING);
  List<ExpeditionEvent.Type> eligible=ExpeditionEventConfig.eligible(v.game.expeditionController.location(location));
  final int size=eligible.size(),index=eligible.indexOf(type);require(index>=0,"event eligible "+type);
  v.game.rnd=new Roll(0){public int nextInt(int bound){return bound==size?index:bound-1;}};
  until(v,Expedition.State.AWAITING_DECISION);require(e(v).explorationEvent.type==type,"generated exact event "+type);
  require(e(v).cityEventChecked&&e(v).lootRolled&&!e(v).resultGenerated,"midpoint loot/event flags");
  require(e(v).elapsedMinutes()==e(v).phaseDurationMinutes/2,"event occurs at research midpoint");
  require(!e(v).explorationEvent.instanceId.isEmpty(),"unique event id");
 }
 static String quantities(ExpeditionLoot loot){String s="";for(ExpeditionLoot.Resource r:ExpeditionLoot.Resource.values())s+=r+"="+loot.get(r)+";";return s;}
 static String snapshot(GameView v){
  Expedition x=v.game.expeditions.get(0);ExpeditionEvent event=x.explorationEvent;String s=x.id+"/"+x.state()+"/"+x.elapsedMinutes()+"/"+x.phaseDurationMinutes+"/"+x.explorationDelay+"/"+x.cityRiskReduction+"/"+x.resultGenerated+"/"+x.rewardCredited+"/"+x.cityEventChecked+"/"+x.lootRolled;
  s+="/"+event.type+"/"+event.instanceId+"/"+event.chosenAction+"/"+event.effectsApplied+"/"+event.continued+"/"+event.outcome.message+"/"+event.outcome.delayMinutes+"/"+event.outcome.riskReduction+"/"+event.outcome.retreat;
  s+="/"+quantities(x.found)+quantities(x.cargo)+quantities(x.unsearchedLoot)+quantities(event.outcome.added)+quantities(event.outcome.lost);
  for(String id:x.participantIds){Resident r=v.game.expeditionController.resident(id);s+="/"+id+":"+r.health+":"+r.fatigue+":"+r.status+":"+event.outcome.healthLoss.getOrDefault(id,0)+":"+event.outcome.fatigueAdded.getOrDefault(id,0);}
  return s;
 }
 static int total(GameView v){return v.game.food+v.game.water+v.game.mats+v.game.expeditionWarehouse.total();}
 static void invariant(GameView v){
  Expedition x=v.game.expeditions.get(0);require(x.cargo.total()<=x.capacity(),"cargo never exceeds capacity");
  for(ExpeditionLoot.Resource r:ExpeditionLoot.Resource.values()){require(x.found.get(r)>=0&&x.cargo.get(r)>=0&&x.unsearchedLoot.get(r)>=0,"nonnegative loot");require(x.cargo.get(r)<=x.found.get(r),"cargo belongs to found loot");}
  for(Resident r:v.game.people)require(r.health>=1&&r.health<=100&&r.fatigue>=0&&r.fatigue<=100,"resident bounded, no deaths");
 }
 static void choice(ExpeditionEvent.Type type,int action,double roll,int number){
  choices++;GameView v=fresh(420,840);int[] indices=number==1?new int[]{0}:number==2?new int[]{0,1}:new int[]{0,1,2};trigger(v,type,indices);
  String instance=e(v).explorationEvent.instanceId;String waiting=snapshot(v);int resources=total(v);List<String> log=new ArrayList<>(v.game.log);
  v=kill();require(waiting.equals(snapshot(v)),"restore pending "+type);require(v.cityMap.eventPanel&&v.game.log.equals(log),"pending overlay restored, no duplicate log");
  require(v.game.screen==0&&!v.game.paused&&!v.game.event,"waiting overlay over home without global pause");
  for(String id:e(v).participantIds){Resident r=v.game.expeditionController.resident(id);require(v.game.homeRoomFor(r)==-1&&!v.game.assignJob(v.game.people.indexOf(r),"Еда"),"squad absent and unavailable");}
  v.game.rnd=new Forbidden();
  require(!v.game.expeditionController.chooseEvent("stale",action).isEmpty(),"stale id rejected");
  require(!v.game.expeditionController.chooseEvent(instance,-1).isEmpty(),"negative action rejected");
  require(!v.game.expeditionController.chooseEvent(instance,3).isEmpty(),"out of range rejected");
  require(!v.game.expeditionController.continueEvent(instance).isEmpty(),"continue cannot bypass decision");
  require(waiting.equals(snapshot(v)),"invalid input atomic");
  int duration=e(v).phaseDurationMinutes,elapsed=e(v).elapsedMinutes();long phaseStart=e(v).phaseStartMinute;
  v.game.rnd=new Roll(roll);require(v.game.expeditionController.chooseEvent(instance,action).isEmpty(),"choice accepted "+type+"/"+action);
  Expedition x=e(v);ExpeditionEventOutcome out=x.explorationEvent.outcome;
  require(x.explorationEvent.effectsApplied&&x.explorationEvent.chosenAction==action&&!out.message.isEmpty(),"generated and applied decision");
  require(x.elapsedMinutes()==elapsed&&x.phaseDurationMinutes==duration+out.delayMinutes&&x.phaseStartMinute==phaseStart,"delay extends only remaining exploration");
  require(total(v)==resources,"no home resources credited by choice");invariant(v);
  String applied=snapshot(v);log=new ArrayList<>(v.game.log);int commits=Context.preferences.commits;require(commits>0,"durable decision");
  v.game.rnd=new Forbidden();require(!v.game.expeditionController.chooseEvent(instance,action).isEmpty(),"double action rejected");require(applied.equals(snapshot(v)),"no second effects");
  v=kill();require(applied.equals(snapshot(v)),"restore result before continue");require(v.cityMap.eventPanel&&v.game.log.equals(log),"result overlay restored without duplicate journal");
  v.game.rnd=new Forbidden();require(!v.game.expeditionController.chooseEvent(instance,action).isEmpty(),"choice rejected after restart");require(applied.equals(snapshot(v)),"no injury/loot reroll after restart");
  for(int i=0;i<4;i++)v.game.expeditionController.advanceMinute();require(applied.equals(snapshot(v)),"result also freezes until continue");
  boolean retreat=e(v).explorationEvent.outcome.retreat;int carriedBefore=e(v).cargo.total(),foundBefore=e(v).found.total();
  require(v.game.expeditionController.continueEvent(instance).isEmpty(),"continue accepted");
  require(e(v).explorationEvent.continued,"continue persisted");require(!v.game.expeditionController.continueEvent(instance).isEmpty(),"double continue rejected");
  if(retreat){require(e(v).state()==Expedition.State.RETURNING&&e(v).resultGenerated,"early retreat begins reverse journey");require(e(v).found.total()==foundBefore&&e(v).cargo.total()==carriedBefore&&e(v).unsearchedLoot.total()==0,"retreat preserves only already found loot");}
  else {require(e(v).state()==Expedition.State.EXPLORING&&e(v).elapsedMinutes()==elapsed,"resume at same elapsed point");until(v,Expedition.State.AWAITING_RETURN);require(v.game.expeditionController.returnHome(e(v).id).isEmpty(),"return after research");}
  invariant(v);require(total(v)==resources,"not credited before return");v.game.expeditionController.advanceMinute();v.game.save();String returning=snapshot(v);v=kill();require(returning.equals(snapshot(v)),"restore returning with city event");
  int carried=e(v).cargo.total();v.game.rnd=new Forbidden();while(e(v)!=null)v.game.expeditionController.advanceMinute();v.game.save();
  Expedition complete=v.game.expeditions.get(0);require(complete.rewardCredited&&complete.state()==Expedition.State.COMPLETED,"returned and credited");require(total(v)==resources+carried,"exactly one resource credit after event");
  require(v.game.cityLocations.stream().filter(l->l.id.equals(complete.locationId)).findFirst().get().depletion()==10,"one depletion change");
  for(String id:complete.participantIds)require(v.game.expeditionController.resident(id).status==Resident.Status.HOME,"residents released");
  int awarded=total(v);for(int i=0;i<3;i++){v=kill();v.game.expeditionController.complete(v.game.expeditions.get(0));v.game.expeditionController.acknowledge(complete.id);require(total(v)==awarded,"never credit again after restart/ack");}invariant(v);
 }
 static ExpeditionEventOutcome resolved(ExpeditionEvent.Type type,int action,double roll,int... indices){
  GameView v=fresh(420,840);trigger(v,type,indices);v.game.rnd=new Roll(roll);String before=snapshot(v);ExpeditionEventOutcome result=ExpeditionEventResolver.resolve(v.game,e(v),action);require(before.equals(snapshot(v)),"resolver is pure");return result;
 }
 static int injury(ExpeditionEventOutcome out){return out.healthLoss.values().stream().mapToInt(Integer::intValue).sum();}
 static void roles(){
  require(resolved(ExpeditionEvent.Type.MARAUDERS,1,.6,0).lost.total()>0&&resolved(ExpeditionEvent.Type.MARAUDERS,1,.6,2).lost.total()==0,"guard negotiation changes outcome");
  require(injury(resolved(ExpeditionEvent.Type.MARAUDERS,2,.65,0))>0&&injury(resolved(ExpeditionEvent.Type.MARAUDERS,2,.65,2))==0,"guard escape changes outcome");
  close(injury(resolved(ExpeditionEvent.Type.INFECTED,1,.999,0,1)),injury(resolved(ExpeditionEvent.Type.INFECTED,1,.999,0))*.5,"doctor halves injury");
  require(resolved(ExpeditionEvent.Type.WOUNDED_SURVIVOR,0,0,1).delayMinutes<resolved(ExpeditionEvent.Type.WOUNDED_SURVIVOR,0,0,0).delayMinutes,"doctor helps faster");
  require(injury(resolved(ExpeditionEvent.Type.LOCKED_ROOM,0,.6,0))>0&&resolved(ExpeditionEvent.Type.LOCKED_ROOM,0,.6,4).added.total()>0,"mechanic opens door");
  require(resolved(ExpeditionEvent.Type.WAREHOUSE,0,.999,4).added.total()>resolved(ExpeditionEvent.Type.WAREHOUSE,0,.999,0).added.total(),"mechanic finds warehouse materials");
  require(resolved(ExpeditionEvent.Type.COLLAPSE,0,0,0).delayMinutes==5&&injury(resolved(ExpeditionEvent.Type.COLLAPSE,0,0,0))==0,"engineer safe short bypass");
  require(resolved(ExpeditionEvent.Type.COLLAPSE,0,0,1).delayMinutes==20&&injury(resolved(ExpeditionEvent.Type.COLLAPSE,0,0,1))>0,"non-engineer slower risky bypass");
  require(resolved(ExpeditionEvent.Type.CACHE,0,.999,3).added.total()>resolved(ExpeditionEvent.Type.CACHE,0,.999,0).added.total(),"gatherer finds more cache supplies");
  require(injury(resolved(ExpeditionEvent.Type.INFECTED,0,.65,0))>0&&injury(resolved(ExpeditionEvent.Type.INFECTED,0,.65,3))==0,"gatherer safer passage");
  require(injury(resolved(ExpeditionEvent.Type.DANGEROUS_AREA,0,.3,0))>0&&injury(resolved(ExpeditionEvent.Type.DANGEROUS_AREA,1,.3,0))==0,"safe route really lowers collision probability");
  GameView v=fresh(420,840);trigger(v,ExpeditionEvent.Type.INFECTED,0);v.game.people.get(0).health=1;v.game.people.get(0).fatigue=99;v.game.rnd=new Roll(.999);String id=e(v).explorationEvent.instanceId;v.game.expeditionController.chooseEvent(id,1);require(v.game.people.get(0).health==1&&v.game.people.get(0).fatigue==100,"injury never kills, fatigue clamped");String state=snapshot(v);v=kill();require(state.equals(snapshot(v))&&e(v).explorationEvent.outcome.healthLoss.containsValue(0),"zero injury survives restart");
 }
 static void clock(){
  GameView v=fresh(420,840);v.game.assignJob(4,"Вода");trigger(v,ExpeditionEvent.Type.CACHE,0);v.cityMap.openPendingReport();draw(v);int elapsed=e(v).elapsedMinutes(),minute=v.game.gameMinute;
  for(int speed:new int[]{1,2,4}){v.game.speed=speed;int before=v.game.gameMinute;v.tick.run();require(v.game.gameMinute==before+speed,"global clock continues at speed "+speed);require(e(v).elapsedMinutes()==elapsed&&!e(v).explorationEvent.effectsApplied,"no automatic decision at speed "+speed);}
  int water=v.game.water,health=v.game.people.get(0).health,fatigue=v.game.people.get(0).fatigue;v.game.processJobs();require(v.game.water>water&&v.game.people.get(0).health==health&&v.game.people.get(0).fatigue==fatigue,"home production continues while waiting, squad excluded");
  require(v.game.people.get(4).job.equals("Вода")&&v.game.people.get(4).status==Resident.Status.HOME,"remaining resident still works");
  v.game.paused=true;minute=v.game.gameMinute;v.tick.run();require(v.game.gameMinute==minute&&e(v).elapsedMinutes()==elapsed,"manual pause preserved");v.game.save();v=kill();require(v.cityMap.eventPanel&&v.game.paused,"pending panel restored even on paused home screen");
  v.game.paused=false;v.cityMap.closeSelection();v.game.screen=GameView.CITY_MAP;v.cityMap.openPendingReport();CityMapLayout layout=new CityMapLayout(v.H/v.scale);tap(v,180,layout.bottom-39);require(v.cityMap.eventPanel,"pending event reopen from map indicator");
  v.cityMap.closeSelection();v.game.screen=0;for(int i=0;i<3;i++)v.tick.run();require(e(v).state()==Expedition.State.AWAITING_DECISION,"tab changes cannot bypass waiting");
  v=fresh(420,840);start(v,"shop",0);until(v,Expedition.State.EXPLORING);until(v,Expedition.State.AWAITING_RETURN);require(e(v).cityEventChecked&&!e(v).explorationEvent.interactive(),"event miss checked once");v=kill();v.game.rnd=new Forbidden();for(int i=0;i<5;i++)v.game.expeditionController.advanceMinute();require(!e(v).explorationEvent.interactive(),"miss never rerolled");
 }
 static void ui(){
  for(int width:new int[]{420,840})for(int height:new int[]{640,840,1200})for(ExpeditionEvent.Type type:ExpeditionEventConfig.eligible(CityMapController.defaultLocations().get(3))){
   GameView v=fresh(width,height*width/420);trigger(v,type,0,1,2);v.cityMap.openPendingReport();Canvas c=draw(v);text(c,ExpeditionEventConfig.title(type));CityMapLayout map=new CityMapLayout(height);ExpeditionEventLayout panel=new ExpeditionEventLayout(map,e(v).explorationEvent);
   require(panel.firstChoice>map.panelTop+93&&panel.top(2)+panel.CHOICE_HEIGHT<map.panelBottom,"buttons inside panel");
   for(int action=0;action<3;action++){require(panel.hit(32,panel.top(action)+1)==action&&panel.hit(389,panel.top(action)+53)==action,"choice hit corners use render transform");text(c,(action+1)+". "+ExpeditionEventConfig.actions(type)[action]);}
   require(panel.hit(29,panel.top(0)+20)==-1&&panel.hit(391,panel.top(0)+20)==-1,"outside buttons rejected");
   require(map.panelBottom-panel.footer+9<=panel.firstChoice-9,"scroll hint stays above buttons");
   int visible=MapPanelContent.visibleLines(map,panel.footer);require(map.panelTop+93+(visible-1)*MapPanelContent.LINE_HEIGHT<panel.firstChoice-10,"body does not overlap choice footer");
   float y=map.panelTop+110;v.onTouchEvent(new MotionEvent(180*v.scale,y*v.scale,MotionEvent.ACTION_DOWN));v.onTouchEvent(new MotionEvent(180*v.scale,(y-72)*v.scale,MotionEvent.ACTION_MOVE));v.onTouchEvent(new MotionEvent(180*v.scale,(panel.top(0)+20)*v.scale,MotionEvent.ACTION_UP));require(!e(v).explorationEvent.effectsApplied,"scroll release never chooses action");require(v.cityMap.panelLineCount>visible?v.cityMap.panelScroll>0:v.cityMap.panelScroll==0,"scroll follows actual body viewport");
   v.game.rnd=new Roll(.999);tap(v,180,panel.top(1)+27);require(e(v).explorationEvent.effectsApplied,"scaled choice button works");c=draw(v);text(c,"ПРОДОЛЖИТЬ");ExpeditionEventLayout result=new ExpeditionEventLayout(map,e(v).explorationEvent);tap(v,180,result.top(0)+27);require(e(v).state()==Expedition.State.EXPLORING&&!v.cityMap.eventPanel,"continue button works while scrolled");
  }
 }
 static void legacy(){
  for(ExpeditionEvent.Type legacy:new ExpeditionEvent.Type[]{ExpeditionEvent.Type.CACHE,ExpeditionEvent.Type.INJURY,ExpeditionEvent.Type.DAMAGED,ExpeditionEvent.Type.THREAT}){
   GameView v=fresh(420,840);start(v,"shop",0);until(v,Expedition.State.AWAITING_RETURN);e(v).explorationEvent=new ExpeditionEvent(legacy,"Старое событие",v.game.people.get(0).id,12);v.game.people.get(0).health=88;v.game.save();
   Context.preferences.values.keySet().removeIf(k->k.startsWith("exp2_0_city")||k.startsWith("exp2_0_unsearched")||k.equals("exp2_0_lootRolled")||k.equals("exp2_0_explorationDelay")||k.equals("exp2_0_riskReduction"));
   int cargo=e(v).cargo.total();v=kill();require(e(v).cityEventChecked&&e(v).lootRolled&&!e(v).explorationEvent.interactive(),"Stage 3 automatic result preserved");require(v.game.people.get(0).health==88&&e(v).cargo.total()==cargo,"Stage 3 effects not reapplied");v.game.rnd=new Forbidden();v.game.expeditionController.returnHome(e(v).id);while(e(v)!=null)v.game.expeditionController.advanceMinute();require(v.game.people.get(0).health==88,"legacy injury not repeated on return");
  }
  GameView v=fresh(420,840);start(v,"shop",0);until(v,Expedition.State.EXPLORING);Context.preferences.values.keySet().removeIf(k->k.startsWith("exp2_0_city")||k.equals("exp2_0_lootRolled"));v=kill();require(!e(v).cityEventChecked&&!e(v).lootRolled&&!v.cityMap.eventPanel,"old research resumes with safe defaults");
  require(Context.preferenceName.equals("save_v02"),"old namespace retained");
 }
 public static void main(String[] args){
  for(MapLocation location:CityMapController.defaultLocations()){
   close(ExpeditionEventConfig.probability(location),location.risk==MapLocation.Risk.LOW?.15:location.risk==MapLocation.Risk.LOW_MEDIUM?.25:location.risk==MapLocation.Risk.MEDIUM?.35:.5,"configured frequency");
   List<ExpeditionEvent.Type> eligible=ExpeditionEventConfig.eligible(location);require(new HashSet<>(eligible).size()==eligible.size(),"unique event types");
   require(eligible.contains(ExpeditionEvent.Type.DANGEROUS_AREA)==(location.risk==MapLocation.Risk.MEDIUM||location.risk==MapLocation.Risk.HIGH),"dangerous area risk condition");
   require(eligible.contains(ExpeditionEvent.Type.INFECTED)==(location.kind==MapLocation.Kind.STORE||location.kind==MapLocation.Kind.PHARMACY||location.kind==MapLocation.Kind.HOSPITAL),"infected building condition");
  }
  for(ExpeditionEvent.Type type:new ExpeditionEvent.Type[]{ExpeditionEvent.Type.MARAUDERS,ExpeditionEvent.Type.INFECTED,ExpeditionEvent.Type.COLLAPSE,ExpeditionEvent.Type.CACHE,ExpeditionEvent.Type.WOUNDED_SURVIVOR,ExpeditionEvent.Type.LOCKED_ROOM,ExpeditionEvent.Type.DANGEROUS_AREA,ExpeditionEvent.Type.WAREHOUSE})for(int action=0;action<3;action++)for(double roll:new double[]{0,.999})for(int n=1;n<=3;n++)choice(type,action,roll,n);
  roles();clock();ui();legacy();System.out.println("PASS: "+checks+" city-event assertions; "+choices+" full event/choice/roll/squad cycles, profession effects, durable decisions, exactly-once injuries/loot, independent clock, early return, capacity, legacy saves and scaled scrolling/touch.");
 }
}
'''


def main():
    sources = {"com/lastdom/game/" + p.name: p.read_text()
               for p in (fixtures.ROOT / fixtures.JAVA_PATH).glob("*.java")}
    sources["com/lastdom/game/R.java"] = "package com.lastdom.game; public class R {public static class drawable {public static final int shelter_clean=1,shelter_full_scene=2;}}"
    fixtures.PROBE = PROBE
    with tempfile.TemporaryDirectory(prefix="city-event-regression-") as directory:
        print(fixtures.run_version(Path(directory), "city-events", sources).strip())


if __name__ == "__main__":
    main()
