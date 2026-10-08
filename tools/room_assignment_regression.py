#!/usr/bin/env python3
"""Room candidate filtering, actual assignments and touch/save behavior against real Java classes."""
import sys
sys.dont_write_bytecode = True
from pathlib import Path
import tempfile
import production_regression as production
import refactor_regression as fixtures

PROBE = production.PROBE[:production.PROBE.index(' static void generatorsAndWater()')] + r'''
 static RoomUpgradeLayout layout(GameView v){return new RoomUpgradeLayout(v.H/v.scale);}
 static Canvas picker(GameView v){Canvas c=new Canvas();v.roomUpgradeRenderer.draw(c);return c;}
 static void openWorkers(GameView v,int room){v.roomUpgradePanel.open(room);RoomUpgradeLayout l=layout(v);tap(v,90,l.secondaryTop+20);require(v.roomUpgradePanel.assigning&&!v.game.jobMenu,"room opens resident picker without arbitrary selection");picker(v);}
 static void candidate(GameView v,Resident r,boolean yes){require(v.roomUpgradePanel.workerIds.contains(r.id)==yes,"candidate "+r.name+" present="+yes);}
 static void allRooms(){
  for(int room=0;room<6;room++)for(String job:new String[]{"Отдых","Еда","Вода","Материалы","Ремонт","Охрана","Лечение","Строительство","Лечится"}){
   GameView v=fresh();idle(v);Resident r=v.game.people.get(0);r.job=job.equals("Вода")?"Ремонт":"Вода";
   boolean supported=v.game.roomJobs[room].equals(job)||(room==1&&job.equals("Вода"));
   String before=snapshot(v),reason=v.game.roomAssignmentController.assign(room,job,r.id);
   require(reason.isEmpty()==supported,"only jobs belonging to room "+room+" / "+job);
   if(supported){require(r.job.equals(job)&&v.game.homeRoomFor(r)==room,"real assignment/location changed for "+room);String paid=snapshot(v);v=kill();require(paid.equals(snapshot(v))&&v.game.people.get(0).job.equals(job),"assignment survives exact process restart "+room);}
   else require(before.equals(snapshot(v)),"foreign job rejected without save/resource mutation");
  }
  GameView v=fresh();idle(v);Resident r=v.game.people.get(3);require(v.game.roomAssignmentController.assign(0,"Ремонт",r.id).isEmpty(),"professions are bonuses, not invented restrictions on rooms");
  String before=snapshot(v);require(!v.game.roomAssignmentController.assign(-1,"Еда",r.id).isEmpty()&&!v.game.roomAssignmentController.assign(6,"Еда",r.id).isEmpty()&&!v.game.roomAssignmentController.assign(1,"Еда","missing").isEmpty()&&before.equals(snapshot(v)),"invalid room/ID atomically rejected");
 }
 static void filtering(){
  for(int state=0;state<11;state++){
   GameView v=fresh();idle(v);Resident r=v.game.people.get(0);
   switch(state){
    case 0:require(v.game.expeditionController.start("shop",Arrays.asList(r.id)).isEmpty(),"away fixture");break;
    case 1:require(v.game.roomUpgradeController.start(0,r.id).isEmpty(),"builder fixture");break;
    case 2:discover(v,RaidState.Enemy.INFECTED,30,1);require(v.game.raidController.toggleDefender(r.id).isEmpty(),"defender fixture");break;
    case 3:r.fatigue=20;break;
    case 4:r.fatigue=0;r.survivalFractions.put("fatigue",1);break;
    case 5:r.autoRecovery=true;break;
    case 6:r.job="Лечится";break;
    case 7:r.alive=false;break;
    case 8:r.health=0;break;
    case 9:r.job="Вода";r.fatigue=SurvivalConfig.REST_START;break;
    case 10:r.job="Вода";r.health=SurvivalConfig.TREAT_START-1;break;
   }
   v.roomUpgradePanel.open(1);v.roomUpgradePanel.assigning=true;v.roomUpgradePanel.assignmentJob="Еда";String before=snapshot(v);Canvas c=picker(v);candidate(v,r,false);require(c.commands.stream().noneMatch(cmd->cmd.startsWith("drawText["+r.name+" •")),"unavailable resident not rendered "+state);require(!v.game.roomAssignmentController.assign(1,"Еда",r.id).isEmpty()&&before.equals(snapshot(v)),"same availability enforced on controller without mutation "+state);
  }
  GameView v=fresh();idle(v);Resident r=v.game.people.get(0);r.job="Еда";openWorkers(v,1);candidate(v,r,true);require(v.roomUpgradePanel.assignedIds.contains(r.id),"existing cook shown as assigned, not offered as new assignment");RoomUpgradeLayout l=layout(v);tap(v,300,l.top+95);picker(v);require(v.roomUpgradePanel.assignmentJob.equals("Вода"),"kitchen water mode");candidate(v,r,true);require(!v.roomUpgradePanel.assignedIds.contains(r.id),"cook available for transfer to water");tap(v,90,l.top+95);picker(v);candidate(v,r,true);
  r.job="Отдых";r.fatigue=0;picker(v);candidate(v,r,true);r.fatigue=1;picker(v);candidate(v,r,false);r.job="Лечится";picker(v);candidate(v,r,false);
 }
 static void ui(){
  for(int[] size:new int[][]{{360,640},{420,560},{420,640},{420,840},{540,960},{1080,2340}})for(int room:new int[]{0,1,2,3,5}){
   GameView v=fresh();idle(v);if(room==5)for(Resident r:v.game.people)r.job="Вода";View.width=size[0];View.height=size[1];draw(v);String before=snapshot(v);openWorkers(v,room);RoomUpgradeLayout l=layout(v);Canvas c=picker(v);text(c,"ВЫБОР ЖИТЕЛЯ");text(c,"НАЗАД К КОМНАТЕ");text(c,"ЗАКРЫТЬ");require(before.equals(snapshot(v)),"opening/painting picker doesn't mutate game");boolean kitchen=room==1;int capacity=l.workerCapacity(kitchen);require(l.workerRowTop(kitchen)+capacity*RoomUpgradeLayout.ASSIGNMENT_ROW-8<=l.pageY-16,"candidate rows don't overlap pagination");
   if(kitchen){text(c,"ГОТОВИТЬ ЕДУ");text(c,"ДОБЫВАТЬ ВОДУ");tap(v,300,l.top+95);picker(v);}
   String job=kitchen?"Вода":v.game.roomJobs[room];String id=v.roomUpgradePanel.workerIds.get(0);Resident chosen=v.game.expeditionController.resident(id);Resident untouched=v.game.people.get(1);String old=untouched.job;int food=v.game.food,water=v.game.water,mats=v.game.mats,power=v.game.power;tap(v,180,l.workerRowTop(kitchen)+28);
   require(!v.roomUpgradePanel.assigning&&!v.game.jobMenu&&v.game.overlay==2&&chosen.job.equals(job),"tap assigns chosen resident only and returns to room "+room);require(untouched.job.equals(old)&&v.game.food==food&&v.game.water==water&&v.game.mats==mats&&v.game.power==power,"manual assignment doesn't debit/credit resources or reassign others");String saved=snapshot(v);v=kill();require(saved.equals(snapshot(v)),"UI assignment survives process restart "+room);
  }
 }
 static void staleAndPaging(){
  GameView v=fresh();idle(v);openWorkers(v,1);RoomUpgradeLayout l=layout(v);String id=v.roomUpgradePanel.workerIds.get(0);Resident chosen=v.game.expeditionController.resident(id);chosen.job="Лечится";String before=snapshot(v);tap(v,180,l.workerRowTop(true)+28);require(v.roomUpgradePanel.assigning&&chosen.job.equals("Лечится")&&v.game.people.get(1).job.equals("Отдых")&&before.equals(snapshot(v)),"stale candidate revalidated by ID, never selects replacement row");
  for(int state=0;state<3;state++){
   v=fresh();idle(v);openWorkers(v,0);l=layout(v);chosen=v.game.people.get(0);if(state==0)v.game.roomUpgradeController.start(1,chosen.id);if(state==1)v.game.expeditionController.start("shop",Arrays.asList(chosen.id));if(state==2)v.game.people.remove(chosen);before=snapshot(v);tap(v,180,l.workerRowTop(false)+28);require(v.roomUpgradePanel.assigning&&before.equals(snapshot(v)),"candidate became busy/removed after painting, rejected atomically "+state);
  }
  v=fresh();idle(v);View.height=640;draw(v);for(int i=0;i<10;i++){Resident r=v.game.make("Дополнительный "+i,"Житель",2);r.fatigue=0;v.game.people.add(r);}v.game.people.get(0).job="Лечится";v.game.people.get(2).job="Лечится";openWorkers(v,0);l=layout(v);int capacity=l.workerCapacity(false);while(v.roomUpgradePanel.page<(v.roomUpgradePanel.workerIds.size()-1)/capacity)tap(v,355,l.pageY);picker(v);String last=v.roomUpgradePanel.workerIds.get(v.roomUpgradePanel.workerIds.size()-1);int row=(v.roomUpgradePanel.workerIds.size()-1)%capacity;tap(v,180,l.workerRowTop(false)+row*RoomUpgradeLayout.ASSIGNMENT_ROW+28);require(v.game.expeditionController.resident(last).job.equals("Ремонт"),"pagination uses filtered IDs, reaches last resident");
  v=fresh();idle(v);for(Resident r:v.game.people)r.fatigue=20;openWorkers(v,1);before=snapshot(v);text(picker(v),"Нет доступных жителей");l=layout(v);tap(v,180,l.workerRowTop(true)+28);require(before.equals(snapshot(v))&&v.roomUpgradePanel.assigning,"empty list has no selectable phantom row");tap(v,180,l.actionTop+20);require(!v.roomUpgradePanel.assigning&&v.game.overlay==2,"back returns to room");tap(v,90,l.secondaryTop+20);tap(v,180,l.secondaryTop+20);require(v.game.overlay==0&&!v.roomUpgradePanel.assigning&&before.equals(snapshot(v)),"close dismisses picker without tap-through or mutation");
  v=fresh();idle(v);v.roomUpgradePanel.open(4);l=layout(v);tap(v,90,l.secondaryTop+20);require(v.defensePanel.open&&!v.roomUpgradePanel.assigning,"existing barricade defense/repair access preserved");
 }
 static void kitchen(){GameView v=fresh();idle(v);v.game.people.get(3).job="Еда";int food=v.game.food;v.game.productionController.produce(1440);require(v.game.food==food,"kitchen doesn't invent food manufacture");require(v.game.productionController.foodRationMinutes()>1440,"cook really extends existing ration");require(new ProductionRenderer(v).card(1).stream().anyMatch(r->r.text.contains("Кухня её не создаёт")),"card explains zero production");}
 public static void main(String[] args){allRooms();filtering();ui();staleAndPaging();kitchen();System.out.println("PASS: "+checks+" room assignment assertions; room-specific jobs, available-only real resident IDs, kitchen modes, recovery/busy exclusions, stale taps, filtered paging, small/scaled screens, atomicity, persistence and unchanged kitchen economy.");}
}
'''

def main():
    sources={"com/lastdom/game/"+p.name:p.read_text() for p in (fixtures.ROOT/fixtures.JAVA_PATH).glob("*.java")}
    sources["com/lastdom/game/R.java"]="package com.lastdom.game;public class R{public static class drawable{public static final int shelter_clean=1,shelter_full_scene=2;}}"
    fixtures.PROBE=PROBE
    with tempfile.TemporaryDirectory(prefix="room-assignment-") as directory:
        print(fixtures.run_version(Path(directory),"room-assignment",sources).strip())

if __name__=="__main__": main()
