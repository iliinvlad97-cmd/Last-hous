#!/usr/bin/env python3
"""Actual Java room-card rates vs mutation, transaction accounting and Android Canvas doubles."""
import sys
sys.dont_write_bytecode = True
from pathlib import Path
import tempfile
import production_regression as production
import refactor_regression as fixtures

PROBE = production.PROBE[:production.PROBE.index(' static void generatorsAndWater()')] + r'''
 static List<RoomEfficiencyRenderer.Row> card(GameView v,int room){return new ProductionRenderer(v).card(room);}
 static void row(List<RoomEfficiencyRenderer.Row> rows,String expected){require(rows.stream().anyMatch(r->r.text.contains(expected)),"card shows: "+expected);}
 static double fatigue(Resident r){return r.fatigue+r.survivalFractions.getOrDefault("fatigue",0)/(double)SurvivalController.NEED_DENOMINATOR;}
 static double health(Resident r){return r.health+r.survivalFractions.getOrDefault("health",0)/(double)SurvivalController.NEED_DENOMINATOR;}
 static double energy(GameView v){return v.game.power+v.game.productionRemainders.values.getOrDefault("survival6_energy",0)/(double)SurvivalController.OUTPUT_DENOMINATOR;}
 static void generator(){
  for(int level=1;level<=3;level++)for(boolean staffed:new boolean[]{false,true}){
   GameView v=fresh();idle(v);v.game.power=10;v.game.roomLevels[0]=level;if(staffed)v.game.people.get(0).job="Ремонт";
   double displayed=v.game.productionController.energyPerDay();double before=energy(v);v.game.productionController.produce(1440);close(energy(v)-before,displayed,"generator display equals fractional real accrual L"+level+" staffed "+staffed);
   List<RoomEfficiencyRenderer.Row> rows=card(v,0);row(rows,"Базовая выработка: 2");row(rows,"Бонус уровня: +"+(level-1)*25);row(rows,"Бонус работников: +"+(staffed?13:0));row(rows,String.format(Locale.ROOT,"Итоговая выработка сейчас: +%.2f",displayed));
   v.game.roomCondition[0]=50;close(v.game.productionController.energyPerDay(),displayed,"room condition50 doesn't invent linear production penalty");row(card(v,0),"коэффициент 1,00");v.game.roomCondition[0]=0;close(v.game.productionController.energyPerDay(),0,"broken generator0");row(card(v,0),"основной эффект остановлен");
  }
  GameView v=fresh();idle(v);v.game.power=60;row(card(v,0),"Остановлено: накоплен запас");row(card(v,0),"0.00 − 1.00 = -1.00");v.game.power=59;row(card(v,0),"Итоговая выработка сейчас: +2.00");
 }
 static void kitchenAndWorkshop(){
  for(int level=1;level<=3;level++){
   GameView v=fresh();idle(v);v.game.roomLevels[1]=v.game.roomLevels[3]=level;v.game.people.get(3).job="Еда";v.game.people.get(4).job="Материалы";
   List<RoomEfficiencyRenderer.Row> rows=card(v,1);row(rows,"Производство еды: 0");row(rows,"Экономия новых пайков: "+String.format(Locale.ROOT,"%.2f",v.game.productionController.kitchenSavingBasis()/100.0));row(rows,"1 еда → "+v.game.productionController.foodRationMinutes());row(rows,"оценка, не гарантированное списание");
   Resident r=v.game.people.get(0);r.foodMinutes=0;int stock=v.game.food;int duration=v.game.productionController.foodRationMinutes();v.game.productionController.reserveFood(r);require(v.game.food==stock-1&&r.foodMinutes==duration,"cooking card pays one real input for shared duration");String paid=snapshot(v);v=kill();require(paid.equals(snapshot(v)),"ration/accounting exact restart");
   rows=card(v,3);row(rows,"Производство материалов: 0");row(rows,"Экономия улучшений: "+String.format(Locale.ROOT,"%.2f",v.game.productionController.workshopSavingBasis()/100.0));ProductionController.UpgradeQuote q=v.game.productionController.upgradeQuote(20);v.game.mats=100;v.roomUpgradePanel.open(0);row(card(v,0),"Стоимость: "+q.cost);require(v.game.roomUpgradeController.start(0,v.game.people.get(0).id).isEmpty()&&v.game.mats==100-q.cost,"displayed quote matches actual cost");
  }
  GameView v=fresh();idle(v);v.game.food=0;row(card(v,1),"Нет еды для новых пайков");row(card(v,1),"Нет доступных поваров");v.game.mats=0;row(card(v,3),"Нет материалов");row(card(v,3),"Нет доступных работников");
  v=fresh();idle(v);Resident anna=v.game.people.get(3);anna.job="Еда";anna.health=50;anna.hunger=80;anna.thirst=75;anna.fatigue=80;anna.morale=10;
  row(card(v,1),"здоровье −50.00%");row(card(v,1),"голод −35.00%");row(card(v,1),"жажда −35.00%");row(card(v,1),"мораль −35.00%");row(card(v,1),"Навык ×1.00 • профессия ×1.20");
  int fraction=v.game.productionController.kitchenSavingBasis();require(fraction<1200,"real penalty affects real process");String s=snapshot(v);for(int i=0;i<10;i++)card(v,1);require(s.equals(snapshot(v)),"UI penalty not applied repeatedly");
 }
 static void clinicAndRest(){
  GameView v=fresh();idle(v);Resident doctor=v.game.people.get(1),patient=v.game.people.get(0);doctor.job="Лечение";doctor.health=53;doctor.hunger=80;doctor.thirst=45;doctor.fatigue=72;doctor.morale=35;patient.job="Вода";patient.health=40;v.game.roomLevels[2]=3;v.game.expeditionWarehouse.set(ExpeditionLoot.Resource.MEDICINE,2);
  int hundredths=v.game.productionController.clinicalRateHundredths(3);require(hundredths==48,"known 0.483678 potential rate rounds to actual0.48 health/day");close(v.game.productionController.medicalPerDay(),hundredths/100.0,"medical presenter uses applied rounded hundredths");double before=health(patient);v.game.advanceMinute();close(health(patient)-before,hundredths/(double)SurvivalController.NEED_DENOMINATOR,"clinic snapshot is exactly applied rate");row(card(v,2),"подходят для лечения: 2");row(card(v,2),"Эффективность медиков:");row(card(v,2),"общая доза");
  v=fresh();idle(v);doctor=v.game.people.get(1);patient=v.game.people.get(0);doctor.job="Лечение";doctor.skill=0;patient.job="Вода";patient.health=40;v.game.expeditionWarehouse.set(ExpeditionLoot.Resource.MEDICINE,2);minutes(v,60);require(v.game.production.medicineDoses==0&&v.game.expeditionWarehouse.get(ExpeditionLoot.Resource.MEDICINE)==2&&patient.health==40,"zero healing composition never spends medicine");row(card(v,2),"недостаточная эффективность состава");
  v=fresh();idle(v);v.game.people.get(1).job="Лечение";v.game.people.get(0).health=40;row(card(v,2),"Итог лечения сейчас: +0.00");row(card(v,2),"нет медикаментов");v.game.expeditionWarehouse.set(ExpeditionLoot.Resource.MEDICINE,1);row(card(v,2),"Итог лечения сейчас: +2.00");v.game.roomCondition[2]=0;row(card(v,2),"основной эффект остановлен");
  for(int level=1;level<=3;level++){
   v=fresh();idle(v);v.game.people.get(1).job="Лечение";patient=v.game.people.get(0);patient.job="Вода";patient.health=40;v.game.expeditionWarehouse.set(ExpeditionLoot.Resource.MEDICINE,2);v.game.roomLevels[2]=level;double displayed=v.game.productionController.medicalPerDay();before=health(patient);minutes(v,1440);close(health(patient)-before,displayed,"clinic entire day matches actual display and health fraction L"+level);row(card(v,2),"Бонус уровня: +"+((level-1)*25));
   v=fresh();idle(v);Resident r=v.game.people.get(0);r.fatigue=80;v.game.roomLevels[5]=level;v.game.roomCondition[5]=0;r.health=50;r.morale=10;int perHour=SurvivalConfig.bedroomRecoveryPerHour(level);
   row(card(v,5),"Итог восстановления: −"+perHour);row(card(v,5),"Бонус уровня к отдыху: +"+(level==1?"0.00":level==2?"40.00":"100.00"));row(card(v,5),"Отдыхающих: 5");row(card(v,5),"0% — полностью отдохнул");before=fatigue(r);minutes(v,20);close(before-fatigue(r),perHour/3.0,"rest UI matches fractional20 min with low morale/damaged bedroom L"+level);
  }
 }
 static void defenses(){
  GameView v=fresh();idle(v);v.game.raidController.durability=75;discover(v,RaidState.Enemy.MARAUDERS,30,15);Resident guard=v.game.people.get(2);v.game.raidController.toggleDefender(guard.id);
  List<RoomEfficiencyRenderer.Row> rows=card(v,4);row(rows,"Прочность баррикад: 75%");row(rows,"Сила баррикад сейчас: 18.00");row(rows,"Бонус назначенных защитников: +24.00");row(rows,"Текущая сила обороны: 42.00");row(rows,"После улучшения: сила 45.60");row(rows,"Ремонт: 5 материалов, 60 мин., +25%");
  require(v.game.roomUpgradeController.start(4,v.game.people.get(0).id).isEmpty(),"defense forecast upgrade starts");minutes(v,180);close(v.game.raidController.defensePower(2),v.game.raidController.defensePower(),"forecast uses real upgraded defense resolver");RaidState raid=v.game.raidController.latest();double frozen=raid.defenseAtStart;guard=v.game.people.get(2);guard.health=30;row(card(v,4),"Сила защиты в бою: "+String.format(Locale.ROOT,"%.2f",frozen));String saved=snapshot(v);v=kill();require(saved.equals(snapshot(v))&&v.game.raidController.latest().defenseAtStart==frozen,"historical force never recomputed after restart");
  v=fresh();idle(v);discover(v,RaidState.Enemy.INFECTED,30,31);v.game.raidController.toggleDefender(v.game.people.get(2).id);minutes(v,60);raid=v.game.raidController.active();frozen=raid.defenseAtStart;v.game.raidController.durability=10;rows=card(v,4);row(rows,"Сила защиты в бою: "+String.format(Locale.ROOT,"%.2f",frozen));row(rows,"Текущая сила обороны: "+String.format(Locale.ROOT,"%.2f",v.game.raidController.defensePower()));String state=snapshot(v);for(int i=0;i<20;i++)card(v,4);require(state.equals(snapshot(v)),"historical/current display doesn't resolve/apply combat effects");
 }
 static void accounting(){
  GameView v=fresh();idle(v);v.game.people.subList(1,5).clear();Resident r=v.game.people.get(0);r.job="Вода";r.foodMinutes=r.waterMinutes=0;v.game.food=v.game.water=20;v.game.power=10;v.game.resourceAccounting.reset();v.game.save();ResourceAccounting a=v.game.resourceAccounting;
  require(a.change(0)==0&&a.change(1)==0&&a.change(2)==0,"initial balances not manufactured income");int[] initial={v.game.power,v.game.food,v.game.water,v.game.mats,0};v.game.advanceMinute();require(a.used[1]==1&&a.used[2]==1&&a.change(1)==-1&&a.change(2)==-1,"actual indivisible ration accounting, no averaged pretend debit");String paid=snapshot(v);v=kill();require(paid.equals(snapshot(v)),"accounting/counters atomic restart");v.game.advanceMinute();require(v.game.resourceAccounting.used[1]==1&&v.game.resourceAccounting.used[2]==1,"restored paid ration not recharged");
  v.game.power-=8;v.game.food-=2;v.game.mats-=5;v.game.expeditionWarehouse.set(ExpeditionLoot.Resource.MEDICINE,3);v.game.save();a=v.game.resourceAccounting;require(a.other[0]==-8&&a.other[1]==-2&&a.other[3]==-5&&a.other[4]==3,"real external effects separated from production/consumption");long[] stocks={v.game.power,v.game.food,v.game.water,v.game.mats,v.game.expeditionWarehouse.get(ExpeditionLoot.Resource.MEDICINE)};for(int i=0;i<5;i++)require(a.change(i)==stocks[i]-initial[i],"resource equation balances actual inventory "+i);paid=snapshot(v);for(int i=0;i<10;i++)v.game.save();require(paid.equals(snapshot(v)),"repeated save doesn't replay accounting");
  v.game.gameMinute=1439;v.game.save();int prevDay=v.game.day;v.game.advanceMinute();a=v.game.resourceAccounting;require(a.previousDay==prevDay&&a.previousUsed[1]==1&&a.previousOther[3]==-5&&a.used[0]==1,"day boundary saves old partial period and one real energy debit");paid=snapshot(v);v=kill();require(paid.equals(snapshot(v)),"daily accounting history survives restart");
  Context.preferences.values.keySet().removeIf(k->k.startsWith("room_eff8_")||k.equals("production8_waterRations")||k.equals("production8_energyUsed"));int food=v.game.food,water=v.game.water,power=v.game.power;v=kill();require(v.game.food==food&&v.game.water==water&&v.game.power==power&&v.game.resourceAccounting.change(1)==0,"old save migration starts new observation without retroactive credits/debits");
  Context.preferences.edit().putString("room_eff8_stock","bad").apply();v=kill();require(v.game.food==food&&v.game.resourceAccounting.change(1)==0,"invalid accounting discarded without touching real game");
 }
 static void realTransactions(){
  GameView v=fresh();idle(v);v.game.raidController.durability=50;v.game.resourceAccounting.reset();v.game.save();int mats=v.game.mats;require(v.game.raidController.startRepair(v.game.people.get(0).id).isEmpty(),"real repair transaction accepted");require(v.game.resourceAccounting.other[3]==-5&&v.game.mats==mats-5,"repair receipt is real single debit in other changes");String paid=snapshot(v);require(!v.game.raidController.startRepair(v.game.people.get(1).id).isEmpty()&&paid.equals(snapshot(v)),"rejected repeated repair creates no accounting debit");
  v=fresh();idle(v);v.game.resourceAccounting.reset();v.game.save();require(v.game.expeditionController.start("shop",Arrays.asList(v.game.people.get(0).id)).isEmpty(),"real expedition for accounting starts");Expedition expedition=v.game.expeditionController.active();for(int i=0;i<1000&&!expedition.rewardCredited;i++){if(expedition.state()==Expedition.State.AWAITING_DECISION){String id=expedition.explorationEvent.instanceId;v.game.expeditionController.chooseEvent(id,0);v.game.expeditionController.continueEvent(id);}if(expedition.state()==Expedition.State.AWAITING_RETURN)v.game.expeditionController.returnHome(expedition.id);v.game.advanceMinute();}
  require(expedition.rewardCredited,"real return completed");ResourceAccounting a=v.game.resourceAccounting;require(a.other[1]==expedition.cargo.get(ExpeditionLoot.Resource.FOOD)&&a.other[2]==expedition.cargo.get(ExpeditionLoot.Resource.WATER)&&a.other[3]==expedition.cargo.get(ExpeditionLoot.Resource.MATERIALS),"real delivered cargo classified as other income without pretending production");paid=snapshot(v);v=kill();require(paid.equals(snapshot(v)),"delivery and accounting restored together once");long food=v.game.resourceAccounting.other[1],water=v.game.resourceAccounting.other[2];for(int i=0;i<5;i++)v.game.expeditionController.advanceMinute();v.game.save();require(v.game.resourceAccounting.other[1]==food&&v.game.resourceAccounting.other[2]==water,"completed expedition can't duplicate observed reward");
 }
 static void clocks(){
  GameView v=fresh();idle(v);v.game.people.get(0).job="Ремонт";v.game.people.get(2).job="Вода";v.game.people.get(3).job="Еда";v.game.food=v.game.water=40;v.game.power=10;for(Resident r:v.game.people)r.foodMinutes=r.waterMinutes=120;v.game.resourceAccounting.reset();v.game.save();TreeMap<String,Object> disk=new TreeMap<>(Context.preferences.values);String result=null;
  for(int[] pattern:new int[][]{{1,1,1,1},{2,2,2,2},{4,4,4,4},{1,2,4,2}}){Context.preferences=new MemoryPreferences();Context.preferences.values.putAll(disk);v=load();for(int speed:pattern){v.game.speed=speed;for(int i=0;i<360/speed;i++)v.tick.run();v.game.save();v=kill();}v.game.speed=1;String current=snapshot(v);if(result==null)result=current;else require(result.equals(current),"rates/ledger/fractions exact across speeds/restarts "+Arrays.toString(pattern));}
  v.game.paused=true;String paused=snapshot(v);for(int i=0;i<10;i++){v.tick.run();v.roomUpgradePanel.open(i%6);draw(v);}require(paused.equals(snapshot(v)),"pause and all room displays can't create resource movements");
 }
 static void panels(){
  for(int[] size:new int[][]{{360,640},{420,640},{420,840},{540,960},{1080,2340}})for(int room=0;room<6;room++){
   GameView v=fresh();View.width=size[0];View.height=size[1];draw(v);v.roomUpgradePanel.open(room);ProductionRenderer p=new ProductionRenderer(v);List<RoomEfficiencyRenderer.Row> rows=p.card(room);require(rows==p.card(room),"unchanged room presentation cached");row(rows,"УЛУЧШЕНИЕ");row(rows,"РАБОТНИКИ");row(rows,"ФАКТИЧЕСКИЙ УЧЁТ РЕСУРСОВ");row(rows,"Производство − расход + прочие");String before=snapshot(v);Canvas c=draw(v);text(c,"ЗАКРЫТЬ");require(c.commands.stream().anyMatch(cmd->cmd.contains("После улучшения:")&&cmd.contains(":"+v.good+":")),"actual upgrade bonus rendered green");text(c,room==4?"ОБОРОНА / РЕМОНТ":"НАЗНАЧИТЬ");RoomUpgradeLayout l=new RoomUpgradeLayout(v.H/v.scale);require(l.top+93+(l.visibleLines-1)*RoomEfficiencyRenderer.LINE<l.actionTop-24,"larger body typography inside pinned footer");require(l.secondaryTop+43<l.bottom,"fixed actions above nav");
   for(int scroll=0;scroll<v.roomUpgradePanel.lineCount;scroll+=l.visibleLines){v.roomUpgradePanel.scroll=scroll;c=new Canvas();v.roomUpgradeRenderer.draw(c);for(String cmd:c.commands)if(cmd.startsWith("drawText[")){String[] parts=cmd.substring(9).split(", ");float y=Float.parseFloat(parts[parts.length-2]);require(y>=l.top*v.scale&&y<l.bottom*v.scale,"all room text inside overlay");}}
   require(before.equals(snapshot(v)),"full scroll read-only including defense and clinic "+room);v.roomUpgradePanel.scroll=10000;draw(v);tap(v,290,l.secondaryTop+20);require(v.game.overlay==0,"closing scroll end can't activate room behind");
   v.roomUpgradePanel.open(room);List<RoomEfficiencyRenderer.Row> original=p.card(room);v.game.roomCondition[room]=49;require(original!=p.card(room),"condition invalidates cached presentation");v.game.people.get(0).morale=10;original=p.card(room);v.game.people.get(0).morale=75;require(original!=p.card(room),"worker state invalidates cached presentation");
  }
 }
 public static void main(String[] args){generator();kitchenAndWorkshop();clinicAndRest();defenses();accounting();realTransactions();clocks();panels();System.out.println("PASS: "+checks+" unified room efficiency assertions; all six rooms, actual rates/fractions/expenses, inputs, zero-treatment guard, upgrades, defender snapshots, accounting/day rollover, old saves, pause/speeds/restarts, cache, readable colored scroll and pinned actions.");}
}
'''

def main():
    sources={"com/lastdom/game/"+p.name:p.read_text() for p in (fixtures.ROOT/fixtures.JAVA_PATH).glob("*.java")}
    sources["com/lastdom/game/R.java"]="package com.lastdom.game;public class R{public static class drawable{public static final int shelter_clean=1,shelter_full_scene=2;}}"
    fixtures.PROBE=PROBE
    with tempfile.TemporaryDirectory(prefix="room-efficiency-") as directory:
        print(fixtures.run_version(Path(directory),"room-efficiency",sources).strip())

if __name__=="__main__": main()
