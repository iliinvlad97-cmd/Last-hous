#!/usr/bin/env python3
"""ONLINE 0.3.1: visible PvE entry, complete touch flow and durable report access.
Actual Java/Canvas/input with Android doubles; no physical Android rendering/FPS claim.
"""
import sys
sys.dont_write_bytecode = True
from pathlib import Path
import tempfile
import online_combat_regression as combat
import refactor_regression as fixtures

PROBE = combat.PROBE[:combat.PROBE.index(' public static void main(')] + r'''
 static void coopAccess(){
  for(int width:new int[]{420,840})for(int height:new int[]{360,500,640,840,1200})for(String zoneId:Arrays.asList("pve_industry","pve_infected")){
   GameView v=interactive(width,height);radio(v);v.game.paused=true;String solo=snapshot(v);OnlineWorldGeometry g=geometry(v);
   OnlineZone zone=v.onlineWorld.state.zones.stream().filter(z->z.id.equals(zoneId)).findFirst().get();clickZone(v,zone);Canvas card=draw(v);text(card,"РАЙОН РАДИОСЕТИ");text(card,zone.name);text(card,"СОВМЕСТНАЯ ЭКСПЕДИЦИЯ");
   require(!zone.description.contains("недоступны")&&!zone.description.contains("без боёв"),"region no longer incorrectly says cooperative gameplay is unavailable");
   require(g.secondary(35,g.panelBottom-90)&&g.secondary(385,g.panelBottom-90)&&g.panelBottom-76<g.height-78,"wide expedition button above navigation");
   tap(v,180,g.panelBottom-90);require(v.onlineWorld.state.panel==OnlineWorldState.Panel.COOP_PREP,"visible footer opens existing cooperative preparation");draw(v);text(draw(v),"СОВМЕСТНАЯ PvE");
   List<OnlineCombatPanelController.Row> rows=v.onlineWorld.combatPanel.rows();require(rows.stream().anyMatch(r->r.title.equals("Опасность: "+zone.danger))&&rows.stream().anyMatch(r->r.detail.contains(OnlineCombatRules.coopMinutes(zoneId)+" игровых минут")),"preparation has real risk and duration");
   tap(v,180,g.panelBottom-90);require(v.onlineWorld.state.gameplay.combat.expeditions.isEmpty(),"no send without selected squad");panelRow(v,OnlineCombatPanelController.Action.FIGHTER,"radio_1");panelRow(v,OnlineCombatPanelController.Action.FIGHTER,"radio_2");int ally=v.onlineWorld.combatPanel.allyIndex;panelRow(v,OnlineCombatPanelController.Action.ALLY,"");require(v.onlineWorld.combatPanel.allyIndex!=ally,"player selects virtual shelter ally");String allyId=v.onlineWorld.state.shelters.get(v.onlineWorld.combatPanel.allyIndex).id;
   tap(v,180,g.panelBottom-90);require(v.onlineWorld.combatPanel.confirming&&v.onlineWorld.state.gameplay.combat.expeditions.isEmpty(),"send is an explicit final confirmation");text(draw(v),"ОТПРАВИТЬ");tap(v,180,g.panelBottom-90);
   require(v.onlineWorld.state.panel==OnlineWorldState.Panel.COOP_REPORT,"sending opens active report");String id=v.onlineWorld.combatPanel.reportId;OnlineCoopExpedition expedition=v.onlineWorld.state.gameplay.combat.expedition(id);require(expedition!=null&&expedition.allyId.equals(allyId)&&expedition.own.fighters.size()==2,"existing saved model holds chosen own squad and ally");
   for(int i=0;i<17;i++)v.onlineWorld.advanceMinute();expedition=v.onlineWorld.state.gameplay.combat.expedition(id);require(expedition.elapsedMinutes==17&&!expedition.rewardApplied,"active progress uses game-minute controller");v.onlineWorld.closeCard();text(draw(v),"ОТРЯД: "+17*100/expedition.durationMinutes+"%");clickZone(v,zone);text(draw(v),"ОТЧЁТ");text(draw(v),"В пути: "+17*100/expedition.durationMinutes+"%");
   tap(v,315,g.panelTop+30);require(v.onlineWorld.state.panel==OnlineWorldState.Panel.COOP_REPORT&&v.onlineWorld.combatPanel.reportId.equals(id),"zone report shortcut reopens actual active mission");
   v.onlineWorld.closeCard();clickZone(v,zone);tap(v,180,g.panelBottom-90);panelRow(v,OnlineCombatPanelController.Action.FIGHTER,"radio_1");require(v.onlineWorld.combatPanel.selected.isEmpty()&&v.onlineWorld.state.result.contains("занят"),"busy member cannot be selected for another expedition");
   MemoryPreferences disk=Context.namedPreferences.get(OnlineDemoSaveStore.FILE);String active=OnlineDemoSaveStore.encode(v.onlineWorld.state.gameplay);GameView restart=new GameView(new Context());restart.game.rnd=new Quiet();require(active.equals(OnlineDemoSaveStore.encode(restart.onlineWorld.state.gameplay)),"active phase restores without new result or grant");radio(restart);OnlineZone restoredZone=restart.onlineWorld.state.zones.stream().filter(z->z.id.equals(zoneId)).findFirst().get();clickZone(restart,restoredZone);text(draw(restart),"СОВМЕСТНАЯ ЭКСПЕДИЦИЯ");text(draw(restart),"ОТЧЁТ");tap(restart,315,g.panelTop+30);
   require(restart.onlineWorld.combatPanel.reportId.equals(id)&&restart.onlineWorld.state.gameplay.combat.expedition(id).elapsedMinutes==17,"restarted zone links to saved progress");
   OnlineInventory before=restart.onlineWorld.state.gameplay.inventory;for(int i=17;i<expedition.durationMinutes;i++)restart.onlineWorld.advanceMinute();expedition=restart.onlineWorld.state.gameplay.combat.expedition(id);require(expedition.rewardApplied&&!expedition.active(),"existing expedition completes exactly once");
   for(OnlineInventory.Resource resource:OnlineInventory.Resource.values())require(restart.onlineWorld.state.gameplay.inventory.amount(resource)==before.amount(resource)+expedition.loot.amount(resource),"completion applies exact saved demo reward");
   require(restart.onlineWorld.combatPanel.rows().stream().anyMatch(r->r.detail.contains("Вклад в общую силу:")),"completed report shows real ally contribution");String completed=OnlineDemoSaveStore.encode(restart.onlineWorld.state.gameplay);restart.onlineWorld.closeCard();text(draw(restart),"ОТЧЁТ ГОТОВ");clickZone(restart,restoredZone);tap(restart,315,g.panelTop+30);draw(restart);restart.onlineWorld.advanceMinute();require(completed.equals(OnlineDemoSaveStore.encode(restart.onlineWorld.state.gameplay)),"zone reopening/rendering cannot grant twice");
   GameView completedRestart=new GameView(new Context());radio(completedRestart);OnlineZone finalZone=completedRestart.onlineWorld.state.zones.stream().filter(z->z.id.equals(zoneId)).findFirst().get();clickZone(completedRestart,finalZone);tap(completedRestart,315,g.panelTop+30);require(completedRestart.onlineWorld.combatPanel.reportId.equals(id)&&completed.equals(OnlineDemoSaveStore.encode(completedRestart.onlineWorld.state.gameplay)),"saved completed report accessible directly from region after restart");require(solo.equals(snapshot(completedRestart)),"entire PvE UI/send/progress/report flow preserves every solo value");
  }
 }
 static void forbiddenEntriesAndReportPriority(){
  GameView v=interactive(420,840);radio(v);OnlineWorldGeometry g=geometry(v);
  for(OnlineZone zone:v.onlineWorld.state.zones)if(zone.type==OnlineZone.Type.SAFE){v.onlineWorld.closeCard();clickZone(v,zone);require(draw(v).commands.stream().noneMatch(c->c.contains("СОВМЕСТНАЯ ЭКСПЕДИЦИЯ")),"safe region has no misleading PvE action");tap(v,180,g.panelBottom-90);require(v.onlineWorld.state.panel==OnlineWorldState.Panel.OBJECT,"safe-zone footer cannot launch preparation");}
  v.onlineWorld.closeCard();chooseShelter(v,"demo_beacon");text(draw(v),"ТОРГОВЛЯ");text(draw(v),"ПОМОЩЬ");tap(v,115,g.panelBottom-90);require(v.onlineWorld.state.panel==OnlineWorldState.Panel.TRADE,"old shelter trade remains unchanged");closeCard(v);tap(v,300,g.panelBottom-90);require(v.onlineWorld.state.panel==OnlineWorldState.Panel.HELP,"old shelter assistance remains unchanged");
  MockOnlineWorldRepository repo=new MockOnlineWorldRepository();repo.startCoop("online_job_1","pve_industry","demo_ember",fighters(1),4,true);repo.startCoop("online_job_2","pve_infected","demo_outpost",fighters(2),4,true);for(int i=0;i<90;i++)repo.advanceMinute();repo.startCoop("online_job_3","pve_industry","demo_beacon",fighters(3),4,true);OnlineWorldState state=new OnlineWorldState(repo.load(),repo.gameplay());require(state.coop("pve_industry").id.equals("online_job_3")&&state.coop("pve_infected").id.equals("online_job_2")&&state.coop("safe_north")==null,"zone chooses own active mission over historical report and cannot inherit another zone");for(int i=0;i<150;i++)repo.advanceMinute();state=new OnlineWorldState(repo.load(),repo.gameplay());require(state.coop("pve_industry").id.equals("online_job_3")&&!state.coop("pve_industry").active(),"latest completed report is retained without stale progress");
 }
 public static void main(String[] args){coopAccess();forbiddenEntriesAndReportPriority();System.out.println("PASS: "+checks+" ONLINE 0.3.1 assertions; both PvE regions, visible fixed button on ten screen layouts, own squad/ally/risk/duration/send, busy guards, active progress/restart/direct report, exact saved rewards once, zone-specific history, unchanged solo values and shelter trade/help.");}
}
'''

def main():
    sources={"com/lastdom/game/"+p.name:p.read_text() for p in (fixtures.ROOT/fixtures.JAVA_PATH).glob("*.java")}
    sources["com/lastdom/game/R.java"]="package com.lastdom.game;public class R{public static class drawable{public static final int shelter_clean=1,shelter_full_scene=2;}}"
    engine="com/lastdom/game/OnlineCombatEngine.java"
    sources[engine]=sources[engine].replace('final class OnlineCombatEngine {','final class OnlineCombatEngine {static int calculations;').replace('    Objects.requireNonNull(tactic);','    calculations++;\n    Objects.requireNonNull(tactic);')
    fixtures.PROBE=PROBE
    with tempfile.TemporaryDirectory(prefix="online-coop-access-") as directory:
        print(fixtures.run_version(Path(directory),"online031",sources).strip())

if __name__=="__main__": main()
