#!/usr/bin/env python3
"""Actual ONLINE 0.5 economy/UI, original ONLINE 0.4 saves and bounded visual caches.
Canvas doubles and frame clocks do not establish physical Android graphics or FPS.
"""
import sys
sys.dont_write_bytecode = True
from pathlib import Path
import json, subprocess, tempfile
import online_alliance_regression as alliance
import refactor_regression as fixtures

BASE = '9436447715870edc762c5e2ab26141679834f38d'
LEGACY = r'''
package com.lastdom.game;
import android.content.*;import java.util.*;
public class RegressionProbe {
 public static void main(String[] args){
  MockOnlineWorldRepository r=new MockOnlineWorldRepository(new OnlineDemoSaveStore(new MemoryPreferences()));
  r.execute("ember_water",OnlineWorldGameplay.Kind.TRADE);r.createAlliance("Северный союз");System.out.println("LEGACY:"+OnlineDemoSaveStore.encode(r.gameplay()));
  r.invite("demo_ember");r.invite("demo_beacon");System.out.println("LEGACY:"+OnlineDemoSaveStore.encode(r.gameplay()));
  for(int i=0;i<10;i++)r.advanceMinute();r.createDemoEvent();
  r.startOperation("online_job_1","city_event_1",Arrays.asList("radio_1","radio_2"),Arrays.asList("demo_ember"),7,true);
  for(int i=0;i<55;i++)r.advanceMinute();System.out.println("LEGACY:"+OnlineDemoSaveStore.encode(r.gameplay()));
  for(int i=55;i<150;i++)r.advanceMinute();System.out.println("LEGACY:"+OnlineDemoSaveStore.encode(r.gameplay()));
  r.startOperation("online_job_2","city_event_2",Arrays.asList("radio_3","radio_4"),Arrays.asList("demo_beacon"),42,true);
  r.startPvp("online_job_3","pvp_frontier",Arrays.asList("radio_5"),OnlineCombatRules.Tactic.BALANCED,42,true);
  for(int i=0;i<20;i++)r.advanceMinute();System.out.println("LEGACY:"+OnlineDemoSaveStore.encode(r.gameplay()));
 }
}
'''

PROBE = alliance.PROBE[:alliance.PROBE.index(' public static void main(')] + r'''
 static OnlineWorldGameplay.Data conditions(int hp,int stamina,int medicine,int water){
  OnlineWorldGameplay.Data d=OnlineWorldGameplay.Data.initial();List<OnlineCombatSquad.Fighter> fs=new ArrayList<>(d.combat.fighters);fs.set(0,fs.get(0).condition(hp,stamina));
  Map<OnlineInventory.Resource,Integer> amounts=new EnumMap<>(OnlineInventory.Resource.class);amounts.put(OnlineInventory.Resource.MEDICINE,medicine);amounts.put(OnlineInventory.Resource.WATER,water);
  return new OnlineWorldGameplay.Data(new OnlineInventory(amounts),d.offers,d.operations,d.reputation,d.pvpZoneId,new OnlineBattleRepository.State(fs,d.combat.battles,d.combat.expeditions,1,true));
 }
 static void recoveryQuotes(){
  for(int hp:new int[]{0,35,65,99,100})for(int stamina:new int[]{0,40,60,99,100}){
   OnlineWorldGameplay.Data d=conditions(hp,stamina,hp<100?1:0,stamina<100?1:0);String before=OnlineDemoSaveStore.encode(d);OnlineCombatController.Change c=new OnlineCombatController().recover(d,"radio_1");
   if(hp==100&&stamina==100){require(!c.result.success&&c.data==d,"full recovery spends nothing");continue;}
   require(c.result.success&&OnlineActionRules.recovery(d,"radio_1").isEmpty(),"UI quote and command agree");OnlineCombatSquad.Fighter f=c.data.combat.fighter("radio_1");require(f.health==Math.min(100,hp+35)&&f.stamina==Math.min(100,stamina+60),"new bounded recovery");require(c.data.inventory.amount(OnlineInventory.Resource.MEDICINE)==0&&c.data.inventory.amount(OnlineInventory.Resource.WATER)==0,"charge only the required supply");require(before.equals(OnlineDemoSaveStore.encode(d)),"quote/command never mutate old snapshot");
   require(OnlineDemoSaveStore.encode(c.data).equals(OnlineDemoSaveStore.encode(OnlineDemoSaveStore.decode(OnlineDemoSaveStore.encode(c.data),new MockOnlineWorldRepository()))),"targeted recovery survives restart");
  }
  for(int med:new int[]{0,1})for(int water:new int[]{0,1})if(med+water<2){OnlineWorldGameplay.Data d=conditions(65,40,med,water);String old=OnlineDemoSaveStore.encode(d);require(!new OnlineCombatController().recover(d,"radio_1").result.success&&old.equals(OnlineDemoSaveStore.encode(d)),"insufficient input is atomic");}
  OnlineWorldGameplay.Data tired=conditions(100,40,0,1);require(new OnlineCombatController().recover(tired,"radio_1").result.success,"healthy tired fighter needs no medicine");OnlineWorldGameplay.Data injured=conditions(65,100,1,0);require(new OnlineCombatController().recover(injured,"radio_1").result.success,"fully rested injured fighter needs no water");
 }
 static void legacySaves(){for(String save:LEGACY_SAVES){MemoryPreferences disk=new MemoryPreferences();disk.values.put(OnlineDemoSaveStore.KEY,save);MockOnlineWorldRepository r=persistent(disk);require(r.writable()&&save.equals(ledger(r)),"actual ONLINE 0.4 save loads byte-for-byte");OnlineWorldGameplay.Data d=r.gameplay();for(OnlineCoopExpedition e:d.combat.expeditions){String id=e.id;boolean applied=e.rewardApplied;r.advanceMinute();require(!applied||r.gameplay().combat.expedition(id).rewardApplied,"old completed result cannot be unapplied");}require(r.gameplay().civic.alliance.name.equals("Северный союз"),"old name/members retained");}}
 static void mapCachesAndFades(){
  for(int width:new int[]{420,840})for(int height:new int[]{360,640,840,1200}){
   GameView v=interactive(width,height);radio(v);v.game.paused=true;String solo=snapshot(v);OnlineWorldGeometry g=geometry(v);OnlineMapArt art=new OnlineMapArt();art.prepare(g,v.onlineWorld.state.snapshot,v.scale);android.graphics.Path path=art.icon(OnlineCityEvent.Type.FIRE);int paths=android.graphics.Path.created;
   for(int i=0;i<60;i++){art.prepare(g,v.onlineWorld.state.snapshot,v.scale);art.draw(new Canvas());require(path==art.icon(OnlineCityEvent.Type.FIRE),"static projected icon identity retained");}
   require(paths==android.graphics.Path.created,"no static path rebuilding per frame");art.prepare(new OnlineWorldGeometry(height+20),v.onlineWorld.state.snapshot,v.scale);require(path!=art.icon(OnlineCityEvent.Type.FIRE),"resize rebuilds projection");
   tap(v,205,84);for(int i=0;i<60;i++)frame(v,16_666_667);draw(v);int commands=OnlineUiStyle.allocated;for(int i=0;i<120;i++)frame(v,16_666_667);require(commands==OnlineUiStyle.allocated,"card command pool grows once then reuses slots");float opacity=v.onlineWorld.state.cardOpacity;tap(v,80,84);require(!v.onlineWorld.state.selected()&&v.onlineWorld.state.cardOpacity==opacity,"close releases input immediately but retains presentation fade");frame(v,16_666_667);require(v.onlineWorld.state.cardOpacity>0&&v.onlineWorld.state.cardOpacity<opacity,"closing opacity decays smoothly");text(draw(v),"СОЮЗЫ");for(int i=0;i<80;i++)frame(v,16_666_667);require(v.onlineWorld.state.cardOpacity<.01,"closed card and highlight settle");require(solo.equals(snapshot(v)),"art/scroll/fade preserve solo save");
   double seconds=v.onlineWorld.state.animationSeconds;v.onlineWorld.leave();for(int i=0;i<60;i++){clock.now+=16_666_667;v.onlineWorld.frame();}require(seconds==v.onlineWorld.state.animationSeconds&&v.onlineWorld.civicPanel.routes.isEmpty(),"closed radio does no animation and retains no hidden route effects");
  }
  GameView v=interactive(420,640);radio(v);v.onlineWorld.createAlliance("ЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖ");tap(v,205,84);draw(v);OnlinePanelLayout layout=v.onlineWorld.civicPanel.layout;for(int i=0;i<v.onlineWorld.civicPanel.rows().size();i++){require(layout.height(i)>=64&&layout.at(layout.top(i)+layout.height(i)-1)==i,"measured draw and input rows agree");if(i>0)require(layout.top(i)==layout.top(i-1)+layout.height(i-1),"long rows cannot overlap");}
  OnlinePanelLayout huge=new OnlinePanelLayout();huge.reset(2);huge.fit(0,4,8);huge.finish();require(huge.height(0)>64&&huge.at(huge.top(1)+1)==1,"arbitrary long text keeps next action reachable");
 }
 static void fullPlayerCycle(){
  GameView v=interactive(420,840);radio(v);v.game.paused=true;String solo=snapshot(v);OnlineWorldGeometry g=geometry(v);tap(v,205,84);tap(v,180,g.height-114);civicRow(v,OnlineCivicPanelController.Action.INVITE,"demo_ember");for(int i=0;i<10;i++)v.onlineWorld.advanceMinute();require(v.onlineWorld.state.notice.contains("СОЮЗНИК"),"actual acceptance gets feedback");tap(v,335,84);tap(v,180,g.height-114);civicRow(v,OnlineCivicPanelController.Action.EVENT,"city_event_1");tap(v,180,g.height-114);require(!v.onlineWorld.civicPanel.primaryReason().isEmpty(),"empty squad shows disabled primary and reason");civicRow(v,OnlineCivicPanelController.Action.FIGHTER,"radio_1");civicRow(v,OnlineCivicPanelController.Action.ALLY,"demo_ember");require(v.onlineWorld.civicPanel.primaryReason().isEmpty(),"valid selection enables main action");
  MockOnlineWorldRepository tmp=new MockOnlineWorldRepository();OnlineCombatSquad own=new OnlineCombatSquad(Arrays.asList(v.onlineWorld.state.gameplay.combat.fighter("radio_1"))),ally=OnlineCityEventController.allies(v.onlineWorld.state.snapshot,Arrays.asList("demo_ember"));long seed=coopSeed("pve_infected",own,ally,true);clock.now=seed^0x9e3779b97f4a7c15L;
  tap(v,180,g.height-114);require(v.onlineWorld.civicPanel.confirming,"separate confirmation remains mandatory");tap(v,180,g.height-114);OnlineCoopExpedition trip=v.onlineWorld.state.gameplay.combat.expedition("online_job_1");require(trip.success&&v.onlineWorld.state.gameplay.combat.expeditions.size()==1,"real UI starts seeded successful operation once");tap(v,180,g.height-114);require(v.onlineWorld.state.gameplay.combat.expeditions.size()==1,"report action cannot duplicate sending");OnlineInventory inventory=v.onlineWorld.state.gameplay.inventory;for(int i=0;i<150;i++)v.onlineWorld.advanceMinute();require(v.onlineWorld.state.notice.contains("НАГРАДА ПОЛУЧЕНА"),"new success reports actual reward");for(OnlineInventory.Resource r:OnlineInventory.Resource.values())require(v.onlineWorld.state.gameplay.inventory.amount(r)==inventory.amount(r)+trip.loot.amount(r),"full cycle grants exactly once");
  tap(v,350,35);tap(v,110,g.panelBottom-90);require(v.onlineWorld.state.panel==OnlineWorldState.Panel.ROSTER,"large inventory action opens fighters");panelRow(v,OnlineCombatPanelController.Action.FIGHTER,"radio_1");require(v.onlineWorld.combatPanel.primaryReason().isEmpty(),"recovery shows real availability");int water=v.onlineWorld.state.gameplay.inventory.amount(OnlineInventory.Resource.WATER);tap(v,110,g.panelBottom-90);require(v.onlineWorld.state.gameplay.combat.fighter("radio_1").stamina==100&&v.onlineWorld.state.gameplay.inventory.amount(OnlineInventory.Resource.WATER)==water-1,"UI spends demo water and restores before next operation");require(!v.onlineWorld.combatPanel.primaryReason().isEmpty(),"full fighter disables repeat recovery");
  v.onlineWorld.closeCard();chooseShelter(v,"demo_ember");tap(v,110,g.panelBottom-90);tap(v,110,g.panelBottom-90);require(v.onlineWorld.state.notice.contains("СДЕЛКА ЗАВЕРШЕНА"),"trade feedback explicit");int trades=v.onlineWorld.state.gameplay.operations.size();tap(v,110,g.panelBottom-90);require(v.onlineWorld.state.gameplay.operations.size()==trades,"disabled one-shot trade cannot duplicate");v.onlineWorld.closeCard();tap(v,335,84);civicRow(v,OnlineCivicPanelController.Action.EVENT,"city_event_2");tap(v,180,g.height-114);civicRow(v,OnlineCivicPanelController.Action.FIGHTER,"radio_1");civicRow(v,OnlineCivicPanelController.Action.ALLY,"demo_ember");tap(v,180,g.height-114);tap(v,180,g.height-114);require(v.onlineWorld.state.gameplay.combat.expeditions.size()==2,"recovered fighters enter next operation");require(solo.equals(snapshot(v)),"entire online loop touches no solo resources/residents/save");
 }
 static void economyRuns(){
  for(String zone:new String[]{"pve_industry","pve_infected","mixed"})for(int size:new int[]{1,3}){
   MockOnlineWorldRepository r=new MockOnlineWorldRepository();int completed=0,wins=0,waterSpent=0,medSpent=0;
   for(int run=0;run<12;run++){
    List<String> ids=size==1?fighters(1):fighters(1,2,3);boolean ready=true;
    for(String id:ids){OnlineCombatSquad.Fighter f=r.gameplay().combat.fighter(id);if(f.stamina<50||f.health<50){int w=r.gameplay().inventory.amount(OnlineInventory.Resource.WATER),m=r.gameplay().inventory.amount(OnlineInventory.Resource.MEDICINE);if(!r.recover(id).success){ready=false;break;}waterSpent+=w-r.gameplay().inventory.amount(OnlineInventory.Resource.WATER);medSpent+=m-r.gameplay().inventory.amount(OnlineInventory.Resource.MEDICINE);}}
    if(!ready)break;String target=zone.equals("mixed")?(run%2==0?"pve_industry":"pve_infected"):zone;String job=r.gameplay().combat.requestId();require(r.startCoop(job,target,"demo_beacon",ids,run*97+42,true).success,"economy run starts available squad");OnlineCoopExpedition e=r.gameplay().combat.expedition(job);for(int i=0;i<e.durationMinutes;i++)r.advanceMinute();completed++;if(e.success)wins++;String same=ledger(r);r=persistentSaved(r);require(same.equals(ledger(r)),"simulation restart never changes reward");for(OnlineInventory.Resource resource:OnlineInventory.Resource.values())require(r.gameplay().inventory.amount(resource)>=0&&r.gameplay().inventory.amount(resource)<=OnlineInventory.LIMIT,"bounded real balances");
   }
   require(completed>0,"finite demo loop remains playable");System.out.println("ECONOMY:"+zone+" squad="+size+" completed="+completed+" wins="+wins+" waterSpent="+waterSpent+" medicineSpent="+medSpent+" balances="+ledger(r).split("\\|")[1]);
  }
 }
 static MockOnlineWorldRepository persistentSaved(MockOnlineWorldRepository r){MemoryPreferences disk=new MemoryPreferences();disk.values.put(OnlineDemoSaveStore.KEY,ledger(r));return persistent(disk);}
 public static void main(String[] args){recoveryQuotes();legacySaves();mapCachesAndFades();fullPlayerCycle();economyRuns();gameClockAndRoutes();System.out.println("PASS: "+checks+" ONLINE 0.5 assertions; selective bounded recovery, actual ONLINE 0.4 saves, cached static paths/reused card commands, measured layouts/fade/input isolation, full operation/reward/recovery/trade/next-operation cycle, six seeded economy scenarios, independent animation clocks and solo preservation.");}
}
'''

def sources(ref=None):
    if ref:
        names=subprocess.check_output(['git','ls-tree','-r','--name-only',ref,'--',str(fixtures.JAVA_PATH)],cwd=fixtures.ROOT,text=True).splitlines()
        result={'com/lastdom/game/'+Path(p).name:subprocess.check_output(['git','show',ref+':'+p],cwd=fixtures.ROOT,text=True) for p in names if p.endswith('.java')}
    else:
        result={'com/lastdom/game/'+p.name:p.read_text() for p in (fixtures.ROOT/fixtures.JAVA_PATH).glob('*.java')}
    result['com/lastdom/game/R.java']='package com.lastdom.game;public class R{public static class drawable{public static final int shelter_clean=1,shelter_full_scene=2;}}'
    return result

def main():
    with tempfile.TemporaryDirectory(prefix='online-polish-') as directory:
        fixtures.PROBE=LEGACY
        before=fixtures.run_version(Path(directory),'original_online04',sources(BASE))
        saves=[s.removeprefix('LEGACY:') for s in before.splitlines() if s.startswith('LEGACY:')]
        assert len(saves)==5
        current=sources()
        engine='com/lastdom/game/OnlineCombatEngine.java'
        current[engine]=current[engine].replace('final class OnlineCombatEngine {','final class OnlineCombatEngine {static int calculations;').replace('    Objects.requireNonNull(tactic);','    calculations++;\n    Objects.requireNonNull(tactic);')
        file='com/lastdom/game/OnlineUiStyle.java'
        current[file]=current[file].replace('final class OnlineUiStyle {','final class OnlineUiStyle {static int allocated;').replace('private static final class Command {','private static final class Command {Command(){allocated++;}')
        # Counters and conservative font widths exist only in Android test doubles.
        fixtures.STUBS['android/graphics/Path.java']=fixtures.STUBS['android/graphics/Path.java'].replace('public class Path {','public class Path {public static int created;public Path(){created++;}')
        fixtures.STUBS['android/graphics/Paint.java']=fixtures.STUBS['android/graphics/Paint.java'].replace('s.length()*size*.5f','s.length()*size*.65f')
        fixtures.PROBE=PROBE.replace('public class RegressionProbe {','public class RegressionProbe {static final String[] LEGACY_SAVES=new String[]{'+','.join(json.dumps(s) for s in saves)+'};')
        print(fixtures.run_version(Path(directory),'online05',current).strip())

if __name__=='__main__':main()
