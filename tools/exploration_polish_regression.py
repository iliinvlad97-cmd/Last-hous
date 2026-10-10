#!/usr/bin/env python3
"""v1.1.1: active route lifetime, truthful frozen discoveries and durable journal navigation.
Actual Java controllers/renderers with Android doubles; not a physical-device test.
"""
import sys
sys.dont_write_bytecode = True
from pathlib import Path
import tempfile
import exploration_regression as exploration
import refactor_regression as fixtures

PROBE = exploration.PROBE[:exploration.PROBE.index(' static void defaults()')] + r'''
 static long paths(GameView v){Canvas c=new Canvas();v.expeditionRenderer.drawRoute(c,new CityMapLayout(v.H/v.scale));long count=c.commands.stream().filter(x->x.startsWith("drawPath[")).count();if(count==0)require(c.commands.isEmpty(),"no historical crew marker or clickable route indicator");return count;}
 static Expedition returned(GameView v,boolean success){launch(v,"residential",success,false,3);Expedition e=recon(v);minutes(v,150);require(e.state()==Expedition.State.COMPLETED,"actual recon returned");v.cityMap.closeSelection();return e;}
 static void cleanRoutes(){
  GameView v=fresh();require(paths(v)==0,"no route before launch");launch(v,"residential",true,false,3);Expedition e=recon(v);String id=e.id;
  require(paths(v)==1,"outbound route and marker");minutes(v,45);require(e.state()==Expedition.State.EXPLORING&&paths(v)==1,"route present while exploring");minutes(v,60);require(e.state()==Expedition.State.RETURNING&&paths(v)==1,"return animation retains route");minutes(v,44);require(e.active()&&paths(v)==1,"route retained until actual return");minutes(v,1);require(paths(v)==0&&!e.reportAcknowledged,"unacknowledged report is not an active route");
  require(v.cityMap.reconDisplayProgress==0,"completed visual progress reset");v.game.screen=GameView.HOME;draw(v);v.game.screen=GameView.CITY_MAP;draw(v);require(paths(v)==0,"tabs cannot restore stale route");String before=snapshot(v);v=animatedRestore();require(paths(v)==0&&before.equals(snapshot(v)),"completed restart read only without route");
  v.game.people.get(0).fatigue=v.game.people.get(0).hunger=v.game.people.get(0).thirst=0;launch(v,"industrial",true,false,0);Expedition next=recon(v);require(paths(v)==1&&!next.id.equals(id)&&v.cityMap.reconDisplayProgress==0,"new scout shows only its own initial route");minutes(v,17);paths(v);animationNanos+=16_000_000L;paths(v);require(v.cityMap.reconDisplayProgress>0,"new route progresses");v=kill();require(paths(v)==1&&recon(v).locationId.equals("industrial"),"restart keeps only new route");
  v=fresh();require(v.game.expeditionController.start("shop",ids(v,0)).isEmpty(),"ordinary launch");e=v.game.expeditionController.active(Expedition.Type.LOOT);id=e.id;require(paths(v)==1,"ordinary outbound visible");minutes(v,75);require(e.state()==Expedition.State.AWAITING_RETURN&&paths(v)==1,"loot awaiting return still active");require(v.game.expeditionController.returnHome(id).isEmpty(),"ordinary return");minutes(v,44);require(paths(v)==1,"ordinary almost home visible");minutes(v,1);require(paths(v)==0,"ordinary actual return clears path and marker");before=snapshot(v);v=kill();require(before.equals(snapshot(v))&&paths(v)==0,"ordinary completion reload no route or duplicate reward");
  e=v.game.expeditionController.find(id);int logs=v.game.log.size(),food=v.game.food,water=v.game.water,mats=v.game.mats,warehouse=v.game.expeditionWarehouse.total();for(int i=0;i<5;i++){v.game.expeditionController.complete(e);v.cityMap.openExpedition(e);draw(v);v.cityMap.closeSelection();}require(v.game.log.size()==logs&&v.game.food==food&&v.game.water==water&&v.game.mats==mats&&v.game.expeditionWarehouse.total()==warehouse,"repeated ordinary reports have no repeated effects");
  v=fresh();launch(v,"residential",true,false,3);require(v.game.expeditionController.start("garage",ids(v,0)).isEmpty(),"parallel route launch");require(paths(v)==2,"two live routes");minutes(v,150);require(paths(v)==1&&v.game.expeditionController.active(Expedition.Type.LOOT)!=null,"completed recon doesn't draw alongside remaining ordinary route");
 }
 static long animationNanos;
 static GameView animatedRestore(){TreeMap<String,Object> disk=new TreeMap<>(Context.preferences.values);Context.preferences=new MemoryPreferences();Context.preferences.values.putAll(disk);animationNanos=0;GameView v=new GameView(new Context(),new MockOnlineWorldRepository(),()->animationNanos);v.game.rnd=new Quiet();draw(v);return v;}
 static GameView animatedFresh(){View.width=420;View.height=840;Context.preferences=new MemoryPreferences();animationNanos=0;GameView v=new GameView(new Context(),new MockOnlineWorldRepository(),()->animationNanos);v.game.event=false;draw(v);return v;}
 static void motionDiagnostics(){
  for(int speed:new int[]{1,2,4}){
   GameView v=animatedFresh();launch(v,"residential",true,false,3);Expedition e=recon(v);v.game.speed=speed;v.game.paused=true;String stopped=snapshot(v);for(int i=0;i<5;i++){v.tick.run();paths(v);}require(stopped.equals(snapshot(v)),"pause freezes recon and cosmetic progress");
   v.game.paused=false;paths(v);v.tick.run();require(e.elapsedMinutes()==speed,"speed controls game minutes per tick");paths(v);animationNanos+=16_000_000L;paths(v);require(v.cityMap.reconDisplayProgress>0&&v.cityMap.reconDisplayProgress<e.routeProgress(),"cosmetic marker follows actual speed-adjusted target without advancing time");
   int elapsed=e.elapsedMinutes();for(int i=0;i<5;i++)paths(v);require(e.elapsedMinutes()==elapsed,"extra frames cannot advance expedition");
   v=fresh();v.game.speed=speed;draw(v);v.game.residentX[0]-=50;float x=v.game.residentX[0],y=v.game.residentY[0];v.residentRenderer.drawFullSceneResidents(new Canvas(),116,Math.max(610,v.H/v.scale-72));double distance=Math.hypot(v.game.residentX[0]-x,v.game.residentY[0]-y);require(Math.abs(distance-3.8)<.001,"resident step is per draw, not multiplied by game speed");
  }
 }
 static void truthfulDiscoveries(){
  for(int prior=0;prior<=2;prior++){
   GameView v=fresh();ExplorationConfig.District config=ExplorationConfig.district("residential");for(int i=0;i<prior;i++)v.game.expeditionController.location(config.points.get(i)).setState(MapLocation.State.AVAILABLE);
   Expedition e=returned(v,true);String id=e.id;require(e.recon.discoveryRecorded&&e.recon.openedPoints.equals(config.points),"legacy point set preserved");require(e.recon.newlyOpenedPoints.equals(config.points.subList(prior,2)),"only actual newly unlocked points snapshotted");require(e.recon.discoveredDistricts.equals(Arrays.asList("industrial")),"actual next district snapshot");
   List<String> lines=v.reconReportRenderer.lines(e);require(lines.contains("Промышленная зона"),"new district name in result");if(prior==2)require(lines.stream().anyMatch(s->s.contains("Новых точек нет")),"honest no-new-points explanation");
   v.cityMap.openExpedition(e);text(draw(v),"РАЙОН ИССЛЕДОВАН");text(draw(v),"ПЕРЕЙТИ К ОТКРЫТЫМ ТОЧКАМ");String before=snapshot(v);v=kill();e=v.game.expeditionController.find(id);require(before.equals(snapshot(v))&&v.reconReportRenderer.lines(e).equals(lines),"discovery report exact restart");for(String point:config.points)require(!v.game.expeditionController.location(point).isLocked(),"opened points remain accessible");
   before=snapshot(v);for(int i=0;i<4;i++){v.game.explorationController.applyReturn(e);v.game.expeditionController.complete(e);v.cityMap.openExpedition(e);draw(v);}require(before.equals(snapshot(v)),"reopening discoveries never replays unlocks/injuries/logs");
  }
  GameView v=fresh();v.game.explorationController.district("industrial").state=CityDistrict.State.DISCOVERED;Expedition e=returned(v,true);require(e.recon.discoveredDistricts.isEmpty(),"already discovered child not claimed as new");require(v.reconReportRenderer.lines(e).stream().anyMatch(s->s.contains("Новых районов для разведки не обнаружено")),"no false new district feedback");
  v=fresh();e=returned(v,false);require(e.recon.newlyOpenedPoints.isEmpty()&&e.recon.discoveredDistricts.isEmpty()&&!v.cityMap.hasOpenedPoints(e),"failure has no fake discoveries or map button");v.cityMap.openExpedition(e);Canvas c=draw(v);text(c,"РАЗВЕДКА — НЕУДАЧА");require(c.commands.stream().noneMatch(x->x.contains("ПЕРЕЙТИ К ОТКРЫТЫМ ТОЧКАМ")),"failure has no misleading action");require(v.reconReportRenderer.lines(e).stream().anyMatch(s->s.contains("Новые точки и районы не открыты")),"failure explained honestly");
  v=fresh();e=returned(v,true);String id=e.id;snapshot(v);Context.preferences.values.keySet().removeIf(k->k.endsWith("recon9_discoveryRecorded")||k.endsWith("recon9_newPoints")||k.endsWith("recon9_newDistricts"));v=kill();e=v.game.expeditionController.find(id);require(e!=null&&e.rewardCredited&&!e.recon.discoveryRecorded,"v1.1.0 report migrates without changing completion");require(v.reconReportRenderer.lines(e).stream().anyMatch(s->s.contains("история новых открытий не сохранялась")),"legacy history isn't invented");require(v.cityMap.hasOpenedPoints(e)&&paths(v)==0,"legacy report still has map navigation without stale route");
 }
 static void journalAndMap(){
  for(int height:new int[]{640,720,840,1200})for(int width:new int[]{420,840}){
   GameView v=fresh();View.width=width;View.height=height*width/420;draw(v);Expedition e=returned(v,true);String id=e.id;v.game.expeditionController.acknowledge(id);v=kill();v.game.screen=2;
   String before=snapshot(v);tap(v,290,104);require(v.journal.reports,"journal reports tab");Canvas c=draw(v);text(c,"Жилой квартал");text(c,"РАЙОН ИССЛЕДОВАН");tap(v,180,JournalLayout.TOP+30);require(v.cityMap.expeditionPanel&&v.cityMap.panelExpedition().id.equals(id),"acknowledged result opens from journal after restart");c=draw(v);CityMapLayout map=new CityMapLayout(v.H/v.scale);text(c,"ПЕРЕЙТИ К ОТКРЫТЫМ ТОЧКАМ");
   int count=v.cityMap.panelLineCount;for(int i=0;i<count;i++){v.cityMap.panelScroll=i;draw(v);}require(before.equals(snapshot(v)),"journal and report scrolling do not change game state");
   tap(v,180,map.panelBottom-90);require(v.game.screen==GameView.CITY_MAP&&!v.cityMap.expeditionPanel&&!v.cityMap.districtsLayer&&v.cityMap.districtFilterId.equals("residential"),"action goes to existing location map");require(v.cityMap.focusedLocationId.equals("apartments")&&v.cityMap.visibleLocations().size()==2,"real newly opened object highlighted");c=draw(v);text(c,"ВЫДЕЛЕНО: Пустующие квартиры");MapLocation point=v.game.expeditionController.location(v.cityMap.focusedLocationId);tap(v,map.x(point.mapX),map.y(point.mapY));require(v.cityMap.selected()==point&&!point.isLocked(),"highlight uses original location and transform");tap(v,180,map.panelBottom-40);require(v.cityMap.preparation()==point,"opened point launches ordinary preparation");v.cityMap.closeSelection();
   tap(v,80,55);require(v.cityMap.visibleLocations().size()==6&&v.cityMap.focusedLocationId.isEmpty(),"old map preserved and focus cleared");tap(v,180,55);CityDistrict district=v.game.explorationController.district("residential");tap(v,map.x(district.config.x),map.y(district.config.y));c=draw(v);text(c,"ПОКАЗАТЬ ОТКРЫТЫЕ ТОЧКИ");require(c.commands.stream().noneMatch(x->x.contains("ОТПРАВИТЬ РАЗВЕДКУ")),"explored district has no repeat scouting button");tap(v,180,map.panelBottom-40);require(v.cityMap.focusedLocationId.equals("apartments"),"district action also highlights available point");
   v.cityMap.closeSelection();v.game.screen=2;v.journal.reports=false;v.game.log.clear();String longLog=String.join(" ",Collections.nCopies(100,"подробность"));v.game.log.add(longLog);require(v.journal.eventEntries().size()>1,"long journal entry remains readable on multiple cards");String reconstructed=String.join(" ",v.journal.eventEntries()).replace('\n',' ');require(reconstructed.equals(longLog),"pagination loses no log text");
   for(int i=0;i<15;i++){Expedition copy=new Expedition(e.locationId,e.participantIds,e.departureMinute,e.durationMinutes,e.durationMinutes,Expedition.State.COMPLETED);copy.type=Expedition.Type.RECON;copy.recon=e.recon;copy.rewardCredited=true;copy.resultGenerated=true;v.game.expeditions.add(copy);}
   v.journal.reports=true;draw(v);JournalLayout journal=new JournalLayout(v.H/v.scale);require(v.journal.pages(journal)>1,"report history paginated on every screen");tap(v,350,journal.pagerY);require(v.journal.page==1,"history forward");draw(v);tap(v,80,journal.pagerY);require(v.journal.page==0,"history back");tap(v,180,v.H/v.scale-40);require(v.game.screen==GameView.HOME,"journal back remains accessible");
  }
 }
 public static void main(String[] args){cleanRoutes();motionDiagnostics();truthfulDiscoveries();journalAndMap();System.out.println("PASS: "+checks+" city exploration polish assertions; both trip lifetimes, restart/tab/new-route cleanup, parallel squads, factual discovery snapshots and legacy reports, read-only journal history, map navigation/highlighting and eight scaled screen layouts.");}
}
'''

def main():
    sources = {"com/lastdom/game/"+p.name: p.read_text() for p in (fixtures.ROOT/fixtures.JAVA_PATH).glob("*.java")}
    sources["com/lastdom/game/R.java"] = "package com.lastdom.game;public class R{public static class drawable{public static final int shelter_clean=1,shelter_full_scene=2;}}"
    fixtures.PROBE = PROBE
    with tempfile.TemporaryDirectory(prefix="exploration-polish-") as directory:
        print(fixtures.run_version(Path(directory), "polish111", sources).strip())

if __name__ == "__main__":
    main()
