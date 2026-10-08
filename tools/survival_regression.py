#!/usr/bin/env python3
"""Stage 6 actual Java controller/save/Canvas tests and reproducible 10-day simulations.

Uses the same Android doubles as existing regressions; not an Android device test.
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
 static void close(double a,double b,String s){require(Math.abs(a-b)<.00001,s+": "+a+" != "+b);}
 static class Safe extends Random {public double nextDouble(){return .999;}public int nextInt(int n){return n-1;}}
 static GameView load(){GameView v=new GameView(new Context());v.game.rnd=new Safe();draw(v);return v;}
 static GameView fresh(){View.width=420;View.height=840;Context.preferences=new MemoryPreferences();return load();}
 static GameView kill(){TreeMap<String,Object> disk=new TreeMap<>(Context.preferences.values);Context.preferences=new MemoryPreferences();Context.preferences.values.putAll(disk);return load();}
 static Canvas draw(GameView v){Canvas c=new Canvas();v.onDraw(c);return c;}
 static void text(Canvas c,String s){require(c.commands.stream().anyMatch(x->x.contains(s)),"drawn: "+s);}
 static void tap(GameView v,float x,float y){v.onTouchEvent(new MotionEvent(x*v.scale,y*v.scale,MotionEvent.ACTION_UP));}
 static void minutes(GameView v,int n){for(int i=0;i<n;i++)v.game.advanceMinute();}
 static Resident only(GameView v){for(int i=1;i<v.game.people.size();i++)v.game.people.get(i).alive=false;return v.game.people.get(0);}
 static String state(GameView v){v.game.save();return Context.preferences.values.toString();}
 static void invariant(GameView v){Set<String> ids=new HashSet<>();for(Resident r:v.game.people){require(ids.add(r.id),"unique resident");for(int n:new int[]{r.health,r.hunger,r.thirst,r.fatigue,r.morale})require(n>=0&&n<=100,"bounded condition");require(r.foodMinutes>=0&&r.foodMinutes<=ProductionConfig.MAX_FOOD_MINUTES&&r.waterMinutes>=0&&r.waterMinutes<=1440,"bounded rations");}require(v.game.food>=0&&v.game.water>=0&&v.game.mats>=0,"nonnegative resources");}
 static void needs(){
  GameView v=fresh();Resident r=only(v);r.job="Материалы";v.game.food=v.game.water=0;int h=r.health,m=r.morale;
  minutes(v,720);require(r.hunger==22&&r.thirst==27&&r.fatigue==25,"half-day smooth rates 25/35/30, not daily jumps");
  minutes(v,720);require(r.hunger==35&&r.thirst==45&&r.fatigue==40&&r.health==h,"day rates and no early starvation damage");
  require(r.morale>=m,"normal conditions initially stable/recovering");
  minutes(v,4*1440);require(r.health<h&&r.morale<m,"prolonged critical deprivation harms health and morale");
  minutes(v,15*1440);require(r.alive&&r.health==0&&!v.game.gameOver,"no starvation death in Stage 6");invariant(v);
  v=fresh();r=only(v);r.job="Материалы";r.hunger=r.thirst=60;v.game.food=v.game.water=10;
  minutes(v,1);require(v.game.food==9&&v.game.water==9&&r.foodMinutes==1439,"one prepaid ration per home day");
  String first=state(v);v=kill();r=v.game.people.get(0);require(first.equals(state(v)),"reload all fractions and ration balance exact");minutes(v,1439);
  require(v.game.food==9&&v.game.water==9&&r.foodMinutes==0&&r.waterMinutes==0,"exact day, no global daily consumption");
  require(r.hunger==35&&r.thirst==25,"smooth food/water relief");
  v=kill();minutes(v,1);require(v.game.food==8&&v.game.water==8,"next ration once after restart");
  v=fresh();r=only(v);r.job="Материалы";v.game.food=v.game.water=0;minutes(v,500);int hungry=r.hunger;v.game.food=v.game.water=1;minutes(v,1);require(r.hunger==hungry&&r.foodMinutes==1439,"supplies do not instantaneously erase needs");minutes(v,200);require(r.hunger<hungry,"recovery after supply resumes");
 }
 static void clocks(){
  for(int speed:new int[]{1,2,4}){
   GameView v=fresh();Resident r=only(v);v.game.food=v.game.water=0;r.job="Материалы";v.game.speed=speed;
   for(int i=0;i<360/speed;i++)v.tick.run();require(r.hunger==16&&r.thirst==18&&r.fatigue==17,"same simulation quarter-day at speed "+speed);
   v.game.paused=true;String before=state(v);for(int i=0;i<10;i++)v.tick.run();require(before.equals(state(v)),"pause freezes resources/needs/clock "+speed);
  }
 }
 static void efficiency(){
  Resident r=new Resident("Проверка","Инженер",4);r.fatigue=r.hunger=r.thirst=0;r.morale=100;
  close(SurvivalConfig.efficiency(r),1,"healthy baseline");r.morale=50;close(SurvivalConfig.efficiency(r),.9,"moderate morale");r.morale=30;close(SurvivalConfig.efficiency(r),.8,"low morale");r.morale=10;close(SurvivalConfig.efficiency(r),.65,"critical morale");
  r.morale=75;r.health=50;r.hunger=50;r.thirst=80;r.fatigue=95;double expected=.5*.9*.65*.4;
  close(SurvivalConfig.efficiency(r),expected,"all condition factors exactly once");for(int i=0;i<100;i++)close(SurvivalConfig.efficiency(r),expected,"idempotent efficiency query");
  GameView v=fresh();r=only(v);r.job="Еда";r.hunger=r.thirst=r.fatigue=0;r.health=100;r.morale=75;v.game.roomLevels[1]=2;int food=v.game.food;
  for(int i=0;i<1440;i++)v.game.survivalController.produce(1);require(v.game.food==food&&v.game.productionController.kitchenSavingBasis()==1500,"cooking conserves inputs with 15 percent improved efficiency");v.game.save();v=kill();r=v.game.people.get(0);
  for(int i=0;i<1440;i++)v.game.survivalController.produce(1);require(v.game.food==food&&v.game.productionController.foodRationMinutes()==1694,"cooking saving survives restart without generating food");
  r.health=50;food=v.game.food;v.game.productionRemainders.values.put("survival6_food",0);for(int i=0;i<1440;i++)v.game.survivalController.produce(1);require(v.game.food==food&&v.game.productionController.kitchenSavingBasis()==756,"health penalty once on rounded cooking quality");
  v.game.food=Integer.MAX_VALUE;v.game.water=Integer.MAX_VALUE;r.health=100;v.game.processJobs();require(v.game.food==Integer.MAX_VALUE,"safe production overflow");
  v=fresh();r=only(v);r.job="Материалы";r.fatigue=0;v.game.food=v.game.water=100;r.morale=20;minutes(v,1440);require(r.morale>20,"good conditions recover morale gradually");
  int before=r.morale;v.game.survivalController.injury(r,12);require(r.morale==before-6,"injury lowers morale once at controller effect");
 }
 static void recovery(){
  for(int level=1;level<=3;level++){
   GameView v=fresh();Resident r=only(v);r.fatigue=100;r.job="Отдых";v.game.roomLevels[5]=level;v.game.food=v.game.water=100;
   minutes(v,60);require(100-r.fatigue==new int[]{5,7,10}[level-1],"bedroom hourly recovery/room bonus "+level);v.game.save();int fatigue=r.fatigue;v=kill();r=v.game.people.get(0);require(r.fatigue==fatigue&&r.job.equals("Отдых"),"rest state reload");
   v=fresh();for(int i=2;i<5;i++)v.game.people.get(i).alive=false;r=v.game.people.get(0);Resident doctor=v.game.people.get(1);r.health=40;r.job="Вода";doctor.job="Лечение";doctor.fatigue=0;v.game.expeditionWarehouse.set(ExpeditionLoot.Resource.MEDICINE,2);v.game.food=v.game.water=100;v.game.roomLevels[2]=level;
   minutes(v,1440);require(r.health-40==2*RoomUpgradeConfig.percent(2,level)/100,"existing base2 healing, room bonus "+level);
   doctor.job="Отдых";int health=r.health;minutes(v,1440);require(r.health==health,"no staff no medical healing for worker");
  }
  GameView v=fresh();Resident r=only(v);r.job="Материалы";r.fatigue=80;v.game.food=v.game.water=100;minutes(v,1);require(r.job.equals("Отдых")&&r.autoRecovery&&r.resumeJob.equals("Материалы"),"AI sends exhausted worker to rest");v.game.save();v=kill();r=v.game.people.get(0);require(r.autoRecovery&&r.resumeJob.equals("Материалы"),"AI resume assignment saved");
  minutes(v,4*1440);require(r.fatigue<80&&!r.job.equals("Экспедиция"),"AI rests and safely resumes work");
  v=fresh();for(int i=2;i<5;i++)v.game.people.get(i).alive=false;r=v.game.people.get(0);v.game.people.get(1).job="Лечение";v.game.expeditionWarehouse.set(ExpeditionLoot.Resource.MEDICINE,2);r.job="Материалы";r.health=30;minutes(v,1);require(r.job.equals("Лечится")&&v.game.homeRoomFor(r)==2&&!v.game.survivalController.working(r),"patient AI medpoint, not production staff");v.game.save();v=kill();r=v.game.people.get(0);require(r.job.equals("Лечится")&&r.autoRecovery,"treatment reload");
  v.game.people.get(1).job="Отдых";minutes(v,1);require(r.job.equals("Отдых")&&r.autoRecovery,"missing doctor falls back to bedroom, no recovery deadlock");int health=r.health;minutes(v,1440);require(r.health>health,"injured resident still recovers without medical staff");
 }
 static void builderAndExpedition(){
  GameView v=fresh();v.game.mats=100;Resident builder=v.game.people.get(0),worker=v.game.people.get(4);worker.job="Ремонт";builder.fatigue=0;
  require(v.game.roomUpgradeController.start(0,builder.id).isEmpty(),"construction start");require(v.game.occupants(0).equals("Павел"),"builder absent from normal worker list");
  v.roomUpgradePanel.open(0);Canvas c=draw(v);text(c,"Работают: Павел");text(c,"Строитель: Иван");
  require(!v.game.survivalController.working(builder)&&v.game.homeRoomFor(builder)==0,"builder visible at build room but no output");
  require(v.game.findBestResident("Инженер","")!=0&&!v.game.expeditionController.start("shop",Arrays.asList(builder.id)).isEmpty(),"AI/expedition excludes builder");
  v.game.shelter=50;worker.job="Отдых";v.game.processJobs();require(v.game.shelter==50,"builder really produces no repairs");minutes(v,40);String before=state(v);v=kill();require(before.equals(state(v))&&v.game.people.get(0).status==Resident.Status.BUILDING,"construction+needs snapshot reload");
  minutes(v,80);require(v.game.people.get(0).status==Resident.Status.HOME&&v.game.roomLevels[0]==2,"builder freed normally");
  v=fresh();Resident r=v.game.people.get(0);int[] bad={90,90,20};for(int i=0;i<3;i++){r.hunger=10;r.thirst=10;r.health=100;if(i==0)r.hunger=bad[i];if(i==1)r.thirst=bad[i];if(i==2)r.health=bad[i];String state=state(v);String reason=v.game.expeditionController.start("shop",Arrays.asList(r.id));require(!reason.isEmpty()&&state.equals(state(v)),"critical expedition rejection atomic "+i);require(reason.contains(i==0?"голод":i==1?"жажда":"здоровья"),"specific unavailable cause");}
  r.hunger=r.thirst=10;r.health=100;r.fatigue=10;require(v.game.expeditionController.start("shop",Arrays.asList(r.id)).isEmpty(),"healthy expedition");for(int i=1;i<5;i++)v.game.people.get(i).alive=false;int food=v.game.food,water=v.game.water;
  minutes(v,1440);require(v.game.food==food&&v.game.water==water&&r.foodMinutes==0,"away resident consumes no shelter supplies");require(r.hunger==35&&r.thirst==45&&r.fatigue==40,"away/waiting needs clock");require(v.game.homeRoomFor(r)==-1,"no away clone in shelter");
  String saved=state(v);v=kill();r=v.game.people.get(0);require(saved.equals(state(v))&&r.status==Resident.Status.ON_EXPEDITION,"expedition needs reload");Expedition e=v.game.expeditionController.active();int elapsed=e.elapsedMinutes();v.game.expeditionController.returnHome(e.id);int previousFatigue=r.fatigue;minutes(v,e.durationMinutes);require(e.rewardCredited&&r.status==Resident.Status.HOME&&r.fatigue>=previousFatigue&&r.fatigue<previousFatigue+3,"return preserves minute fatigue without second lump");int fatigue=r.fatigue,award=v.game.food;v.game.expeditionController.complete(e);require(r.fatigue==fatigue&&v.game.food==award,"return effects idempotent");invariant(v);
 }
 static void savesAndWarnings(){
  Context.preferences=new MemoryPreferences();Context.preferences.edit().putInt("day",2).putInt("count",1).putString("p0_name","Старый").putInt("p0_health",44).putInt("p0_fatigue",63).putInt("p0_hunger",57).putInt("p0_morale",22).apply();GameView v=load();Resident r=v.game.people.get(0);
  require(r.health==44&&r.fatigue==63&&r.hunger==57&&r.morale==22&&r.thirst==10,"legacy existing health/fatigue/needs unchanged, safe thirst default");require(!r.autoRecovery&&r.foodMinutes==0,"safe absent new fields");
  Context.preferences=new MemoryPreferences();Context.preferences.edit().putInt("day",1).putInt("count",1).apply();v=load();r=v.game.people.get(0);require(r.hunger==10&&r.thirst==10&&r.morale==75,"legacy missing needs defaults");
  Context.preferences.values.put("p0_health",999);Context.preferences.values.put("p0_hunger",-8);v=kill();require(v.game.people.get(0).health==100&&v.game.people.get(0).hunger==0,"corrupt metrics safely clamped");
  v=fresh();r=only(v);v.game.food=v.game.water=0;r.hunger=r.thirst=90;r.fatigue=85;r.health=30;r.morale=10;r.job="Экспедиция";minutes(v,1);text(draw(v),"Заканчивается вода");require(v.game.log.stream().anyMatch(s->s.contains("Низкая мораль")),"all simultaneous threshold warnings journaled");int logs=v.game.log.size();List<String> entries=new ArrayList<>(v.game.log);minutes(v,100);require(v.game.log.size()==logs,"no warnings every tick");v=kill();minutes(v,1);require(v.game.log.equals(entries),"no warning replay on restart");v.game.load();require(v.game.log.equals(entries),"load clears log rather than duplicate entries");
  v=fresh();r=only(v);r.health=0;minutes(v,1);require(r.alive,"zero-health survivor never dies");
 }
 static void migrationAndEffects(){
  Context.preferences=new MemoryPreferences();Context.preferences.edit().putInt("day",1).putInt("count",1).putString("p0_id","legacy-one").putString("p0_job","Еда").putInt("p0_fatigue",0).putInt("upgrade5_schema",1).putInt("upgrade5_fraction_food",50).putInt("upgrade5_fraction_heal_legacy-one",50).putInt("upgrade5_fraction_rest_legacy-one",50).apply();GameView v=load();Resident r=v.game.people.get(0);
  require(v.game.productionRemainders.values.get("survival6_food")==7200000,"Stage 5 fractional output migrated without loss");require(r.survivalFractions.get("health")==72000&&r.survivalFractions.get("fatigue")==-72000,"Stage 5 healing/rest fractions migrated");String saved=state(v);v=kill();require(saved.equals(state(v)),"old fractional units migrate only once");
  v=fresh();r=v.game.people.get(0);v.game.expeditionController.start("shop",Arrays.asList(r.id));Expedition e=v.game.expeditionController.active();e.beginPhase(Expedition.State.EXPLORING,30,v.game.expeditionController.now());v.game.expeditionController.rollLoot(e);e.cityEventChecked=true;e.explorationEvent=ExpeditionEvent.city(ExpeditionEvent.Type.INFECTED);e.restorePhase(Expedition.State.AWAITING_DECISION,30,15,v.game.expeditionController.now());String id=e.explorationEvent.instanceId;
  int food=v.game.food;int morale=r.morale;v.game.expeditionController.chooseEvent(id,1);require(r.health<100&&r.morale<morale,"city injury actually lowers morale");int health=r.health,after=r.morale;v.game.save();v=kill();r=v.game.people.get(0);require(r.health==health&&r.morale==after,"city injury morale restored");v.game.expeditionController.chooseEvent(id,1);require(r.health==health&&r.morale==after,"city injury morale never applied twice");
  e=v.game.expeditionController.active();int elapsed=e.elapsedMinutes();minutes(v,120);require(e.elapsedMinutes()==elapsed&&r.hunger>10,"waiting decision freezes expedition, not survivor needs");require(v.game.food==food-4,"only four home residents purchase rations during decision");
  v=fresh();r=only(v);v.game.food=v.game.water=0;r.fatigue=80;r.job="Экспедиция";minutes(v,1);long first=v.game.log.stream().filter(t->t.contains("Житель сильно устал")).count();r.hunger=90;minutes(v,1);require(first==1&&v.game.log.stream().anyMatch(t->t.contains("Критический голод")),"a fatigue warning cannot suppress a new critical hunger warning");
  v=fresh();v.game.roomLevels[1]=3;r=v.game.people.get(3);r.job="Еда";require(v.game.survivalController.workPercent(r)==168,"resident UI includes cooking profession and room bonus once");r.job="Отдых";require(v.game.survivalController.workPercent(r)==0,"rest is not reported as production");
 }
 static void ui(){
  for(int width:new int[]{420,840})for(int height:new int[]{640,840,1200}){
   GameView v=fresh();View.width=width;View.height=height*width/420;draw(v);v.game.overlay=3;ResidentNeedsLayout l=new ResidentNeedsLayout(height);Canvas c=draw(v);text(c,"ЖИТЕЛИ");text(c,"Голод");text(c,"Жажда");text(c,"Мораль");text(c,"Работа");require(l.bodyTop+l.capacity*98<=l.footer&&l.footer+98<l.bottom,"cards and actions in portrait panel");
   int selected=v.game.selectedRoom;tap(v,80,l.bodyTop+5);require(v.game.overlay==1&&v.game.selected==0&&v.game.selectedRoom==selected,"resident tap blocks underlying room");c=draw(v);text(c,"Здоровье");
   v.game.people.get(0).hunger=v.game.people.get(0).thirst=v.game.people.get(0).fatigue=95;v.game.people.get(0).health=15;v.game.people.get(0).morale=10;v.game.people.get(0).role="Инженер — восстанавливается после травмы, нуждается в отдыхе и помощи врача в медпункте убежища";c=draw(v);require(v.residentNeedsPanel.lineCount>l.lines||height>=1200,"long detail scrollable when needed");float y=l.bodyTop+100;
   v.onTouchEvent(new MotionEvent(180*v.scale,y*v.scale,MotionEvent.ACTION_DOWN));v.onTouchEvent(new MotionEvent(180*v.scale,(y-200)*v.scale,MotionEvent.ACTION_MOVE));v.onTouchEvent(new MotionEvent(180*v.scale,(l.footer+20)*v.scale,MotionEvent.ACTION_UP));require(!v.game.jobMenu,"scroll release does not hit assignment action");
   tap(v,180,l.footer+22);require(v.game.jobMenu,"fixed assignment action accessible");v.game.jobMenu=false;tap(v,110,l.footer+70);require(v.game.people.get(0).job.equals("Отдых"),"rest action");tap(v,290,l.footer+70);require(v.game.overlay==0,"fixed close action");
   for(int i=0;i<8;i++)v.game.people.add(v.game.make("Новый "+i,"Житель",2));v.game.overlay=3;draw(v);for(int i=0;i<20;i++)tap(v,290,l.footer+20);draw(v);int page=v.residentNeedsPanel.page;require(page==v.residentNeedsPanel.pages(l)-1,"all residents accessible by paging");tap(v,80,l.bodyTop+5);require(v.game.selected==page*l.capacity,"last page detail opens real resident");
   String before=state(v);for(int i=0;i<50;i++)draw(v);require(before.equals(state(v)),"rendering changes no survival/resources");invariant(v);
  }
 }
 static void simulate(String name,boolean expeditions,boolean noFood,boolean noWater,boolean upgraded,boolean balanced){
  GameView v=fresh();v.game.gameMinute=0;v.game.food=noFood?0:28;v.game.water=noWater?0:34;
  String[] jobs=balanced?new String[]{"Еда","Лечение","Вода","Еда","Вода"}:new String[]{"Вода","Лечение","Охрана","Еда","Материалы"};
  for(int i=0;i<5;i++)v.game.people.get(i).job=jobs[i];if(noFood)for(Resident r:v.game.people)if(r.job.equals("Еда"))r.job="Материалы";if(noWater)for(Resident r:v.game.people)if(r.job.equals("Вода"))r.job="Материалы";
  if(upgraded)Arrays.fill(v.game.roomLevels,3);
  if(expeditions)v.game.rnd=new Random(6099){public int nextInt(int n){return n==100?99:super.nextInt(n);}};
  int trips=0,events=0;
  for(int minute=0;minute<10*1440;minute++){
   if(expeditions&&minute%1440==0&&v.game.expeditionController.active()==null){Resident r=v.game.people.get(4);if(v.game.expeditionController.start(trips%2==0?"shop":"garage",Arrays.asList(r.id)).isEmpty())trips++;}
   Expedition e=v.game.expeditionController.active();if(e!=null&&e.state()==Expedition.State.AWAITING_RETURN)v.game.expeditionController.returnHome(e.id);
   if(e!=null&&e.state()==Expedition.State.AWAITING_DECISION){if(!e.explorationEvent.effectsApplied){v.game.expeditionController.chooseEvent(e.explorationEvent.instanceId,0);events++;}v.game.expeditionController.continueEvent(e.explorationEvent.instanceId);}
   v.game.advanceMinute();
   if(expeditions&&e!=null&&e.state()==Expedition.State.COMPLETED){v.game.expeditionController.acknowledge(e.id);v.game.assignJob(4,jobs[4]);}
   if(minute%1440==1439){Random simulationRandom=v.game.rnd;String before=state(v);v=kill();v.game.rnd=simulationRandom;require(before.equals(state(v)),"10-day simulation daily reload "+name);invariant(v);}
  }
  int hunger=0,thirst=0,fatigue=0,morale=0,health=0;for(Resident r:v.game.people){hunger+=r.hunger;thirst+=r.thirst;fatigue+=r.fatigue;morale+=r.morale;health+=r.health;require(r.alive,"all alive after 10 days "+name);}
  System.out.println("SIM "+name+": food="+v.game.food+", water="+v.game.water+", materials="+v.game.mats+", health="+health/5.0+", hunger="+hunger/5.0+", thirst="+thirst/5.0+", fatigue="+fatigue/5.0+", morale="+morale/5.0+", trips="+trips+", events="+events);
 }
 public static void main(String[] args){needs();clocks();efficiency();recovery();builderAndExpedition();savesAndWarnings();migrationAndEffects();ui();simulate("normal",false,false,false,false,false);simulate("regular-expeditions",true,false,false,false,false);simulate("food-shortage",false,true,false,false,false);simulate("water-shortage",false,false,true,false,false);simulate("upgraded",false,false,false,true,false);simulate("survival-assignments",false,false,false,false,true);System.out.println("PASS: "+checks+" survivor needs assertions, six 10-day scenarios and daily process restarts.");}
}
'''


def main():
    sources = {"com/lastdom/game/" + p.name: p.read_text()
               for p in (fixtures.ROOT / fixtures.JAVA_PATH).glob("*.java")}
    sources["com/lastdom/game/R.java"] = "package com.lastdom.game; public class R {public static class drawable {public static final int shelter_clean=1,shelter_full_scene=2;}}"
    fixtures.PROBE = PROBE
    with tempfile.TemporaryDirectory(prefix="survival-regression-") as directory:
        print(fixtures.run_version(Path(directory), "survival", sources).strip())


if __name__ == "__main__":
    main()
