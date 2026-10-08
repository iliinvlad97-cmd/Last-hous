#!/usr/bin/env python3
"""Stage 8.1 kitchen projection vs real prepaid debits, persistence and categorized assignment UI."""
import sys
sys.dont_write_bytecode = True
from pathlib import Path
import tempfile
import room_assignment_regression as assignments
import refactor_regression as fixtures

PROBE = assignments.PROBE[:assignments.PROBE.index(' static void allRooms()')] + r'''
 static void row(GameView v,String s){require(new ProductionRenderer(v).card(1).stream().anyMatch(r->r.text.contains(s)),"kitchen row: "+s);}
 static GameView staffed(int level,int cooks){GameView v=fresh();idle(v);v.game.roomLevels[1]=level;for(int i=0;i<cooks;i++)v.game.people.get(i==0?3:i-1).job="Еда";v.game.resourceAccounting.reset();v.game.save();return v;}
 static void foodMinute(GameView v){for(Resident r:v.game.people)if(r.alive&&!v.game.isOnExpedition(r)){v.game.productionController.reserveFood(r);if(r.foodMinutes>0)r.foodMinutes--;}}
 static void rates(){
  for(int level=1;level<=3;level++)for(int cooks=0;cooks<=3;cooks++){
   GameView v=staffed(level,cooks);ProductionController p=v.game.productionController;ProductionController.FoodBalance b=p.foodBalance();
   int quality=cooks==0?0:cooks==1?120:cooks==2?245:300;int nominal=Math.min(2500,quality*10*(100+(level-1)*20)/100);int duration=14400000/(10000-nominal);
   require(p.staffPercent(1,"Еда")==quality&&b.nominalSavingBasis==nominal&&b.rationMinutes==duration,"known worker/level/cap composition L"+level+" cooks"+cooks);
   require(b.workerSavingBasis==Math.min(2500,quality*10)&&b.workerSavingBasis+b.levelSavingBasis==nominal,"worker base plus level increment, not repeated bonuses");
   close(b.consumptionPerDay,5*1440.0/duration,"average uses actual integer ration duration");close(b.savedPerDay,5-b.consumptionPerDay,"actual sustainable savings");close(b.effectiveSavingPercent,100*(1-1440.0/duration),"floor-minute effect shown honestly");require(b.consumptionPerDay>=3.75&&b.consumptionPerDay<=5,"positive bounded consumption, no free food");
   String saved=snapshot(v);v=kill();p=v.game.productionController;ProductionController.FoodBalance restored=p.foodBalance();close(restored.consumptionPerDay,b.consumptionPerDay,"reload doesn't multiply bonus again");require(saved.equals(snapshot(v)),"all prepaid/survival fractions survive exact restart");
   int stock=v.game.food;for(int i=0;i<1440;i++)foodMinute(v);require(v.game.food==stock,"existing 1440-minute paid rations aren't recharged");for(int i=0;i<1440;i++)foodMinute(v);require(v.game.food==stock-5,"one actual ration debit per resident, no global extra charge");require(v.game.people.get(0).foodMinutes==duration-1440,"fractional extended prepaid minutes retained");
   v.game.save();saved=snapshot(v);v=kill();require(saved.equals(snapshot(v)),"fractional paid cycle exact restart");foodMinute(v);require(v.game.food==stock-5-(duration==1440?5:0),"only exhausted cycles charge after restart");
  }
 }
 static void forecasts(){
  for(int level=1;level<=3;level++)for(int stock:new int[]{0,1,4,5,8,13}){
   GameView v=staffed(level,1);v.game.food=stock;int[] paid={0,1,1439,1440,1920};for(int i=0;i<5;i++)v.game.people.get(i).foodMinutes=paid[i];v.game.save();ProductionController.FoodBalance b=v.game.productionController.foodBalance();String disk=snapshot(v);
   long empty=stock==0?0:-1,shortage=-1;int firstDay=-1;for(int minute=1;minute<=20000;minute++){
    boolean missing=false;for(Resident r:v.game.people)if(r.foodMinutes==0&&v.game.food==0)missing=true;
    // Actual controller pays in resident order; detect shortage after each requested debit.
    for(Resident r:v.game.people){if(r.foodMinutes==0&&v.game.food==0&&shortage<0)shortage=minute;v.game.productionController.reserveFood(r);if(r.foodMinutes>0)r.foodMinutes--;}
    if(v.game.food==0&&empty<0)empty=minute;
    if(minute==1440)firstDay=stock-v.game.food;
    if(minute>=1440&&empty>=0&&shortage>=0)break;
   }
   require(empty==b.stockEmptyMinute&&shortage==b.shortageMinute,"food forecast equals actual last debit and first shortage L"+level+" stock"+stock);
   require(firstDay==b.nextDayAffordable&&b.nextDayRequired>=firstDay,"next 24h real affordability vs required debits");
   Context.preferences=new MemoryPreferences(); // Restore the pre-projection paid state, not simulated hunger/resource effects.
   v=fresh();v.game.people.clear();v.game.food=0;ProductionController.FoodBalance none=v.game.productionController.foodBalance();require(none.stockEmptyMinute==-1&&none.shortageMinute==-1&&none.nextDayRequired==0&&none.consumptionPerDay==0,"no home residents means no fictitious forecast/expense");
  }
  GameView v=staffed(3,1);require(v.game.expeditionController.start("shop",Arrays.asList(v.game.people.get(0).id)).isEmpty(),"away participant fixture");v.game.people.get(0).foodMinutes=0;ProductionController.FoodBalance b=v.game.productionController.foodBalance();require(b.residents==4&&b.nextDayRequired==0,"away ration excluded from forecast");row(v,"БАЛАНС ПРОДОВОЛЬСТВИЯ");
  v=staffed(1,0);v.game.food=Integer.MAX_VALUE;require(v.game.productionController.foodBalance().stockEmptyMinute>0,"large inventory forecast uses long math");
 }
 static void observations(){
  GameView v=staffed(2,1);for(Resident r:v.game.people)r.foodMinutes=0;v.game.resourceAccounting.reset();v.game.save();minutes(v,1);ProductionController.FoodBalance b=v.game.productionController.foodBalance();require(b.spentToday==5&&v.game.production.foodRations==5,"displayed actual debits from transaction observer");row(v,"Фактически сегодня: 5");row(v,"Расход в среднем:");row(v,"Экономия в среднем:");row(v,"Работники: 12.00%");row(v,"(+2.40 п.п.)");
  int prepaid=v.game.people.get(0).foodMinutes;String saved=snapshot(v);for(int i=0;i<20;i++)new ProductionRenderer(v).card(1);require(saved.equals(snapshot(v)),"card/forecast never pay or consume");v=kill();require(saved.equals(snapshot(v))&&v.game.people.get(0).foodMinutes==prepaid,"actual counters and fraction exact restart");
  v.game.roomLevels[1]=3;int food=v.game.food;require(v.game.people.get(0).foodMinutes==prepaid,"level change never extends paid rations retrospectively");require(v.game.roomAssignmentController.unassign(1,"Еда",v.game.people.get(3).id).isEmpty(),"cook removed onto rest");require(v.game.productionController.foodRationMinutes()==1440&&v.game.food==food&&v.game.people.get(0).foodMinutes==prepaid,"worker change affects next ration only");
  v.game.gameMinute=1439;v.game.save();minutes(v,1);b=v.game.productionController.foodBalance();require(b.spentPreviousDay==5&&b.spentToday==0,"actual partial previous/current days, no pretend average debit");saved=snapshot(v);v=kill();require(saved.equals(snapshot(v)),"observed daily history exact reload");
  v=staffed(3,1);v.game.roomCondition[1]=0;require(v.game.productionController.foodBalance().nominalSavingBasis==0&&v.game.productionController.foodRationMinutes()==1440,"broken kitchen stops savings, not nourishment from real stock");
  v=staffed(3,1);v.game.food=0;for(Resident r:v.game.people)r.foodMinutes=0;minutes(v,60);require(v.game.food==0&&v.game.production.foodRations==0&&v.game.people.get(0).hunger>0,"no food means no negative debit/free nourishment");row(v,"Запас исчерпан");row(v,"можно оплатить 0");
 }
 static void clocksAndOldSave(){
  GameView v=staffed(2,1);for(Resident r:v.game.people)r.foodMinutes=37;v.game.save();TreeMap<String,Object> disk=new TreeMap<>(Context.preferences.values);String reference=null;
  for(int[] pattern:new int[][]{{1,1,1,1},{2,2,2,2},{4,4,4,4},{1,2,4,2}}){Context.preferences=new MemoryPreferences();Context.preferences.values.putAll(disk);v=load();for(int speed:pattern){v.game.speed=speed;for(int i=0;i<360/speed;i++)v.tick.run();v.game.save();v=kill();}v.game.speed=1;String end=snapshot(v);if(reference==null)reference=end;else require(reference.equals(end),"food accounting/paid fractions exact at x1/x2/x4 and mixed speeds");}
  v.game.paused=true;String paused=snapshot(v);for(int i=0;i<100;i++){v.tick.run();v.game.productionController.foodBalance();new ProductionRenderer(v).card(1);}require(paused.equals(snapshot(v)),"pause/forecast drawing doesn't change consumption");
  v=staffed(2,1);minutes(v,17);int paid=v.game.people.get(0).foodMinutes,stock=v.game.food;Context.preferences.values.keySet().removeIf(k->k.startsWith("room_eff8_")||k.startsWith("production8_"));v=kill();require(v.game.people.get(0).foodMinutes==paid&&v.game.food==stock&&v.game.productionController.foodBalance().spentToday==0,"old save preserves prepaid balance, starts new observation without charging");minutes(v,1);require(v.game.food==stock,"legacy paid ration still not recharged");
 }
 static void categories(){
  for(int room:new int[]{0,1,2,3,5}){
   GameView v=fresh();idle(v);String job=v.game.roomJobs[room];Resident assigned=v.game.people.get(0),free=v.game.people.get(1),transfer=v.game.people.get(2);assigned.job=job;transfer.job=room==0?"Еда":"Ремонт";
   if(room==5)free.job="Вода";openWorkers(v,room);RoomUpgradeLayout l=layout(v);require(v.roomUpgradePanel.assignedIds.contains(assigned.id),"assigned group included "+room);require(v.roomUpgradePanel.workerIds.get(0).equals(assigned.id),"assigned category sorted first "+room);text(picker(v),"УЖЕ НАЗНАЧЕН");
   if(room!=5){text(picker(v),"снять на отдых");tap(v,180,l.workerRowTop(room==1)+25);require(assigned.job.equals("Отдых")&&!v.roomUpgradePanel.assigning,"assigned tap removes only this worker to rest "+room);String saved=snapshot(v);v=kill();require(saved.equals(snapshot(v)),"removal save/restart "+room);}else{String before=snapshot(v);tap(v,180,l.workerRowTop(false)+25);require(before.equals(snapshot(v))&&assigned.job.equals("Отдых"),"bedroom assigned row cannot interrupt rest");}
  }
  GameView v=fresh();idle(v);Resident r=v.game.people.get(0);r.job="Ремонт";require(v.game.roomAssignmentController.category(1,"Еда",r)==RoomAssignmentController.Category.TRANSFER,"worker categorized for transfer");int stock=v.game.food;require(v.game.roomAssignmentController.assign(1,"Еда",r.id).isEmpty()&&v.game.homeRoomFor(r)==1&&!v.game.productionController.worker(r,"Ремонт")&&v.game.productionController.worker(r,"Еда"),"transfer switches single existing job, old production excluded");require(v.game.food==stock,"transfer doesn't manufacture food");String saved=snapshot(v);require(!v.game.roomAssignmentController.assign(1,"Еда",r.id).isEmpty()&&saved.equals(snapshot(v)),"repeat assignment rejected without duplicate effects");
  require(v.game.roomAssignmentController.profession(v.game.people.get(3),1,"Еда",false,false).contains("+20%"),"actual collector contribution shown");require(v.game.roomAssignmentController.profession(v.game.people.get(3),1,"Вода",false,false).contains("бонуса нет"),"water doesn't invent food profession bonus");
 }
 static void specializedModes(){
  GameView v=fresh();idle(v);Resident patient=v.game.people.get(0);patient.job="Лечится";patient.health=40;v.game.raidController.durability=50;String before=snapshot(v);require(!v.game.roomUpgradeController.start(0,patient.id).isEmpty()&&!v.game.raidController.startRepair(patient.id).isEmpty()&&before.equals(snapshot(v)),"mandatory patient cannot build or repair, no spending");
  v.roomUpgradePanel.open(0);RoomUpgradeLayout l=layout(v);tap(v,180,l.actionTop+20);picker(v);require(!v.roomUpgradePanel.builderIds.contains(patient.id),"treated person hidden from build picker");String id=v.roomUpgradePanel.builderIds.get(0);Resident changed=v.game.expeditionController.resident(id);changed.job="Лечится";before=snapshot(v);tap(v,180,l.rowTop+25);require(v.roomUpgradePanel.choosing&&v.roomUpgradePanel.builderId.isEmpty()&&before.equals(snapshot(v)),"stale builder ID revalidated, no paid task");
  v=fresh();idle(v);v.game.raidController.durability=50;v.defensePanel.openStatus();v.defensePanel.mode=DefensePanelController.Mode.BUILDERS;v.defensePanel.reset();patient=v.game.people.get(0);patient.job="Лечится";draw(v);require(!v.defensePanel.residentIds.contains(patient.id),"repair picker excludes patient");text(draw(v),"СВОБОДЕН");
  v=fresh();idle(v);discover(v,RaidState.Enemy.MARAUDERS,30,1);v.defensePanel.openStatus();v.game.raidController.prepare();v.defensePanel.mode=DefensePanelController.Mode.DEFENDERS;v.defensePanel.reset();Resident guard=v.game.people.get(2);v.game.raidController.toggleDefender(guard.id);draw(v);require(v.defensePanel.residentIds.get(0).equals(guard.id),"defense assigned group sorted first");text(draw(v),"УЖЕ НАЗНАЧЕН");require(v.defenseRenderer.content.residents.get(0).detail.stream().anyMatch(s->s.contains("+50%")),"real guard profession effect shown");double power=v.game.raidController.defensePower();String disk=snapshot(v);for(int i=0;i<10;i++)draw(v);require(disk.equals(snapshot(v))&&power==v.game.raidController.defensePower(),"defense presentation doesn't modify strength or outcomes");
 }
 static void panels(){
  for(int[] size:new int[][]{{360,640},{420,560},{420,640},{420,840},{1080,2340}}){
   GameView v=staffed(2,1);View.width=size[0];View.height=size[1];draw(v);v.roomUpgradePanel.open(1);List<RoomEfficiencyRenderer.Row> rows=new ProductionRenderer(v).card(1);require(rows.get(0).text.equals("БАЛАНС ПРОДОВОЛЬСТВИЯ"),"food balance first before upgrade details");Canvas c=picker(v);text(c,"Запас:");text(c,"Расход в среднем:");text(c,"ЗАКРЫТЬ");RoomUpgradeLayout l=layout(v);String before=snapshot(v);for(int scroll=0;scroll<v.roomUpgradePanel.lineCount;scroll+=l.visibleLines){v.roomUpgradePanel.scroll=scroll;picker(v);}require(before.equals(snapshot(v)),"scrolling economy read only");openWorkers(v,1);text(picker(v),"УЖЕ НАЗНАЧЕН");v.roomUpgradePanel.page=1;Canvas second=picker(v);text(second,"СВОБОДЕН");for(String cmd:second.commands)if(cmd.startsWith("drawText[")){String[] parts=cmd.substring(9).split(", ");float y=Float.parseFloat(parts[parts.length-2]);require(y>=l.top*v.scale&&y<l.bottom*v.scale,"assignment text bounded above nav");}tap(v,180,l.secondaryTop+20);require(v.game.overlay==0,"close remains accessible");
  }
 }
 public static void main(String[] args){rates();forecasts();observations();clocksAndOldSave();categories();specializedModes();panels();System.out.println("PASS: "+checks+" kitchen economy and categorized assignment assertions; actual ration rates, worker/level/cap breakdown, saved minutes, exact debit/shortage forecasts, real daily accounting, pause/speeds/restarts/old saves, transfers/removal, professions, mandatory treatment guard, specialized repair/defense and small-screen panels.");}
}
'''

def main():
    sources={"com/lastdom/game/"+p.name:p.read_text() for p in (fixtures.ROOT/fixtures.JAVA_PATH).glob("*.java")}
    sources["com/lastdom/game/R.java"]="package com.lastdom.game;public class R{public static class drawable{public static final int shelter_clean=1,shelter_full_scene=2;}}"
    fixtures.PROBE=PROBE
    with tempfile.TemporaryDirectory(prefix="kitchen81-") as directory:
        print(fixtures.run_version(Path(directory),"kitchen81",sources).strip())

if __name__=="__main__": main()
