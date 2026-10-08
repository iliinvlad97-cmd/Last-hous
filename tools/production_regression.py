#!/usr/bin/env python3
"""Stage 8 actual Java production, resources, save/touch regression and seeded 10-day economies."""
import sys
sys.dont_write_bytecode = True
from pathlib import Path
import tempfile
import refactor_regression as fixtures
import raid_regression as raids

PROBE = raids.PROBE[:raids.PROBE.index(' static void frequency()')] + r'''
 static void stats(GameView v){
  require(v.game.food>=0&&v.game.water>=0&&v.game.power>=0&&v.game.mats>=0,"nonnegative resources");
  require(v.game.production.materialSavingRemainder>=0&&v.game.production.materialSavingRemainder<10000,"material carry bounded");
  require(v.game.production.medicineMinutes>=0&&v.game.production.medicineMinutes<=1440,"prepaid medicine bounded");
  Set<String> ids=new HashSet<>();for(Resident r:v.game.people){require(ids.add(r.id),"no resident duplication");require(r.foodMinutes>=0&&r.foodMinutes<=ProductionConfig.MAX_FOOD_MINUTES,"food cycle bounds");}
 }
 static void zeroNeeds(GameView v){for(Resident r:v.game.people){r.hunger=r.thirst=r.fatigue=0;r.health=r.morale=100;r.foodMinutes=r.waterMinutes=1440;}}
 static void idle(GameView v){zeroNeeds(v);for(Resident r:v.game.people)r.job="Отдых";}
 static void generatorsAndWater(){
  for(int level=1;level<=3;level++){
   GameView v=fresh();idle(v);v.game.power=10;v.game.roomLevels[0]=level;
   v.game.productionController.produce(1440);close(v.game.production.energyProduced,2*RoomUpgradeConfig.percent(0,level)/100,"whole base output L"+level);
   v.game.productionController.produce(1440);require(v.game.power==10+4*RoomUpgradeConfig.percent(0,level)/100,"base generation once, persisted fraction L"+level);
   v=fresh();idle(v);v.game.power=0;v.game.roomLevels[0]=level;v.game.people.get(0).job="Ремонт";
   double rate=v.game.productionController.energyPerDay();require(rate>2*RoomUpgradeConfig.percent(0,level)/100.0&&rate<=3.6,"bounded worker contribution");
   v.game.productionController.produce(1440);require(v.game.power==(int)rate,"rate displayed equals production floor");
  }
  GameView v=fresh();idle(v);v.game.power=60;v.game.productionRemainders.values.put("survival6_energy",14399999);v.game.productionController.produce(20*1440);require(v.game.power==60&&v.game.production.energyProduced==0,"no unbounded generator/storage carry");v.game.power=59;v.game.productionController.produce(1);require(v.game.power==59,"full storage can't bank free energy");
  v.game.power=5;v.game.roomCondition[0]=0;v.game.productionController.produce(1440);require(v.game.power==5,"broken generator stops");v.game.roomCondition[0]=100;v.game.productionController.produce(1440);require(v.game.power==7,"repaired generator resumes");
  v=fresh();idle(v);v.game.water=0;v.game.people.get(2).job="Вода";v.game.productionController.produce(1440);require(v.game.water==4,"one water worker base4");v.game.people.get(0).job="Вода";v.game.productionController.produce(1440);require(v.game.water==12,"two water workers base8");
  v.game.water=60;v.game.productionController.produce(1440);require(v.game.water==60,"water output bounded");v.game.water=90;v.game.productionController.produce(1440);require(v.game.water==90,"legacy/expedition surplus never removed");
  v=fresh();idle(v);v.game.water=0;v.game.people.get(2).job="Вода";v.game.people.get(2).health=50;v.game.productionController.produce(1440);require(v.game.water==2,"water condition penalty applied once");v.game.water=0;v.game.roomCondition[1]=0;v.game.productionController.produce(1440);require(v.game.water==0,"broken water equipment stops");v.game.roomCondition[1]=100;v.game.people.get(2).health=100;v.game.productionController.produce(1440);require(v.game.water==4,"water resumes after repair");
  v.game.people.get(0).job=v.game.people.get(2).job="Отдых";v.game.water=0;v.game.productionController.produce(1440);require(v.game.water==0,"no workers no water");stats(v);
 }
 static void cooking(){
  for(int level=1;level<=3;level++){
   GameView v=fresh();idle(v);v.game.people.get(3).job="Еда";v.game.roomLevels[1]=level;
   int expected=120*10*RoomUpgradeConfig.percent(1,level)/100;require(v.game.productionController.kitchenSavingBasis()==expected,"skill/profession/room used once L"+level);
   int stock=v.game.food;v.game.productionController.produce(10*1440);require(v.game.food==stock,"cooking cannot create food");
   Resident r=v.game.people.get(0);r.foodMinutes=0;require(v.game.productionController.reserveFood(r),"one real food ration booked");require(v.game.food==stock-1&&r.foodMinutes==14400000/(10000-expected),"saving extends exactly one prepaid ration");
   int duration=r.foodMinutes;String paid=snapshot(v);v=kill();require(paid.equals(snapshot(v))&&v.game.people.get(0).foodMinutes==duration,"long cooked ration survives loader/restart");
   require(!v.game.productionController.reserveFood(v.game.people.get(0))&&paid.equals(snapshot(v)),"no repeat payment");
  }
  GameView v=fresh();idle(v);Resident r=v.game.people.get(0);r.foodMinutes=0;v.game.food=0;String empty=snapshot(v);require(!v.game.productionController.reserveFood(r)&&empty.equals(snapshot(v)),"no raw food no meal/no mutation");v.game.food=1;require(v.game.productionController.reserveFood(r)&&v.game.food==0&&r.foodMinutes==1440,"without cook base ration unchanged");
  v=fresh();idle(v);for(Resident chef:v.game.people)chef.job="Еда";v.game.roomLevels[1]=3;require(v.game.productionController.kitchenSavingBasis()==2500&&v.game.productionController.foodRationMinutes()==1920,"multiple cooks cap25 percent");
  v=fresh();idle(v);Resident anna=v.game.people.get(3);anna.job="Еда";int normal=v.game.productionController.kitchenSavingBasis();anna.health=50;require(v.game.productionController.kitchenSavingBasis()==normal/2,"health penalty once");anna.health=100;anna.hunger=80;require(v.game.productionController.kitchenSavingBasis()<normal,"hunger penalty");anna.hunger=0;anna.thirst=95;require(v.game.productionController.kitchenSavingBasis()<normal,"thirst penalty");anna.thirst=0;anna.fatigue=80;require(v.game.productionController.kitchenSavingBasis()<normal,"fatigue penalty");anna.fatigue=0;anna.morale=10;require(v.game.productionController.kitchenSavingBasis()<normal,"morale penalty");stats(v);
 }
 static void workshop(){
  for(int level=1;level<=3;level++){
   GameView v=fresh();idle(v);v.game.people.get(4).job="Материалы";v.game.roomLevels[3]=level;v.game.mats=100;
   require(v.game.productionController.workshopSavingBasis()==750*RoomUpgradeConfig.percent(3,level)/100,"mechanic/skill and additive upgrade bonus L"+level);
   v.game.productionController.produce(10*1440);require(v.game.mats==100,"workshop doesn't create materials");
   int carry=v.game.production.materialSavingRemainder;ProductionController.UpgradeQuote quote=v.game.productionController.upgradeQuote(20);
   require(v.game.production.materialSavingRemainder==carry&&v.game.mats==100,"preview quote read-only");
   require(v.game.roomUpgradeController.start(0,v.game.people.get(0).id).isEmpty(),"discounted construction start");
   require(v.game.mats==100-quote.cost&&v.game.production.materialSavingRemainder==quote.remainder,"single atomic cost/carry update");
   int paid=v.game.mats;String saved=snapshot(v);v=kill();require(saved.equals(snapshot(v))&&v.game.roomUpgradeController.active()!=null,"discounted construction survives strict legacy loader");
   require(!v.game.roomUpgradeController.start(1,v.game.people.get(2).id).isEmpty()&&v.game.mats==paid,"second build no duplicate spending");minutes(v,120);require(v.game.roomLevels[0]==2&&v.game.mats==paid,"completion doesn't recharge");v=kill();minutes(v,1);require(v.game.roomLevels[0]==2&&v.game.mats==paid,"completion once across restart");
  }
  GameView v=fresh();idle(v);v.game.people.get(4).job="Материалы";v.game.mats=18;ProductionController.UpgradeQuote q=v.game.productionController.upgradeQuote(20);String before=snapshot(v);require(!v.game.roomUpgradeController.start(0,v.game.people.get(0).id).isEmpty()&&before.equals(snapshot(v)),"insufficient raw materials atomic");v.game.mats=q.cost;require(v.game.roomUpgradeController.start(0,v.game.people.get(0).id).isEmpty()&&v.game.mats==0,"exact discounted budget accepted");
  v=fresh();idle(v);v.game.people.get(4).job="Материалы";long base=0,saved=0;for(int i=0;i<100;i++){q=v.game.productionController.upgradeQuote(15);base+=15;saved+=q.saved;v.game.productionController.applyUpgradeQuote(q);if(i%7==0){v.game.save();v=kill();}}require(saved==base*750/10000&&v.game.production.materialSavingRemainder==5000,"fractional small discounts accumulate without loss/duplication");
  v.game.people.get(4).job="Отдых";q=v.game.productionController.upgradeQuote(20);require(q.cost==20&&q.saved==0,"no workshop staff no discount");
  v=fresh();idle(v);for(Resident worker:v.game.people)worker.job="Материалы";v.game.roomLevels[3]=3;require(v.game.productionController.workshopSavingBasis()==1500,"discount cap15 percent");v.game.raidController.durability=50;int mats=v.game.mats;v.game.people.get(0).job="Вода";require(v.game.raidController.startRepair(v.game.people.get(0).id).isEmpty()&&v.game.mats==mats-5,"barricade repair cost unchanged by workshop");stats(v);
 }
 static void clinic(){
  GameView v=fresh();idle(v);Resident doctor=v.game.people.get(1),patient=v.game.people.get(0);doctor.job="Лечение";patient.job="Вода";patient.health=40;
  int h=patient.health;minutes(v,1440);require(patient.health==h&&v.game.production.medicineDoses==0,"no medicine no free clinical healing for worker");
  v=fresh();idle(v);doctor=v.game.people.get(1);patient=v.game.people.get(0);doctor.job="Лечение";patient.job="Вода";patient.health=40;v.game.expeditionWarehouse.set(ExpeditionLoot.Resource.MEDICINE,2);
  minutes(v,20);require(v.game.expeditionWarehouse.get(ExpeditionLoot.Resource.MEDICINE)==1&&v.game.production.medicineMinutes==1420,"one prepaid clinic dose");String paid=snapshot(v);v=kill();require(paid.equals(snapshot(v)),"clinic/input/health restart atomic");minutes(v,1420);require(v.game.production.medicineMinutes==0&&v.game.expeditionWarehouse.get(ExpeditionLoot.Resource.MEDICINE)==1&&v.game.people.get(0).health==42,"one day dose preserves old2 health rate");minutes(v,1);require(v.game.production.medicineDoses==2&&v.game.expeditionWarehouse.get(ExpeditionLoot.Resource.MEDICINE)==0,"next dose once");
  for(Resident r:v.game.people)r.health=100;int carry=v.game.production.medicineMinutes;minutes(v,20);require(v.game.production.medicineMinutes==carry,"no patients dose progress paused");
  v=fresh();idle(v);v.game.expeditionWarehouse.set(ExpeditionLoot.Resource.MEDICINE,3);v.game.people.get(0).health=30;v.game.people.get(0).job="Вода";minutes(v,20);require(v.game.expeditionWarehouse.get(ExpeditionLoot.Resource.MEDICINE)==3&&v.game.people.get(0).job.equals("Отдых"),"no doctor no dose, existing passive bedroom recovery");
  for(int level=1;level<=3;level++){v=fresh();idle(v);doctor=v.game.people.get(1);doctor.job="Лечение";patient=v.game.people.get(0);patient.job="Вода";patient.health=40;v.game.roomLevels[2]=level;v.game.expeditionWarehouse.set(ExpeditionLoot.Resource.MEDICINE,2);minutes(v,1440);require(patient.health==40+2*RoomUpgradeConfig.percent(2,level)/100,"clinic upgrade once L"+level);}
  v=fresh();idle(v);doctor=v.game.people.get(1);patient=v.game.people.get(0);doctor.job="Лечение";patient.health=40;patient.job="Вода";v.game.expeditionWarehouse.set(ExpeditionLoot.Resource.MEDICINE,1);minutes(v,1);int dose=v.game.production.medicineMinutes;doctor.job="Отдых";minutes(v,20);require(v.game.production.medicineMinutes==dose,"absent doctor pauses paid dose without spending");v=kill();doctor=v.game.people.get(1);doctor.job="Лечение";v.game.roomCondition[2]=0;minutes(v,20);require(v.game.production.medicineMinutes==dose,"broken clinic pauses paid dose");v.game.roomCondition[2]=100;minutes(v,dose);require(v.game.production.medicineMinutes==0&&v.game.production.medicineDoses==1,"paid dose resumes without recharging");dose=v.game.people.get(0).health;minutes(v,20);require(v.game.people.get(0).health==dose&&v.game.production.medicineDoses==1,"empty stock stops clinical output");v.game.expeditionWarehouse.set(ExpeditionLoot.Resource.MEDICINE,1);minutes(v,1);require(v.game.production.medicineDoses==2&&v.game.production.medicineMinutes==1439,"medicine delivery resumes exactly once");
  stats(v);
 }
 static void exclusivity(){
  for(String job:new String[]{"Еда","Материалы","Вода","Ремонт","Лечение"}){
   GameView v=fresh();idle(v);Resident r=v.game.people.get(0);r.job=job;
   require(v.game.expeditionController.start("shop",Arrays.asList(r.id)).isEmpty(),"worker expedition starts");require(!v.game.productionController.worker(r,job),"away excluded "+job);
   v=fresh();idle(v);r=v.game.people.get(0);require(v.game.roomUpgradeController.start(0,r.id).isEmpty(),"builder starts");r.job=job;require(!v.game.productionController.worker(r,job),"builder excluded even stale job "+job);
   v=fresh();idle(v);discover(v,RaidState.Enemy.MARAUDERS,30,7);r=v.game.people.get(0);v.game.raidController.toggleDefender(r.id);r.job=job;require(!v.game.productionController.worker(r,job),"defense has priority even stale job "+job);
  }
  GameView v=fresh();idle(v);Resident r=v.game.people.get(0);r.job="Лечится";require(!v.game.productionController.worker(r,"Лечится"),"patient not staff");r.job="Отдых";require(!v.game.productionController.worker(r,"Отдых"),"resting not staff");
 }
 static void clocksAndLegacy(){
  GameView v=fresh();idle(v);v.game.people.get(3).job="Еда";v.game.people.get(4).job="Материалы";v.game.people.get(2).job="Вода";v.game.food=v.game.water=40;v.game.power=10;zeroNeeds(v);String seed=snapshot(v);TreeMap<String,Object> disk=new TreeMap<>(Context.preferences.values);String result=null;
  for(int speed:new int[]{1,2,4}){Context.preferences=new MemoryPreferences();Context.preferences.values.putAll(disk);v=load();v.game.speed=speed;for(int i=0;i<120/speed;i++)v.tick.run();v.game.speed=1;String current=snapshot(v);if(result==null)result=current;else require(result.equals(current),"equivalent survival/production/doses at speed "+speed);}
  v.game.paused=true;String paused=snapshot(v);for(int i=0;i<20;i++){v.tick.run();draw(v);}require(paused.equals(snapshot(v)),"pause and rendering no production/input charging");
  v=fresh();idle(v);v.game.people.get(2).job="Вода";v.game.water=0;v.game.power=0;disk=new TreeMap<>(Context.preferences.values);v.game.save();disk=new TreeMap<>(Context.preferences.values);result=null;
  for(int step:new int[]{1,2,4,17,60,1440}){Context.preferences=new MemoryPreferences();Context.preferences.values.putAll(disk);v=load();for(int n=0;n<1440;n+=step)v.game.productionController.produce(Math.min(step,1440-n));String current=snapshot(v);if(result==null)result=current;else require(result.equals(current),"production grouping invariant step "+step);}
  Context.preferences=new MemoryPreferences();Context.preferences.edit().putInt("day",3).putInt("count",1).putInt("food",23).putInt("water",24).putInt("power",102).putInt("mats",25).putInt("p0_health",77).putInt("p0_fatigue",19).putInt("p0_foodMinutes",800).putInt("survival6_output_energy",7200000).apply();v=load();require(v.game.food==23&&v.game.water==24&&v.game.power==102&&v.game.mats==25&&v.game.people.get(0).health==77&&v.game.people.get(0).fatigue==19&&v.game.people.get(0).foodMinutes==800,"old save metrics/resources/paid ration preserved");require(v.game.production.medicineMinutes==0&&v.game.production.materialSavingRemainder==0,"old save new fields safe0");String old=snapshot(v);v=kill();require(old.equals(snapshot(v)),"migration then repeated restart no grant/spend");
 }
 static void cycleBoundaries(){
  GameView v=fresh();idle(v);v.game.food=v.game.water=40;v.game.power=10;v.game.people.get(3).job="Еда";v.game.people.get(4).job="Материалы";v.game.people.get(1).job="Лечение";v.game.people.get(2).job="Вода";v.game.people.get(2).health=40;for(Resident r:v.game.people)r.foodMinutes=r.waterMinutes=120;v.game.expeditionWarehouse.set(ExpeditionLoot.Resource.MEDICINE,3);v.game.save();TreeMap<String,Object> disk=new TreeMap<>(Context.preferences.values);String result=null;
  for(int[] pattern:new int[][]{{1,1,1,1},{2,2,2,2},{4,4,4,4},{1,2,4,2}}){Context.preferences=new MemoryPreferences();Context.preferences.values.putAll(disk);v=load();for(int speed:pattern){v.game.speed=speed;for(int i=0;i<360/speed;i++)v.tick.run();v.game.save();v=kill();}v.game.speed=1;String current=snapshot(v);if(result==null)result=current;else require(result.equals(current),"ration/dose/day boundaries with speed switches and phase restarts "+Arrays.toString(pattern));require(v.game.production.medicineDoses==1&&v.game.production.medicineMinutes==0&&v.game.production.foodRations==5,"one real purchase per cycle across speeds");}
  v=fresh();idle(v);require(v.game.roomUpgradeController.start(0,v.game.people.get(0).id).isEmpty(),"legacy full-price task created");v.game.save();Context.preferences.values.keySet().removeIf(k->k.startsWith("production8_")||k.endsWith("_production8_saved"));int paid=v.game.mats;v=kill();require(v.game.roomUpgradeController.active()!=null&&v.game.mats==paid,"pre-stage8 paid construction loads without spending");
  v=fresh();idle(v);require(v.game.roomUpgradeController.start(0,v.game.people.get(0).id).isEmpty(),"invalid paid-cost fixture");v.game.save();Context.preferences.edit().putInt("upgrade5_0_production8_saved",19).putInt("upgrade5_0_cost",1).apply();paid=v.game.mats;v=kill();require(v.game.roomUpgradeController.active()==null&&v.game.mats==paid&&v.game.roomLevels[0]==1,"invalid excessive saved discount rejected without grant/charge");
  Context.preferences.edit().putInt("production8_medicineMinutes",Integer.MAX_VALUE).putInt("production8_materialRemainder",-100).putString("production8_foodRations","bad").putString("production8_energyProduced","-20").apply();v=kill();require(v.game.production.medicineMinutes==1440&&v.game.production.materialSavingRemainder==0&&v.game.production.foodRations==0&&v.game.production.energyProduced==0,"damaged new progress/counters safely bounded");
 }
 static void ui(){
  for(int[] size:new int[][]{{360,640},{420,640},{420,840},{540,960},{1080,2340}})for(int room=0;room<6;room++){
   GameView v=fresh();View.width=size[0];View.height=size[1];draw(v);v.roomUpgradePanel.open(room);String before=snapshot(v);Canvas c=draw(v);if(room!=4)text(c,"ПРОИЗВОДСТВО / ПОЛЕЗНЫЙ ЭФФЕКТ");text(c,"ЗАКРЫТЬ");text(c,room==4?"ОБОРОНА / РЕМОНТ":"НАЗНАЧИТЬ");RoomUpgradeLayout l=new RoomUpgradeLayout(v.H/v.scale);require(l.secondaryTop+43<l.bottom,"production footer accessible above nav");
   v.roomUpgradePanel.message="ОченьДлинноеНазваниеБезПробелов".repeat(8);v.roomUpgradePanel.scroll=0;c=draw(v);for(String cmd:c.commands)if(cmd.startsWith("drawText[Очень")){String content=cmd.substring(cmd.indexOf('[')+1,cmd.indexOf(", "));require(content.length()<100,"long production word wraps without clipping");}v.roomUpgradePanel.scroll=10000;c=draw(v);int wrapped=0;for(String cmd:c.commands)if(cmd.startsWith("drawText[Очень")){wrapped++;String content=cmd.substring(cmd.indexOf('[')+1,cmd.indexOf(", "));require(content.length()<100,"scrolled long production word fits");}require(wrapped>0,"long message visible after scrolling");require(v.roomUpgradePanel.scroll<10000,"production scroll clamps to content");for(int i=0;i<5;i++)draw(v);require(before.equals(snapshot(v)),"production UI read-only in room "+room);tap(v,290,l.secondaryTop+20);require(v.game.overlay==0,"close works independently of scroll");
  }
 }
 static class EconomyRandom extends Random {
  EconomyRandom(long seed){super(seed);}
  public int nextInt(int n){
   for(StackTraceElement f:Thread.currentThread().getStackTrace())if(f.getClassName().endsWith("GameController")&&f.getMethodName().equals("dailyCycle"))return n-1;
   return super.nextInt(n);
  }
 }
 static void simulation(String name,boolean upgraded,boolean foodDeficit,boolean waterDeficit,boolean trips){
  GameView v=fresh();v.game.reset();v.game.rnd=new EconomyRandom(8088);v.game.raidController.checkedDay=99;
  if(upgraded)Arrays.fill(v.game.roomLevels,3);if(foodDeficit)v.game.food=0;if(waterDeficit)v.game.water=0;
  String[] jobs={"Ремонт","Лечение","Вода","Еда","Материалы"};for(int i=0;i<5;i++)v.game.people.get(i).job=jobs[i];
  int journeys=0,foodReturned=0,waterReturned=0,materialReturned=0,medReturned=0;Set<String> credited=new HashSet<>();
  Random sequence=new EconomyRandom(8088);
  for(int minute=0;minute<14400;minute++){
   v.game.rnd=sequence;v.game.raidController.checkedDay=v.game.day+1;
   // Explicit simulated player reassignment after an expedition's existing mandatory rest.
   for(int i=0;i<5;i++){Resident resident=v.game.people.get(i);if(resident.job.equals("Отдых")&&!resident.autoRecovery&&!v.game.survivalController.protectedRest(resident)&&!v.game.isOnExpedition(resident)&&!v.game.isBuilding(resident))v.game.assignJob(i,jobs[i]);}
   if(trips&&minute%1440==0&&v.game.expeditionController.active()==null){
    String location=(journeys==2?"garage":journeys==4?"pharmacy":"shop");
    java.util.List<String> team=new ArrayList<>();int[] choices=location.equals("pharmacy")?new int[]{1,0}:location.equals("garage")?new int[]{4,0}:new int[]{3,0,2};
    for(int i:choices){Resident r=v.game.people.get(i);if(v.game.expeditionController.unavailableReason(r).isEmpty())team.add(r.id);}
    if(!team.isEmpty()&&v.game.expeditionController.start(location,team).isEmpty())journeys++;
   }
   if(trips&&!upgraded&&minute%1440==600&&v.game.roomUpgradeController.active()==null){int room=journeys<3?1:0;Resident builder=v.game.people.get(0);if(v.game.roomLevels[room]==1&&v.game.roomUpgradeController.unavailableReason(builder).isEmpty())v.game.roomUpgradeController.start(room,builder.id);}
   Expedition e=v.game.expeditionController.active();
   if(e!=null&&e.state()==Expedition.State.AWAITING_DECISION){for(int choice=0;choice<3;choice++)if(v.game.expeditionController.chooseEvent(e.explorationEvent.instanceId,choice).isEmpty()){v.game.expeditionController.continueEvent(e.explorationEvent.instanceId);break;}}
   if(e!=null&&e.state()==Expedition.State.AWAITING_RETURN)v.game.expeditionController.returnHome(e.id);
   v.game.advanceMinute();
   for(Expedition done:v.game.expeditions)if(done.rewardCredited&&credited.add(done.id)){foodReturned+=done.cargo.get(ExpeditionLoot.Resource.FOOD);waterReturned+=done.cargo.get(ExpeditionLoot.Resource.WATER);materialReturned+=done.cargo.get(ExpeditionLoot.Resource.MATERIALS);medReturned+=done.cargo.get(ExpeditionLoot.Resource.MEDICINE);}
   if(minute%1440==1439){stats(v);String save=snapshot(v);v=kill();require(save.equals(snapshot(v)),"10-day production/recovery/expedition restart "+name);}
  }
  double health=0,hunger=0,thirst=0,fatigue=0,morale=0;for(Resident r:v.game.people){health+=r.health;hunger+=r.hunger;thirst+=r.thirst;fatigue+=r.fatigue;morale+=r.morale;}
  System.out.println("SIM "+name+": food/water/power/materials="+v.game.food+"/"+v.game.water+"/"+v.game.power+"/"+v.game.mats+", mean health/hunger/thirst/fatigue/morale="+health/5+"/"+hunger/5+"/"+thirst/5+"/"+fatigue/5+"/"+morale/5+", trips="+journeys+", returned food/water/materials/medicine="+foodReturned+"/"+waterReturned+"/"+materialReturned+"/"+medReturned+", production energy/water="+v.game.production.energyProduced+"/"+v.game.production.waterProduced+", ration purchases="+v.game.production.foodRations+", medicine doses="+v.game.production.medicineDoses+", levels="+Arrays.toString(v.game.roomLevels)+", construction savings="+v.game.production.materialsSaved);
  if(trips)require(credited.size()==journeys&&foodReturned>0&&materialReturned>0&&v.game.food>0&&health/5>50,"regular real expeditions sustain starter shelter");
 }
 public static void main(String[] args){generatorsAndWater();cooking();workshop();clinic();exclusivity();clocksAndLegacy();cycleBoundaries();ui();simulation("normal-no-expeditions",false,false,false,false);simulation("upgraded-no-expeditions",true,false,false,false);simulation("no-food",false,true,false,false);simulation("no-water",false,false,true,false);simulation("regular-expeditions",false,false,false,true);simulation("upgraded-expeditions",true,false,false,true);System.out.println("PASS: "+checks+" production assertions; staff, zero inputs, stop/resume, bounded bonuses, penalties/exclusions, atomic costs/doses, old saves, step/speed/frame invariance, room UI and six seeded10-day simulations.");}
}
'''

def main():
    sources = {"com/lastdom/game/" + p.name: p.read_text()
               for p in (fixtures.ROOT / fixtures.JAVA_PATH).glob("*.java")}
    sources["com/lastdom/game/R.java"] = "package com.lastdom.game; public class R {public static class drawable {public static final int shelter_clean=1,shelter_full_scene=2;}}"
    fixtures.PROBE = PROBE
    with tempfile.TemporaryDirectory(prefix="production-regression-") as directory:
        print(fixtures.run_version(Path(directory), "production", sources).strip())

if __name__ == "__main__":
    main()
