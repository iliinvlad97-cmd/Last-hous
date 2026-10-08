#!/usr/bin/env python3
"""Stage 7 actual Java logic, save_v02 process restarts, Canvas/touch tests and 20-day simulations.
Uses Android doubles, not a physical Android device.
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
 static void require(boolean ok,String what){if(!ok)throw new AssertionError(what);checks++;}
 static void close(double a,double b,String what){require(Math.abs(a-b)<.000001,what+": "+a+" != "+b);}
 static class Quiet extends Random {public int nextInt(int n){return n-1;}public double nextDouble(){return .999;}}
 static class NoRandom extends Random {public int nextInt(int n){throw new AssertionError("unexpected random roll");}public double nextDouble(){throw new AssertionError("unexpected random roll");}public long nextLong(){throw new AssertionError("unexpected seed roll");}}
 static GameView load(){GameView v=new GameView(new Context());v.game.rnd=new Quiet();draw(v);return v;}
 static GameView fresh(){View.width=420;View.height=840;Context.preferences=new MemoryPreferences();GameView v=load();v.game.day=2;v.game.raidController.checkedDay=2;v.game.food=v.game.water=v.game.mats=100;for(Resident r:v.game.people){r.fatigue=r.hunger=r.thirst=0;r.morale=100;}v.game.save();return v;}
 static GameView kill(){TreeMap<String,Object> disk=new TreeMap<>(Context.preferences.values);Context.preferences=new MemoryPreferences();Context.preferences.values.putAll(disk);return load();}
 static Canvas draw(GameView v){Canvas c=new Canvas();v.onDraw(c);return c;}
 static void text(Canvas c,String s){require(c.commands.stream().anyMatch(x->x.contains(s)),"drawn: "+s);}
 static void tap(GameView v,float x,float y){v.onTouchEvent(new MotionEvent(x*v.scale,y*v.scale,MotionEvent.ACTION_UP));}
 static void minutes(GameView v,int n){for(int i=0;i<n;i++)v.game.advanceMinute();}
 static String snapshot(GameView v){v.game.save();return Context.preferences.values.toString();}
 static RaidState raid(GameView v){return v.game.raidController.latest();}
 static void discover(GameView v,RaidState.Enemy enemy,int power,long seed){require(v.game.raidController.discover(enemy,power,seed),"deterministic threat launch");}
 static void invariant(GameView v){
  RaidController c=v.game.raidController;require(c.durability>=0&&c.durability<=100,"durability bounded");require(v.game.food>=0&&v.game.water>=0&&v.game.mats>=0,"resources nonnegative");
  Set<String> ids=new HashSet<>();for(Resident r:v.game.people){require(ids.add(r.id),"unique resident");require(r.alive,"no death");for(int n:new int[]{r.health,r.hunger,r.thirst,r.fatigue,r.morale})require(n>=0&&n<=100,"bounded condition");int busy=(v.game.isOnExpedition(r)?1:0)+(v.game.isBuilding(r)?1:0)+(v.game.isDefending(r)?1:0);require(busy<=1,"exclusive assignments");if(v.game.isDefending(r)||v.game.isBuilding(r))require(!v.game.survivalController.working(r)&&!v.game.survivalController.resting(r),"busy resident not producing/resting");}
 }
 static void frequency(){
  GameView v=fresh();v.game.day=1;v.game.gameMinute=0;v.game.raidController.checkedDay=0;
  require(!v.game.raidController.discover(RaidState.Enemy.MARAUDERS,30,1),"day1 safe, even deterministic request");minutes(v,1439);require(v.game.raidController.raids.isEmpty(),"no first-day threats");
  v.game.rnd=new Quiet(){public int nextInt(int n){return 0;}public long nextLong(){return 713;}};v.game.advanceMinute();require(raid(v)!=null&&raid(v).phase==RaidState.Phase.WARNING&&raid(v).remaining()==60,"day2 saved warning before attack");require(v.game.raidController.lastAttackMinute<0,"warning not yet an attack");
  String s=snapshot(v);v=kill();require(s.equals(snapshot(v)),"warning process restart exact");minutes(v,59);require(raid(v).phase==RaidState.Phase.WARNING,"full preparation interval");minutes(v,1);require(raid(v).phase==RaidState.Phase.ATTACK&&raid(v).attackElapsed==0,"attack begins only after 60 minutes");minutes(v,30);require(raid(v).effectsApplied,"battle finishes");
  long last=v.game.raidController.lastAttackMinute;require(!v.game.raidController.discover(RaidState.Enemy.INFECTED,30,1),"one threat per day");v.game.day=(int)((last+1439)/1440)+1;v.game.gameMinute=(int)((last+1439)%1440);require(!v.game.raidController.discover(RaidState.Enemy.INFECTED,30,1),"minimum24h interval");v.game.gameMinute++;require(v.game.raidController.discover(RaidState.Enemy.INFECTED,30,1),"new threat after minimum interval on next day");
  v=fresh();v.game.raidController.checkedDay=0;v.game.raidController.checkDailyThreat();require(raid(v)==null&&v.game.raidController.checkedDay==2,"miss evaluated once");v.game.save();v=kill();v.game.rnd=new NoRandom();v.game.raidController.checkDailyThreat();require(raid(v)==null,"daily miss not rerolled on restart");
  require(RaidConfig.chance(1)==0&&RaidConfig.chance(2)==20&&RaidConfig.chance(3)==20&&RaidConfig.chance(4)==35&&RaidConfig.chance(6)==35&&RaidConfig.chance(7)==50,"configured risk bands");
  v=fresh();v.game.day=1;v.game.rnd=new Quiet(){public int nextInt(int n){return n==5?3:n-1;}};int food=v.game.food;v.game.triggerEvent();require(!v.game.event&&raid(v)==null&&v.game.food==food,"legacy instant raid removed and day1 safe");
 }
 static void defenders(){
  GameView v=fresh();discover(v,RaidState.Enemy.MARAUDERS,35,77);Resident guard=v.game.people.get(2),engineer=v.game.people.get(0);
  close(RaidResolver.residentPower(guard),24,"guard real 50% bonus");close(RaidResolver.residentPower(engineer),16,"engineer baseline");
  guard.health=50;close(RaidResolver.residentPower(guard),12,"health factor");guard.health=100;guard.fatigue=75;close(RaidResolver.residentPower(guard),15.6,"fatigue factor");guard.fatigue=0;guard.morale=30;close(RaidResolver.residentPower(guard),19.2,"morale factor");guard.morale=100;guard.hunger=75;close(RaidResolver.residentPower(guard),15.6,"hunger factor");guard.hunger=0;guard.thirst=95;require(!v.game.raidController.toggleDefender(guard.id).isEmpty(),"critical thirst excluded");guard.thirst=0;
  for(int value:new int[]{0,20,29}){guard.health=value;String s=snapshot(v);require(!v.game.raidController.toggleDefender(guard.id).isEmpty()&&s.equals(snapshot(v)),"low health rejected atomically");}guard.health=100;
  engineer.job="Материалы";require(v.game.expeditionController.start("shop",Arrays.asList(engineer.id)).isEmpty(),"expedition start before defense");require(v.game.raidController.defenderReason(engineer).equals("В экспедиции"),"away unavailable");
  Resident builder=v.game.people.get(4);require(v.game.roomUpgradeController.start(0,builder.id).isEmpty(),"upgrade starts before defense");require(!v.game.raidController.toggleDefender(builder.id).isEmpty(),"builder cannot defend");
  guard.job="Отдых";guard.autoRecovery=true;guard.resumeJob="Охрана";guard.fatigue=20;
  require(v.game.raidController.toggleDefender(guard.id).isEmpty(),"explicit defense replaces rest");require(v.game.isDefending(guard)&&guard.status==Resident.Status.DEFENDING&&!guard.autoRecovery&&v.game.homeRoomFor(guard)==4,"defender membership/location");
  require(!v.game.survivalController.working(guard)&&!v.game.survivalController.resting(guard)&&v.game.survivalController.workPercent(guard)==0,"no duplicate production/rest");
  require(!v.game.assignJob(2,"Еда")&&!v.game.expeditionController.start("garage",Arrays.asList(guard.id)).isEmpty()&&!v.game.roomUpgradeController.unavailableReason(guard).isEmpty(),"defender excluded from work/expedition/building");
  require(v.game.findBestResident("Охрана","")!=2,"AI cannot steal defender");String state=snapshot(v);v=kill();guard=v.game.people.get(2);require(state.equals(snapshot(v))&&v.game.isDefending(guard),"defender and prior rest restore");
  require(v.game.raidController.toggleDefender(guard.id).isEmpty()&&guard.job.equals("Отдых")&&guard.autoRecovery&&guard.resumeJob.equals("Охрана"),"release restores recovery state");
  require(v.game.raidController.toggleDefender(guard.id).isEmpty(),"rejoin defense");minutes(v,60);int count=raid(v).defenders.size();require(!v.game.raidController.toggleDefender(v.game.people.get(1).id).isEmpty()&&raid(v).defenders.size()==count,"roster locked during combat");invariant(v);
  v=fresh();discover(v,RaidState.Enemy.INFECTED,35,2);guard=v.game.people.get(2);v.game.raidController.toggleDefender(guard.id);guard.health=29;minutes(v,60);require(!raid(v).defenders.containsKey(guard.id)&&!v.game.isDefending(guard),"worsened defender released before attack");
  close(RaidResolver.barricadePower(1,100),24,"base barricade");close(RaidResolver.barricadePower(2,100),28.8,"existing level2 defense bonus");close(RaidResolver.barricadePower(3,100),33.6,"existing level3 bonus once");close(RaidResolver.barricadePower(3,25),8.4,"durability scales power");
 }
 static void combat(){
  for(RaidState.Enemy enemy:RaidState.Enemy.values())for(int power:new int[]{15,30,100}){
   GameView v=fresh();discover(v,enemy,power,243);RaidState r=raid(v);String old=snapshot(v);RaidResolver.Result a=RaidResolver.resolve(v.game,r,24),b=RaidResolver.resolve(v.game,r,24);require(old.equals(snapshot(v))&&a.outcome==b.outcome&&a.damage==b.damage&&a.injuries.equals(b.injuries),"seeded resolver pure and reproducible");
   minutes(v,60);require(r.resultGenerated&&!r.effectsApplied&&r.phase==RaidState.Phase.ATTACK,"result frozen once at battle start");require(r.outcome==(power==15?RaidState.Outcome.DEFENDED:power==30?RaidState.Outcome.PARTIAL_BREACH:RaidState.Outcome.DEFEAT),"all outcomes reflect defense ratio");
   int initial=v.game.raidController.durability,food=v.game.food,water=v.game.water,mats=v.game.mats;
   v.game.rnd=new NoRandom();minutes(v,15);require(v.game.raidController.durability==initial-r.plannedDamage/2,"damage progresses in game minutes");require(v.game.food==food&&v.game.water==water&&v.game.mats==mats,"no theft before finish");String during=snapshot(v);v=kill();require(during.equals(snapshot(v)),"attack midpoint exact restart");v.game.rnd=new NoRandom();r=raid(v);minutes(v,15);
   require(r.phase==RaidState.Phase.RESULT&&r.effectsApplied&&v.game.raidController.durability==initial-r.plannedDamage,"battle completes damage once");
   require(v.game.food==food-r.foodLost&&v.game.water==water-r.waterLost&&v.game.mats==mats-r.materialsLost,"theft credited exactly once at end");
   require(enemy==RaidState.Enemy.MARAUDERS||r.foodLost+r.waterLost+r.materialsLost==0,"infected never steal");
   if(power==15)require(r.foodLost+r.waterLost+r.materialsLost==0,"successful defense no theft");
   if(power==100)require(!r.injuries.isEmpty(),"defeat injuries generated");
   String finished=snapshot(v);v.game.raidController.applyResult(r);require(finished.equals(snapshot(v)),"cannot repeat combat effects");v=kill();require(finished.equals(snapshot(v)),"finished report restart exact");r=raid(v);v.game.raidController.applyResult(r);require(finished.equals(snapshot(v)),"cannot replay effects after restart");
   List<String> journal=new ArrayList<>(v.game.log);v.defensePanel.openStatus();draw(v);draw(v);require(finished.equals(snapshot(v))&&journal.equals(v.game.log),"reopening read-only report no losses/logs");
   v.game.raidController.acknowledge(r);String ack=snapshot(v);v.game.raidController.acknowledge(r);require(r.phase==RaidState.Phase.COMPLETED&&ack.equals(snapshot(v)),"acknowledge idempotent");v=kill();require(ack.equals(snapshot(v)),"acknowledged report reload");invariant(v);
  }
  GameView v=fresh();v.game.food=v.game.water=v.game.mats=0;v.game.raidController.durability=1;discover(v,RaidState.Enemy.MARAUDERS,100,5);minutes(v,90);require(v.game.raidController.durability==0,"zero durability bounded");invariant(v);
  v=fresh();for(Resident resident:v.game.people)resident.health=1;discover(v,RaidState.Enemy.INFECTED,100,5);minutes(v,90);for(Resident resident:v.game.people)require(resident.health>=1&&resident.alive,"raid never kills");
 }
 static void clocks(){
  for(int speed:new int[]{1,2,4}){
   GameView v=fresh();discover(v,RaidState.Enemy.INFECTED,30,12);v.game.speed=speed;
   for(int i=0;i<60/speed;i++)v.tick.run();require(raid(v).phase==RaidState.Phase.ATTACK&&raid(v).attackElapsed==0,"preparation at speed "+speed);
   v.game.paused=true;String paused=snapshot(v);for(int i=0;i<20;i++)v.tick.run();require(paused.equals(snapshot(v)),"pause freezes warning/attack/resources");
   v.game.paused=false;for(int i=0;i<7;i++)v.tick.run();require(raid(v).attackElapsed==7*speed,"attack clock speed "+speed);invariant(v);
  }
  GameView v=fresh();discover(v,RaidState.Enemy.MARAUDERS,30,40);for(int speed:new int[]{1,2,4,1,4}){v.game.speed=speed;for(int i=0;i<5;i++)v.tick.run();v.game.save();v=kill();}require(raid(v).phase==RaidState.Phase.ATTACK&&raid(v).attackElapsed==0,"speed switching/restart totals60 minutes");
 }
 static void repair(){
  GameView v=fresh();Resident builder=v.game.people.get(0);require(!v.game.raidController.startRepair(builder.id).isEmpty(),"full durability cannot repair");v.game.raidController.durability=40;v.game.mats=4;String old=snapshot(v);require(!v.game.raidController.startRepair(builder.id).isEmpty()&&old.equals(snapshot(v)),"insufficient materials atomic");v.game.mats=20;builder.job="Материалы";
  require(v.game.raidController.startRepair(builder.id).isEmpty()&&v.game.mats==15,"repair payment5 once");require(v.game.isBuilding(builder)&&v.game.homeRoomFor(builder)==4&&!v.game.survivalController.working(builder),"repair builder on barricade floor, no production");
  require(!v.game.expeditionController.start("shop",Arrays.asList(builder.id)).isEmpty()&&!v.game.roomUpgradeController.start(0,v.game.people.get(4).id).isEmpty(),"repair blocks expeditions and simultaneous upgrades");int mats=v.game.mats;require(!v.game.raidController.startRepair(v.game.people.get(4).id).isEmpty()&&v.game.mats==mats,"second repair rejected without payment");
  minutes(v,20);String saved=snapshot(v);v=kill();require(saved.equals(snapshot(v))&&v.game.isBuilding(v.game.people.get(0))&&v.game.raidController.repair.remaining()==40,"repair restart no repay");
  v.game.paused=true;String pause=snapshot(v);for(int i=0;i<10;i++)v.tick.run();require(pause.equals(snapshot(v)),"repair paused");v.game.paused=false;v.game.speed=4;for(int i=0;i<10;i++)v.tick.run();require(v.game.raidController.durability==65&&v.game.raidController.repair.completed&&v.game.people.get(0).job.equals("Материалы"),"repair completes25 exactly at60 game minutes");
  String completed=snapshot(v);v=kill();require(completed.equals(snapshot(v)),"completed repair reload no grant/payment");minutes(v,1);require(v.game.raidController.durability==65&&v.game.mats==15,"no duplicate repair grant");
  v=fresh();v.game.raidController.durability=90;require(v.game.raidController.startRepair(v.game.people.get(0).id).isEmpty(),"partial final repair allowed");minutes(v,60);require(v.game.raidController.durability==100,"repair clamps100");
  for(int speed:new int[]{1,2,4}){v=fresh();v.game.raidController.durability=50;v.game.raidController.startRepair(v.game.people.get(0).id);v.game.speed=speed;for(int i=0;i<60/speed;i++)v.tick.run();require(v.game.raidController.durability==75,"repair speed "+speed);}
  v=fresh();discover(v,RaidState.Enemy.INFECTED,40,8);v.game.raidController.durability=50;v.game.raidController.toggleDefender(v.game.people.get(2).id);require(!v.game.raidController.startRepair(v.game.people.get(2).id).isEmpty(),"defender cannot repair");require(v.game.raidController.startRepair(v.game.people.get(0).id).isEmpty(),"repair during prep");minutes(v,60);require(v.game.raidController.durability==75&&raid(v).durabilityAtStart==75,"repair before attack affects frozen defense");require(!v.game.raidController.startRepair(v.game.people.get(4).id).isEmpty(),"cannot start repair during attack");invariant(v);
 }
 static void expeditionIntegration(){
  GameView v=fresh();Resident away=v.game.people.get(0);v.game.expeditionController.start("shop",Arrays.asList(away.id));Expedition e=v.game.expeditionController.active();
  while(e.state()!=Expedition.State.AWAITING_RETURN)v.game.expeditionController.advanceMinute();v.game.expeditionController.returnHome(e.id);for(int i=0;i<e.durationMinutes-10;i++)v.game.expeditionController.advanceMinute();
  discover(v,RaidState.Enemy.INFECTED,35,8);require(!v.game.raidController.toggleDefender(away.id).isEmpty(),"away cannot defend");minutes(v,10);require(e.rewardCredited&&!v.game.isOnExpedition(away),"expedition returns during prep");require(v.game.raidController.toggleDefender(away.id).isEmpty(),"returned resident can explicitly join preparation");int stock=v.game.food;v.game.expeditionController.complete(e);require(v.game.food==stock,"expedition return still idempotent");
  String state=snapshot(v);v=kill();require(state.equals(snapshot(v)),"completed expedition plus defense restore");
  v=fresh();away=v.game.people.get(0);v.game.expeditionController.start("shop",Arrays.asList(away.id));e=v.game.expeditionController.active();while(e.state()!=Expedition.State.AWAITING_RETURN)v.game.expeditionController.advanceMinute();v.game.expeditionController.returnHome(e.id);discover(v,RaidState.Enemy.MARAUDERS,40,8);minutes(v,65);require(e.rewardCredited&&raid(v).phase==RaidState.Phase.ATTACK&&raid(v).defenders.isEmpty(),"return during attack doesn't auto join");require(!v.game.raidController.toggleDefender(away.id).isEmpty(),"returner cannot join frozen combat");invariant(v);
 }

 static void edgeCases(){
  GameView v=fresh();discover(v,RaidState.Enemy.MARAUDERS,40,243);Resident guard=v.game.people.get(2),repairer=v.game.people.get(0);
  guard.hunger=90;String old=snapshot(v);require(!v.game.raidController.toggleDefender(guard.id).isEmpty()&&old.equals(snapshot(v)),"critical hunger rejection atomic");guard.hunger=0;
  v.game.raidController.durability=50;v.game.raidController.toggleDefender(guard.id);require(v.game.raidController.startRepair(repairer.id).isEmpty(),"parallel defense/prep and repair allowed");minutes(v,20);String saved=snapshot(v);v=kill();require(saved.equals(snapshot(v))&&v.game.isDefending(v.game.people.get(2))&&v.game.isBuilding(v.game.people.get(0)),"defender plus repairer roles/progress/needs restore atomically");minutes(v,40);require(raid(v).phase==RaidState.Phase.ATTACK&&v.game.raidController.repair.completed,"repair and defense timers share game minute");
  v=fresh();discover(v,RaidState.Enemy.MARAUDERS,100,243);minutes(v,60);RaidState r=raid(v);String victim=r.injuries.keySet().iterator().next();Resident departing=v.game.expeditionController.resident(victim);int health=departing.health;require(v.game.expeditionController.start("shop",Arrays.asList(victim)).isEmpty(),"unassigned home resident can leave during attack");minutes(v,30);require(departing.health==health&&r.injuries.get(victim)==0,"report records actual zero injury for absent resident");
  v=fresh();discover(v,RaidState.Enemy.MARAUDERS,100,243);minutes(v,60);r=raid(v);v.game.food=1;v.game.water=2;v.game.mats=0;minutes(v,30);require(r.foodLost<=1&&r.waterLost<=2&&r.materialsLost==0,"losses bounded by stocks at application, never negative");invariant(v);
  for(int level=1;level<=3;level++){v=fresh();v.game.roomLevels[4]=level;discover(v,RaidState.Enemy.INFECTED,100,6);minutes(v,60);v.game.raidController.durability=0;String during=snapshot(v);v=kill();require(during.equals(snapshot(v)),"durability snapshot not reconstructed from room condition");minutes(v,30);invariant(v);}
  require(RaidConfig.attackPercent(RaidState.Enemy.MARAUDERS)==100&&RaidConfig.attackPercent(RaidState.Enemy.INFECTED)==90,"enemy-specific attack balance centralized");
  v=fresh();v.game.overlay=1;v.game.selected=0;discover(v,RaidState.Enemy.MARAUDERS,35,1);v.defensePanel.pending();require(v.defensePanel.open&&v.game.overlay==1&&v.game.selected==0,"threat shows above resident panel without losing selection");v.defensePanel.open=false;v.defensePanel.pending();require(!v.defensePanel.open&&v.game.overlay==1,"dismiss returns to preserved resident panel");
  v=fresh();v.cityMap.eventPanel=true;discover(v,RaidState.Enemy.INFECTED,35,1);v.defensePanel.pending();require(v.defensePanel.open&&v.cityMap.eventPanel,"threat warns above pending city decision, doesn't lose it");v.defensePanel.open=false;v.defensePanel.pending();require(!v.defensePanel.open&&v.cityMap.eventPanel,"city event remains pending after warning closes");
  v=fresh();discover(v,RaidState.Enemy.MARAUDERS,35,1);v.game.selected=2;v.game.jobMenu=true;v.defensePanel.pending();require(v.defensePanel.open&&v.game.jobMenu,"warning preserves interrupted job menu");v.game.raidController.toggleDefender(v.game.people.get(2).id);v.defensePanel.open=false;tap(v,180,165+45+18);require(v.game.people.get(2).job.equals("Оборона")&&v.game.log.get(0).contains("Назначение недоступно"),"stale job menu cannot assign defender or claim successful assignment");
  v=fresh();discover(v,RaidState.Enemy.INFECTED,35,1);v.defensePanel.openStatus();v.defensePanel.open=false;v.defensePanel.pending();require(!v.defensePanel.open,"manually viewed warning stays dismissed");
  v=fresh();discover(v,RaidState.Enemy.MARAUDERS,35,1);v.defensePanel.openStatus();v.defensePanel.mode=DefensePanelController.Mode.DEFENDERS;minutes(v,60);v.defensePanel.pending();require(v.defensePanel.mode==DefensePanelController.Mode.STATUS,"open defender list becomes battle status automatically");minutes(v,30);v.defensePanel.pending();v.defensePanel.open=false;v.defensePanel.pending();require(!v.defensePanel.open,"already viewed result stays dismissed without acknowledgement");v.defensePanel.openStatus();text(draw(v),"РЕЗУЛЬТАТ ОБОРОНЫ");

 }
 static void saves(){
  Context.preferences=new MemoryPreferences();Context.preferences.edit().putInt("day",7).putInt("count",1).putString("p0_name","Старый").putInt("p0_health",74).putInt("p0_fatigue",31).putInt("p0_hunger",18).putInt("p0_thirst",24).putInt("p0_morale",66).putInt("mats",12).apply();GameView v=load();Resident r=v.game.people.get(0);
  require(v.game.raidController.durability==100&&raid(v)==null&&!v.game.raidController.repairing(),"old save defaults");require(r.health==74&&r.fatigue==31&&r.hunger==18&&r.thirst==24&&r.morale==66&&v.game.mats==12,"old resident/resources unchanged");require(Context.preferenceName.equals("save_v02"),"namespace unchanged");
  v=fresh();discover(v,RaidState.Enemy.MARAUDERS,35,3);v.game.raidController.prepare();v.game.raidController.toggleDefender(v.game.people.get(2).id);minutes(v,20);String state=snapshot(v);v=kill();require(state.equals(snapshot(v))&&raid(v).phase==RaidState.Phase.PREPARING,"preparation state restore");
  Context.preferences.values.put("raid7_0_attackPower",0);v=kill();require(raid(v)==null&&!v.game.people.get(2).job.equals("Оборона"),"invalid record safely releases orphan defender without effects");
 }
 static void ui(){
  for(int[] size:new int[][]{{360,640},{420,840},{540,960}}){
   GameView v=fresh();View.width=size[0];View.height=size[1];draw(v);discover(v,RaidState.Enemy.MARAUDERS,40,16);v.defensePanel.pending();Canvas c=draw(v);text(c,"ПРИБЛИЖАЕТСЯ НАПАДЕНИЕ");text(c,"ПОДГОТОВИТЬ ОБОРОНУ");RoomUpgradeLayout l=new RoomUpgradeLayout(v.H/v.scale);
   float[] rect=ShelterGeometry.fullSceneRoomRect(4,116,Math.max(610,v.H/v.scale-72));tap(v,40,l.top+12);require(v.game.selectedRoom==-1,"overlay consumes underlying room taps");
   tap(v,180,l.actionTop+20);require(v.defensePanel.mode==DefensePanelController.Mode.DEFENDERS,"preparation action opens resident choices");text(draw(v),"ПОДГОТОВКА ОБОРОНЫ");tap(v,80,l.rowTop+22);require(raid(v).defenders.containsKey(v.game.people.get(0).id),"scaled resident choice");
   for(int i=0;i<10;i++)v.game.people.add(v.game.make("Защитник"+i,"Житель",2));draw(v);for(int i=0;i<20;i++)tap(v,340,l.pageY);require(v.defensePanel.page==v.defensePanel.pages(l)-1,"all defenders accessible by paging");tap(v,80,l.rowTop+20);require(raid(v).defenders.containsKey(v.game.people.get(v.defensePanel.page*l.capacity).id),"last page selectable");tap(v,180,l.actionTop+20);require(v.defensePanel.mode==DefensePanelController.Mode.STATUS,"back action fixed");
   draw(v);v.onTouchEvent(new MotionEvent(100*v.scale,(l.top+110)*v.scale,MotionEvent.ACTION_DOWN));v.onTouchEvent(new MotionEvent(100*v.scale,(l.top+80)*v.scale,MotionEvent.ACTION_MOVE));v.onTouchEvent(new MotionEvent(100*v.scale,(l.top+80)*v.scale,MotionEvent.ACTION_UP));require(v.defensePanel.scroll>=0,"panel scroll bounded");
   tap(v,290,l.secondaryTop+20);require(!v.defensePanel.open,"close doesn't stop attack");text(draw(v),"УГРОЗА УБЕЖИЩУ");tap(v,100,v.H/v.scale-120);require(v.defensePanel.open,"reopen from notice");
   v.defensePanel.open=false;v.roomUpgradePanel.open(4);c=draw(v);text(c,"ОБОРОНА / РЕМОНТ");tap(v,80,l.secondaryTop+20);require(v.defensePanel.open,"barricade card defense access");tap(v,80,l.secondaryTop+20);require(v.defensePanel.mode==DefensePanelController.Mode.REPAIR,"repair navigation");v.game.raidController.durability=40;draw(v);tap(v,180,l.actionTop+20);require(v.defensePanel.mode==DefensePanelController.Mode.BUILDERS,"repair builder picker");
   String before=snapshot(v);for(int i=0;i<10;i++)draw(v);require(before.equals(snapshot(v)),"render doesn't change resources/raid");invariant(v);
  }
 }
 static void simulation(String strategy,int defenders,int level,int durability,boolean deficit){
  GameView v=fresh();v.game.day=1;v.game.gameMinute=0;v.game.raidController.checkedDay=0;v.game.raidController.durability=durability;v.game.roomLevels[4]=level;
  v.game.food=28;v.game.water=34;v.game.mats=deficit?0:18;
  String[] jobs={"Вода","Лечение","Охрана","Еда",deficit?"Отдых":"Материалы"};for(int i=0;i<5;i++){Resident resident=v.game.people.get(i);resident.job=jobs[i];resident.fatigue=resident.hunger=resident.thirst=10;resident.morale=75;}
  Random schedule=new Random(7100);Random simulationRandom=new Quiet(){public int nextInt(int n){boolean dailyRaid=Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(frame->frame.getClassName().endsWith("RaidController")&&frame.getMethodName().equals("checkDailyThreat"));return dailyRaid?schedule.nextInt(n):n-1;}public long nextLong(){return schedule.nextLong();}};v.game.rnd=simulationRandom;int wins=0,partial=0,losses=0,repairs=0,foodLost=0,waterLost=0,materialsLost=0,damage=0;Set<String> counted=new HashSet<>();
  for(int minute=0;minute<20*1440;minute++){
   RaidState r=v.game.raidController.active();
   if(r!=null&&r.phase!=RaidState.Phase.ATTACK){int[] choice={2,0,4};for(int index:Arrays.copyOf(choice,defenders))if(!r.defenders.containsKey(v.game.people.get(index).id))v.game.raidController.toggleDefender(v.game.people.get(index).id);}
   if(v.game.raidController.durability<=75&&v.game.raidController.repairReason().isEmpty()){
    for(Resident resident:v.game.people)if(v.game.roomUpgradeController.unavailableReason(resident).isEmpty()){if(v.game.raidController.startRepair(resident.id).isEmpty())repairs++;break;}
   }
   v.game.advanceMinute();
   // Explicit player choices for unchanged shelter incidents; Stage 7 itself never blocks the clock.
   if(v.game.event)v.game.choose(1);
   RaidState latest=raid(v);
   if(latest!=null&&latest.effectsApplied&&counted.add(latest.id)){if(latest.outcome==RaidState.Outcome.DEFENDED)wins++;else if(latest.outcome==RaidState.Outcome.PARTIAL_BREACH)partial++;else losses++;foodLost+=latest.foodLost;waterLost+=latest.waterLost;materialsLost+=latest.materialsLost;damage+=latest.damageApplied;v.game.raidController.acknowledge(latest);}
   if(minute%1440==1439){String state=snapshot(v);v=kill();v.game.rnd=simulationRandom;require(state.equals(snapshot(v)),"20-day daily restart "+strategy);invariant(v);}
  }
  Set<Long> days=new HashSet<>();long previous=-RaidConfig.MIN_INTERVAL;for(RaidState r:v.game.raidController.raids){require(r.warningMinute>=1440,"simulation first day safe");if(r.attackMinute<0)continue;require(days.add(r.attackMinute/1440),"one attack per day");require(r.attackMinute-previous>=1440,"simulation24h interval");previous=r.attackMinute;}
  int health=0,morale=0;for(Resident resident:v.game.people){health+=resident.health;morale+=resident.morale;}
  System.out.println("SIM "+strategy+": attacks="+(wins+partial+losses)+", defended/partial/defeat="+wins+"/"+partial+"/"+losses+", damage="+damage+", repairs="+repairs+", durability="+v.game.raidController.durability+", theft food/water/materials="+foodLost+"/"+waterLost+"/"+materialsLost+", stock food/water/materials="+v.game.food+"/"+v.game.water+"/"+v.game.mats+", avg health/morale="+health/5.0+"/"+morale/5.0);
 }
 public static void main(String[] args){frequency();defenders();combat();clocks();repair();expeditionIntegration();edgeCases();saves();ui();simulation("no-defenders",0,1,100,false);simulation("one-guard",1,1,100,false);simulation("three-defenders",3,1,100,false);simulation("upgraded",1,3,100,false);simulation("damaged",1,1,25,false);simulation("material-deficit",1,1,100,true);System.out.println("PASS: "+checks+" raid assertions; frequency, all outcomes/enemies, defender exclusivity, repairs/payment, clock/pause/speeds, process restarts in all phases, idempotent effects, old saves, expedition integration, Canvas/touch/scroll/paging and six20-day strategies.");}
}
'''

def main():
    sources = {"com/lastdom/game/" + p.name: p.read_text()
               for p in (fixtures.ROOT / fixtures.JAVA_PATH).glob("*.java")}
    sources["com/lastdom/game/R.java"] = "package com.lastdom.game; public class R {public static class drawable {public static final int shelter_clean=1,shelter_full_scene=2;}}"
    fixtures.PROBE = PROBE
    with tempfile.TemporaryDirectory(prefix="raid-regression-") as directory:
        print(fixtures.run_version(Path(directory), "raids", sources).strip())

if __name__ == "__main__":
    main()
